package com.lms.dto.quiz;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lms.dto.question.QuestionResponse;
import com.lms.entity.enums.CreatedByType;
import com.lms.entity.enums.QuizType;

/** {@code questions} (with answers) is only populated for instructors/admins who manage the course. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizResponse(
        Long id,
        Long courseId,
        Long topicId,
        String title,
        String description,
        QuizType type,
        Integer timeLimitMinutes,
        BigDecimal passingScore,
        CreatedByType createdByType,
        boolean published,
        Long studentId,
        int questionCount,
        Instant createdAt,
        List<QuestionResponse> questions) {
}
