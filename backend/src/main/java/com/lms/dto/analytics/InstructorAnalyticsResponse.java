package com.lms.dto.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.lms.entity.enums.TopicClassification;

public record InstructorAnalyticsResponse(
        Long courseId,
        String courseTitle,
        int enrolledStudents,
        BigDecimal averageCompletionPercentage,
        long submittedAttempts,
        BigDecimal averageScorePercentage,
        long pendingQuestions,
        long documents,
        Map<TopicClassification, Long> knowledgeDistribution,
        List<TopicPerformance> topics,
        RecommendationEvaluation evaluation) {

    /** Accuracy across all students' answers; strugglingStudents = students in the WEAK or NEEDS_PRACTICE band. */
    public record TopicPerformance(
            Long topicId,
            String title,
            long approvedQuestions,
            long answers,
            long correctAnswers,
            BigDecimal accuracyPercentage,
            long studentsAttempted,
            long strugglingStudents,
            Double averageMastery) {
    }
}
