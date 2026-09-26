package com.lms.rag;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Client for the Python rag-service. Throws {@link RagServiceException} on any failure. */
public interface RagClient {

    /** POST /api/tutor/chat */
    TutorReply chat(TutorRequest request);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TutorRequest(String message, List<Turn> history, StudentContext context) {
    }

    record Turn(String role, String content) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StudentContext(String studentName, String courseTitle, String courseDescription, String topicTitle,
                          String topicDescription, KnowledgeLevel topicKnowledge, List<KnowledgeLevel> weakTopics) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record KnowledgeLevel(String topicTitle, String classification, Double masteryScore) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TutorReply(String reply, String model, String stopReason, boolean refused, int inputTokens,
                      int outputTokens) {
    }
}
