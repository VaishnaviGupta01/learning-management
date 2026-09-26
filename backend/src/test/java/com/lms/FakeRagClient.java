package com.lms;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.lms.rag.RagClient;
import com.lms.rag.RagServiceException;

/** In-process stand-in for rag-service: records the last request and returns a canned reply (or fails). */
public class FakeRagClient implements RagClient {

    public volatile TutorRequest lastRequest;
    public volatile String failWith;

    @Override
    public TutorReply chat(TutorRequest request) {
        lastRequest = request;
        if (failWith != null) {
            throw new RagServiceException("fake failure", failWith, null);
        }
        return new TutorReply("Tutor says: " + request.message(), "claude-opus-5", "end_turn", false, 120, 30);
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
