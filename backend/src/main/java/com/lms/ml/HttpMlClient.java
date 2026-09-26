package com.lms.ml;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpMlClient implements MlClient {

    private final RestClient restClient;

    public HttpMlClient(RestClient.Builder builder,
                        @Value("${app.services.ml-url}") String baseUrl,
                        @Value("${app.services.ml-timeout-ms:3000}") long timeoutMs) {
        // HTTP/1.1 explicitly: the JDK client defaults to HTTP/2 and sends an h2c upgrade request,
        // which uvicorn rejects (the request body is then lost and FastAPI answers 422).
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build());
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public List<KnowledgeScore> scoreKnowledge(List<TopicEvidence> topics) {
        return post("/api/knowledge/score", Map.of("topics", topics), KnowledgeResponse.class).results();
    }

    @Override
    public List<RankedTopic> recommend(List<RecommendCandidate> candidates, Integer topK) {
        Map<String, Object> body = new HashMap<>();
        body.put("topics", candidates);
        body.put("top_k", topK);
        return post("/api/recommend", body, RecommendResponse.class).recommendations();
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            T response = restClient.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
            if (response == null) {
                throw new MlServiceException("Empty response from ml-service " + path, null);
            }
            return response;
        } catch (RestClientException e) {
            throw new MlServiceException("ml-service call failed: " + path + " (" + e.getMessage() + ")", e);
        }
    }

    private record KnowledgeResponse(List<KnowledgeScore> results) {
    }

    private record RecommendResponse(List<RankedTopic> recommendations) {
    }
}
