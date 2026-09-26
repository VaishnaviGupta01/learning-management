package com.lms.rag;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.util.HttpRequestFactories;

@Component
public class HttpRagClient implements RagClient {

    static final String DEFAULT_USER_MESSAGE = "The AI service is temporarily unavailable. Please try again shortly.";

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
        return call("tutor chat", () -> restClient.post().uri("/api/tutor/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TutorReply.class));
    }

    @Override
    public IngestResult ingest(long documentId, long courseId, String title, String fileName, String contentType,
                               byte[] data) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        fileHeaders.setContentDispositionFormData("file", fileName);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("document_id", String.valueOf(documentId));
        parts.add("course_id", String.valueOf(courseId));
        parts.add("title", title);
        parts.add("file", new HttpEntity<>(new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return fileName;
            }
        }, fileHeaders));

        return call("ingest", () -> restClient.post().uri("/api/documents/ingest")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(IngestResult.class));
    }

    @Override
    public void deleteDocument(long courseId, long documentId) {
        call("delete document", () -> restClient.delete()
                .uri(b -> b.path("/api/documents/{id}").queryParam("course_id", courseId).build(documentId))
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T call(String what, Supplier<T> request) {
        try {
            T body = request.get();
            if (body == null) {
                throw new RagServiceException("Empty response from rag-service (" + what + ")", DEFAULT_USER_MESSAGE, null);
            }
            return body;
        } catch (RestClientResponseException e) {
            // rag-service puts a user-safe explanation in FastAPI's "detail" field (e.g. "not configured")
            int status = e.getStatusCode().value();
            throw new RagServiceException("rag-service " + what + " returned " + status,
                    detail(e.getResponseBodyAsString()), status, e);
        } catch (RestClientException e) {
            throw new RagServiceException("rag-service " + what + " failed: " + e.getMessage(), DEFAULT_USER_MESSAGE, e);
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
