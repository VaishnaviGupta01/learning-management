package com.lms.ml;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Client for the Python ml-service. Implementations throw {@link MlServiceException} when the
 * service is unreachable or returns an error, so callers can degrade gracefully.
 */
public interface MlClient {

    /** POST /api/knowledge/score */
    List<KnowledgeScore> scoreKnowledge(List<TopicEvidence> topics);

    /** POST /api/recommend - returns candidates ranked by priority, highest first. */
    List<RankedTopic> recommend(List<RecommendCandidate> candidates, Integer topK);

    /** Per-topic accuracy ratios in [0, 1]; null means no evidence of that kind. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TopicEvidence(Long topicId, Double diagnostic, Double recentQuiz, Double practice, Double revision) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record KnowledgeScore(Long topicId, double score, String classification) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RecommendCandidate(Long topicId, double knowledgeScore, double importance, double urgency) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RankedTopic(Long topicId, int rank, double priority, String reason) {
    }
}
