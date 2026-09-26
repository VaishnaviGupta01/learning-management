package com.lms;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.lms.rag.RagClient;
import com.lms.rag.RagServiceException;

/**
 * In-process stand-in for rag-service. Chat returns a canned reply (or fails); ingest splits the file into
 * one chunk per line and remembers it, so chat can report the course as grounded.
 */
public class FakeRagClient implements RagClient {

    public volatile TutorRequest lastRequest;
    public volatile String failWith;
    public volatile int failStatus;
    /** courseId -> documentId -> number of chunks */
    public final Map<Long, Map<Long, Integer>> indexed = new ConcurrentHashMap<>();

    @Override
    public TutorReply chat(TutorRequest request) {
        lastRequest = request;
        failIfConfigured();
        Long courseId = request.context() == null ? null : request.context().courseId();
        boolean grounded = courseId != null && !indexed.getOrDefault(courseId, Map.of()).isEmpty();
        List<SourceRef> sources = grounded
                ? List.of(new SourceRef(1, indexed.get(courseId).keySet().iterator().next(), "notes", 0, 1, 0.83))
                : List.of();
        return new TutorReply("Tutor says: " + request.message(), "claude-opus-5", "end_turn", false, 120, 30,
                grounded, false, sources);
    }

    @Override
    public IngestResult ingest(long documentId, long courseId, String title, String fileName, String contentType,
                               byte[] data) {
        failIfConfigured();
        String[] lines = new String(data, StandardCharsets.UTF_8).split("\\R");
        List<IngestedChunk> chunks = new ArrayList<>();
        for (String line : lines) {
            if (!line.isBlank()) {
                chunks.add(new IngestedChunk(chunks.size(), line.trim(), line.split("\\s+").length, 1,
                        courseId + ":" + (documentId * 100 + chunks.size())));
            }
        }
        indexed.computeIfAbsent(courseId, k -> new ConcurrentHashMap<>()).put(documentId, chunks.size());
        return new IngestResult(documentId, courseId, 1, chunks.size(), chunks);
    }

    @Override
    public void deleteDocument(long courseId, long documentId) {
        failIfConfigured();
        indexed.getOrDefault(courseId, new ConcurrentHashMap<>()).remove(documentId);
    }

    public void reset() {
        lastRequest = null;
        failWith = null;
        failStatus = 0;
    }

    private void failIfConfigured() {
        if (failWith != null) {
            throw new RagServiceException("fake failure", failWith, failStatus, null);
        }
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        public FakeRagClient fakeRagClient() {
            return new FakeRagClient();
        }
    }
}
