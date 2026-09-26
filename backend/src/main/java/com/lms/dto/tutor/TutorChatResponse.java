package com.lms.dto.tutor;

import java.util.List;

import com.lms.rag.RagClient.SourceRef;

/**
 * {@code grounded}: the course has uploaded material and the answer was built from it; {@code sources} lists the
 * chunks cited as [n]. {@code notInMaterial}: nothing relevant was found, so the tutor declined instead of guessing.
 */
public record TutorChatResponse(
        Long interactionId,
        String reply,
        String model,
        boolean refused,
        long latencyMs,
        boolean grounded,
        boolean notInMaterial,
        List<Source> sources) {

    public record Source(int ref, Long documentId, String title, int chunkIndex, int pageNumber, double score) {

        public static Source from(SourceRef s) {
            return new Source(s.ref(), s.documentId(), s.title(), s.chunkIndex(), s.pageNumber(), s.score());
        }
    }
}
