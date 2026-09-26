package com.lms.dto.document;

import java.time.Instant;

import com.lms.entity.Document;

public record DocumentResponse(
        Long id,
        Long courseId,
        Long topicId,
        String title,
        String fileName,
        String contentType,
        Long fileSize,
        boolean processed,
        int chunkCount,
        Long uploadedById,
        Instant createdAt) {

    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getCourse().getId(), d.getTopic() == null ? null : d.getTopic().getId(),
                d.getTitle(), d.getFileName(), d.getContentType(), d.getFileSize(), d.isProcessed(), d.getChunkCount(),
                d.getUploadedBy().getId(), d.getCreatedAt());
    }
}
