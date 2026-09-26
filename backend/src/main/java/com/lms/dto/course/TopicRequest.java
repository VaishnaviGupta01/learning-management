package com.lms.dto.course;

import com.lms.entity.enums.Difficulty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * {@code orderIndex} defaults to "append at the end", {@code difficulty} to MEDIUM and
 * {@code importance} (0..1, used to rank recommendations) to 0.5 when omitted on create.
 */
public record TopicRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @PositiveOrZero Integer orderIndex,
        Difficulty difficulty,
        @Positive Integer estimatedMinutes,
        @DecimalMin("0.0") @DecimalMax("1.0") Double importance) {
}
