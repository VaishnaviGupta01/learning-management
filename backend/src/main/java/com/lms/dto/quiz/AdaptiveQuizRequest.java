package com.lms.dto.quiz;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * {@code questionCount} defaults to {@code app.adaptive.default-question-count};
 * {@code topicIds} limits the quiz to some topics of the course (all topics when null or empty).
 */
public record AdaptiveQuizRequest(
        @NotNull Long courseId,
        @Min(1) @Max(50) Integer questionCount,
        List<@NotNull Long> topicIds) {
}
