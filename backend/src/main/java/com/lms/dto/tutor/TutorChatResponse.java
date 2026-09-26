package com.lms.dto.tutor;

public record TutorChatResponse(
        Long interactionId,
        String reply,
        String model,
        boolean refused,
        long latencyMs) {
}
