package com.lms.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.util.HttpRequestFactories;

@Component
public class HttpRagClient implements RagClient {

    static final String DEFAULT_USER_MESSAGE = "The AI tutor is temporarily unavailable. Please try again shortly.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpRagClient(RestClient.Builder builder, ObjectMapper objectMapper,
                         @Value("${app.services.rag-url}") String baseUrl,
                         @Value("${app.services.rag-timeout-ms:120000}") long timeoutMs) {
        this.restClient = builder.baseUrl(baseUrl).requestFactory(HttpRequestFactories.http11(timeoutMs)).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public TutorReply chat(TutorRequest request) {
        try {
            TutorReply reply = restClient.post().uri("/api/tutor/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TutorReply.class);
            if (reply == null) {
                throw new RagServiceException("Empty response from rag-service", DEFAULT_USER_MESSAGE, null);
            }
            return reply;
        } catch (RestClientResponseException e) {
            // rag-service puts a user-safe explanation in FastAPI's "detail" field (e.g. "not configured")
            throw new RagServiceException("rag-service returned " + e.getStatusCode().value(),
                    detail(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new RagServiceException("rag-service call failed: " + e.getMessage(), DEFAULT_USER_MESSAGE, e);
        }
    }

    private String detail(String body) {
        try {
            JsonNode detail = objectMapper.readTree(body).get("detail");
            return detail != null && detail.isTextual() ? detail.asText() : DEFAULT_USER_MESSAGE;
        } catch (Exception e) {
            return DEFAULT_USER_MESSAGE;
        }
    }
}
