package com.lms.dto.tutor;

import java.time.Instant;

import com.lms.entity.AiInteraction;
import com.lms.entity.enums.AiInteractionType;

/** {@code response} is null for exchanges that failed before the tutor answered. */
public record AiInteractionResponse(
        Long id,
        AiInteractionType type,
        Long courseId,
        Long topicId,
        String prompt,
        String response,
        String modelName,
        Integer inputTokens,
        Integer outputTokens,
        Long latencyMs,
        Instant createdAt) {

    public static AiInteractionResponse from(AiInteraction i) {
        return new AiInteractionResponse(i.getId(), i.getType(),
                i.getCourse() == null ? null : i.getCourse().getId(),
                i.getTopic() == null ? null : i.getTopic().getId(),
                i.getPrompt(), i.getResponse(), i.getModelName(), i.getInputTokens(), i.getOutputTokens(),
                i.getLatencyMs(), i.getCreatedAt());
    }
}
