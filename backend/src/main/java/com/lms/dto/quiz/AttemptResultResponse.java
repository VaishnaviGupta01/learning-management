package com.lms.dto.quiz;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AttemptResultResponse(
        Long attemptId,
        Long quizId,
        String quizTitle,
        Long studentId,
        Instant startedAt,
        Instant submittedAt,
        BigDecimal score,
        BigDecimal maxScore,
        BigDecimal percentage,
        Boolean passed,
        List<QuestionResult> results) {

    public record QuestionResult(
            Long questionId,
            Long topicId,
            Long selectedOptionId,
            Long correctOptionId,
            boolean correct,
            BigDecimal points,
            String explanation) {
    }
}
