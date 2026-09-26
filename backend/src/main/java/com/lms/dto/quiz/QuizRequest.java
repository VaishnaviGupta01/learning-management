package com.lms.dto.quiz;

import java.math.BigDecimal;
import java.util.List;

import com.lms.entity.enums.QuizType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Instructor-built quiz. Questions must be APPROVED and belong to the course; each is worth 1 point.
 * {@code passingScore} is a percentage (0-100).
 */
public record QuizRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        QuizType type,
        Long topicId,
        @Positive Integer timeLimitMinutes,
        @DecimalMin("0") @DecimalMax("100") BigDecimal passingScore,
        @NotEmpty List<@NotNull Long> questionIds,
        Boolean published) {
}
