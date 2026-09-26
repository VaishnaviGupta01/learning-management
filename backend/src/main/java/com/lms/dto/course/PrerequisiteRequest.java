package com.lms.dto.course;

import jakarta.validation.constraints.NotNull;

public record PrerequisiteRequest(@NotNull Long prerequisiteTopicId) {
}
