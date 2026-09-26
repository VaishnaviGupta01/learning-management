package com.lms.dto.document;

import com.lms.entity.DocumentChunk;

public record DocumentChunkResponse(
        Long id,
        int chunkIndex,
        Integer pageNumber,
        Integer tokenCount,
        String embeddingId,
        String content) {

    public static DocumentChunkResponse from(DocumentChunk c) {
        return new DocumentChunkResponse(c.getId(), c.getChunkIndex(), c.getPageNumber(), c.getTokenCount(),
                c.getEmbeddingId(), c.getContent());
    }
}
