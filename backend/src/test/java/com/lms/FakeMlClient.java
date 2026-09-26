package com.lms;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.lms.ml.MlClient;
import com.lms.ml.MlServiceException;

/**
 * In-process stand-in for ml-service with the same formulas (see ml-service/app), so backend tests do not need
 * the Python service. {@link #down} simulates an outage.
 */
public class FakeMlClient implements MlClient {

    public volatile boolean down;

    @Override
    public List<KnowledgeScore> scoreKnowledge(List<TopicEvidence> topics) {
        failIfDown();
        return topics.stream().map(t -> {
            double sum = 0;
            double weights = 0;
            Double[] values = {t.diagnostic(), t.recentQuiz(), t.practice(), t.revision()};
            double[] w = {0.4, 0.3, 0.2, 0.1};
            for (int i = 0; i < 4; i++) {
                if (values[i] != null) {
                    sum += w[i] * values[i];
                    weights += w[i];
                }
            }
            if (weights == 0) {
                return new KnowledgeScore(t.topicId(), 0.0, "NOT_STARTED");
            }
            double score = Math.round(sum / weights * 10000) / 10000.0;
            String band = score >= 0.8 ? "STRONG" : score >= 0.6 ? "MODERATE" : score >= 0.4 ? "NEEDS_PRACTICE" : "WEAK";
            return new KnowledgeScore(t.topicId(), score, band);
        }).toList();
    }

    @Override
    public List<RankedTopic> recommend(List<RecommendCandidate> candidates, Integer topK) {
        failIfDown();
        List<RecommendCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(FakeMlClient::priority).reversed()) // stable
                .limit(topK == null ? Long.MAX_VALUE : topK)
                .toList();
        return IntStream.range(0, sorted.size())
                .mapToObj(i -> new RankedTopic(sorted.get(i).topicId(), i + 1, priority(sorted.get(i)), "fake reason"))
                .toList();
    }

    static double priority(RecommendCandidate c) {
        return Math.round((0.5 * (1 - c.knowledgeScore()) + 0.3 * c.importance() + 0.2 * c.urgency()) * 10000) / 10000.0;
    }

    private void failIfDown() {
        if (down) {
            throw new MlServiceException("fake ml-service is down", null);
        }
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        public FakeMlClient fakeMlClient() {
            return new FakeMlClient();
        }
    }
}
