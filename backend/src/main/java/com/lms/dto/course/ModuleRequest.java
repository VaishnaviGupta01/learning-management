package com.lms.dto.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** {@code orderIndex} defaults to "append at the end" when omitted on create. */
public record ModuleRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @PositiveOrZero Integer orderIndex) {
}
