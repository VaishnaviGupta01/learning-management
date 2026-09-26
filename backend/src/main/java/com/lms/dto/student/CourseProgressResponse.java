package com.lms.dto.student;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A student's progress in one course.
 * Completion = distinct approved questions answered correctly / approved questions (per topic and course-wide).
 * Accuracy = correct answers / answers given, over the whole QuestionAttempt history.
 */
public record CourseProgressResponse(
        Long courseId,
        String courseCode,
        String courseTitle,
        BigDecimal completionPercentage,
        int topicsCompleted,
        int totalTopics,
        int totalStudyMinutes,
        Instant enrolledAt,
        Instant lastAccessedAt,
        List<TopicProgress> topics) {

    public record TopicProgress(
            Long topicId,
            String title,
            long approvedQuestions,
            long questionsMastered,
            BigDecimal completionPercentage,
            long answersGiven,
            long correctAnswers,
            BigDecimal accuracyPercentage) {
    }
}
