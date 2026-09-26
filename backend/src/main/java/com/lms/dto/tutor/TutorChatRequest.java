package com.lms.dto.tutor;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One tutor turn. {@code courseId}/{@code topicId} are optional and give the tutor context; when only
 * {@code topicId} is sent its course is used. {@code history} is the conversation so far (oldest first).
 */
public record TutorChatRequest(
        @NotBlank @Size(max = 4000) String message,
        Long courseId,
        Long topicId,
        @Size(max = 20) List<@Valid @NotNull Turn> history) {

    public record Turn(
            @NotNull @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 8000) String content) {
    }
}
