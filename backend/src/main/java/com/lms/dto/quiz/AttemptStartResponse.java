package com.lms.dto.quiz;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.lms.entity.enums.Difficulty;
import com.lms.entity.enums.QuizType;

/**
 * What a student sees when taking a quiz. Deliberately carries no correctness information and no
 * explanations; those are only revealed in {@link AttemptResultResponse} after submission.
 */
public record AttemptStartResponse(
        Long attemptId,
        Long quizId,
        String quizTitle,
        QuizType quizType,
        Integer timeLimitMinutes,
        Instant startedAt,
        List<AttemptQuestion> questions) {

    public record AttemptQuestion(
            Long questionId,
            Long topicId,
            String text,
            Difficulty difficulty,
            BigDecimal points,
            List<AttemptOption> options) {
    }

    public record AttemptOption(Long id, String text) {
    }
}
