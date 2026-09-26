package com.lms.dto.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CourseRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "may contain only letters, digits, '-' and '_'")
        String code,
        @NotBlank @Size(max = 255) String title,
        String description,
        Boolean published) {
}
