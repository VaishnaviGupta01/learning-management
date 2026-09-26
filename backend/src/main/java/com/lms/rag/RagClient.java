package com.lms.rag;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Client for the Python rag-service. Throws {@link RagServiceException} on any failure. */
public interface RagClient {

    /** POST /api/tutor/chat */
    TutorReply chat(TutorRequest request);

    /** POST /api/documents/ingest - extract, chunk, embed and index; returns the chunks to persist. */
    IngestResult ingest(long documentId, long courseId, String title, String fileName, String contentType,
                        byte[] data);

    /** DELETE /api/documents/{id}?course_id= - removes the document's vectors. */
    void deleteDocument(long courseId, long documentId);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TutorRequest(String message, List<Turn> history, StudentContext context) {
    }

    record Turn(String role, String content) {
    }

    /** {@code courseId} enables retrieval over that course's uploaded material. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StudentContext(Long courseId, String studentName, String courseTitle, String courseDescription,
                          String topicTitle, String topicDescription, KnowledgeLevel topicKnowledge,
                          List<KnowledgeLevel> weakTopics) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record KnowledgeLevel(String topicTitle, String classification, Double masteryScore) {
    }

    /** {@code model} is null when no LLM call was made (e.g. the question is not in the course material). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TutorReply(String reply, String model, String stopReason, boolean refused, int inputTokens,
                      int outputTokens, boolean grounded, boolean notInMaterial, List<SourceRef> sources) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SourceRef(int ref, Long documentId, String title, int chunkIndex, int pageNumber, double score) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record IngestResult(Long documentId, Long courseId, int pages, int chunkCount, List<IngestedChunk> chunks) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record IngestedChunk(int chunkIndex, String content, int tokenCount, int pageNumber, String embeddingId) {
    }
}
