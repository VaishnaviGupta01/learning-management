package com.lms.dto.course;

import com.lms.entity.enums.Difficulty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** {@code orderIndex} defaults to "append at the end" and {@code difficulty} to MEDIUM when omitted on create. */
public record TopicRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @PositiveOrZero Integer orderIndex,
        Difficulty difficulty,
        @Positive Integer estimatedMinutes) {
}
