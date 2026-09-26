package com.lms.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.analytics.RecommendationEvaluation;
import com.lms.dto.analytics.RecommendationEvaluation.PathStats;
import com.lms.entity.QuestionAttempt;
import com.lms.entity.QuizAttempt;
import com.lms.entity.Recommendation;
import com.lms.entity.enums.QuizType;
import com.lms.repository.QuestionAttemptRepository;
import com.lms.repository.RecommendationRepository;

/** See {@link RecommendationEvaluation} for the exact definitions. Uses real attempt data only - nothing simulated. */
@Service
@Transactional(readOnly = true)
public class RecommendationEvaluationService {

    static final double STRUGGLE_THRESHOLD = 0.6;
    /** Below this many follow-up pairs per path the comparison is flagged as not meaningful yet. */
    static final int MIN_PAIRS = 10;

    private final RecommendationRepository recommendationRepository;
    private final QuestionAttemptRepository questionAttemptRepository;

    public RecommendationEvaluationService(RecommendationRepository recommendationRepository,
                                           QuestionAttemptRepository questionAttemptRepository) {
        this.recommendationRepository = recommendationRepository;
        this.questionAttemptRepository = questionAttemptRepository;
    }

    public RecommendationEvaluation evaluate(Long courseId, int k) {
        List<QuestionAttempt> answers = questionAttemptRepository.findSubmittedByCourse(courseId);

        // ---------------------------------------------------------- precision / recall
        Map<Long, List<Recommendation>> recsByStudent = recommendationRepository
                .findByTopicModuleCourseIdOrderByStudentIdAscScoreDesc(courseId).stream()
                .collect(Collectors.groupingBy(r -> r.getStudent().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<QuestionAttempt>> answersByStudent = answers.stream()
                .collect(Collectors.groupingBy(qa -> qa.getQuizAttempt().getStudent().getId()));

        double precisionSum = 0;
        double recallSum = 0;
        int evaluated = 0;
        int withRelevant = 0;
        for (Map.Entry<Long, List<Recommendation>> e : recsByStudent.entrySet()) {
            Instant generatedAt = e.getValue().stream().map(Recommendation::getCreatedAt).min(Comparator.naturalOrder()).orElseThrow();
            Map<Long, int[]> later = new HashMap<>(); // topicId -> {answers, correct}
            for (QuestionAttempt qa : answersByStudent.getOrDefault(e.getKey(), List.of())) {
                if (qa.getQuizAttempt().getSubmittedAt().isAfter(generatedAt)) {
                    int[] c = later.computeIfAbsent(qa.getQuestion().getTopic().getId(), t -> new int[2]);
                    c[0]++;
                    c[1] += qa.isCorrect() ? 1 : 0;
                }
            }
            if (later.isEmpty()) {
                continue; // no evidence after the recommendations were made
            }
            Set<Long> relevant = later.entrySet().stream()
                    .filter(t -> t.getValue()[1] / (double) t.getValue()[0] < STRUGGLE_THRESHOLD)
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
            List<Long> topK = e.getValue().stream().limit(k).map(r -> r.getTopic().getId()).toList();
            long hits = topK.stream().filter(relevant::contains).count();

            evaluated++;
            precisionSum += hits / (double) k;
            if (!relevant.isEmpty()) {
                withRelevant++;
                recallSum += hits / (double) relevant.size();
            }
        }

        // ---------------------------------------------------------- fixed vs adaptive
        // per attempt: accuracy on each topic it covered
        // keyed by id: entity hashCode is per-class, which would put every attempt in one hash bucket
        Map<Long, QuizAttempt> attempts = new HashMap<>();
        Map<Long, Map<Long, int[]>> perAttempt = new LinkedHashMap<>(); // answers are oldest first
        for (QuestionAttempt qa : answers) {
            attempts.putIfAbsent(qa.getQuizAttempt().getId(), qa.getQuizAttempt());
            int[] c = perAttempt.computeIfAbsent(qa.getQuizAttempt().getId(), a -> new HashMap<>())
                    .computeIfAbsent(qa.getQuestion().getTopic().getId(), t -> new int[2]);
            c[0]++;
            c[1] += qa.isCorrect() ? 1 : 0;
        }
        Map<String, PathAccumulator> paths = new LinkedHashMap<>();
        paths.put("ADAPTIVE", new PathAccumulator());
        paths.put("FIXED", new PathAccumulator());
        // (student, topic) -> sequence of (path, accuracy) in submission order
        Map<List<Long>, List<Object[]>> sequences = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<Long, int[]>> e : perAttempt.entrySet()) {
            QuizAttempt attempt = attempts.get(e.getKey());
            String path = path(attempt.getQuiz().getType());
            Long studentId = attempt.getStudent().getId();
            for (Map.Entry<Long, int[]> t : e.getValue().entrySet()) {
                double accuracy = t.getValue()[1] / (double) t.getValue()[0];
                sequences.computeIfAbsent(List.of(studentId, t.getKey()), key -> new ArrayList<>())
                        .add(new Object[]{path, accuracy});
                if (path != null) {
                    paths.get(path).attempt(studentId, accuracy);
                }
            }
        }
        for (List<Object[]> seq : sequences.values()) {
            for (int i = 0; i + 1 < seq.size(); i++) {
                String path = (String) seq.get(i)[0];
                if (path != null) {
                    paths.get(path).pair((double) seq.get(i)[1], (double) seq.get(i + 1)[1]);
                }
            }
        }

        List<PathStats> stats = paths.entrySet().stream().map(e -> e.getValue().stats(e.getKey())).toList();
        boolean sufficient = evaluated > 0 && stats.stream().allMatch(s -> s.followUpPairs() >= MIN_PAIRS);
        return new RecommendationEvaluation(k, evaluated,
                evaluated == 0 ? null : round(precisionSum / evaluated),
                withRelevant == 0 ? null : round(recallSum / withRelevant),
                stats, sufficient);
    }

    private static String path(QuizType type) {
        return switch (type) {
            case ADAPTIVE -> "ADAPTIVE";
            case PRACTICE, ASSESSMENT, REVISION -> "FIXED";
            case DIAGNOSTIC -> null;
        };
    }

    private static Double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    private static final class PathAccumulator {
        int attempts;
        double accuracySum;
        final Set<Long> students = new HashSet<>();
        int pairs;
        double nextSum;
        double gainSum;

        void attempt(Long studentId, double accuracy) {
            attempts++;
            accuracySum += accuracy;
            students.add(studentId);
        }

        void pair(double accuracy, double next) {
            pairs++;
            nextSum += next;
            gainSum += next - accuracy;
        }

        PathStats stats(String name) {
            return new PathStats(name, attempts, students.size(),
                    attempts == 0 ? null : round(accuracySum / attempts), pairs,
                    pairs == 0 ? null : round(nextSum / pairs), pairs == 0 ? null : round(gainSum / pairs));
        }
    }
}
