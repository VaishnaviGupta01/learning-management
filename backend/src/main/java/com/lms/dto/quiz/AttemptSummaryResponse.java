package com.lms.dto.quiz;

import java.math.BigDecimal;
import java.time.Instant;

import com.lms.entity.QuizAttempt;
import com.lms.entity.enums.QuizType;

public record AttemptSummaryResponse(
        Long attemptId,
        Long quizId,
        String quizTitle,
        QuizType quizType,
        Instant startedAt,
        Instant submittedAt,
        BigDecimal percentage) {

    public static AttemptSummaryResponse from(QuizAttempt a) {
        return new AttemptSummaryResponse(a.getId(), a.getQuiz().getId(), a.getQuiz().getTitle(),
                a.getQuiz().getType(), a.getStartedAt(), a.getSubmittedAt(), a.getPercentage());
    }
}
