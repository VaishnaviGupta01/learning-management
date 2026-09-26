package com.lms.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises the real HTTP client against a local server: wire format (snake_case JSON), protocol
 * (no HTTP/2 upgrade, which uvicorn rejects) and error mapping.
 */
class HttpMlClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastUpgrade = new AtomicReference<>();
    private final AtomicReference<String> lastProtocol = new AtomicReference<>();
    private HttpServer server;
    private HttpMlClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/api/knowledge/score", 200,
                "{\"results\":[{\"topic_id\":7,\"score\":0.72,\"classification\":\"MODERATE\",\"components_used\":[]}],"
                        + "\"weights\":{},\"thresholds\":{}}");
        respond("/api/recommend", 200,
                "{\"recommendations\":[{\"topic_id\":3,\"rank\":1,\"priority\":0.67,\"reason\":\"Low mastery\"}],"
                        + "\"weights\":{}}");
        server.start();
        client = new HttpMlClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), 2000);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void scoreKnowledgeSendsSnakeCaseOverHttp11() throws Exception {
        List<MlClient.KnowledgeScore> scores = client.scoreKnowledge(
                List.of(new MlClient.TopicEvidence(7L, 1.0, null, 0.5, null)));

        assertThat(scores).containsExactly(new MlClient.KnowledgeScore(7L, 0.72, "MODERATE"));
        JsonNode sent = mapper.readTree(lastBody.get()).get("topics").get(0);
        assertThat(sent.get("topic_id").asLong()).isEqualTo(7);
        assertThat(sent.get("diagnostic").asDouble()).isEqualTo(1.0);
        assertThat(sent.get("practice").asDouble()).isEqualTo(0.5);
        assertThat(sent.has("recent_quiz")).isTrue();
        assertThat(lastProtocol.get()).isEqualTo("HTTP/1.1");
        assertThat(lastUpgrade.get()).isNull();
    }

    @Test
    void recommendSendsCandidatesAndTopK() throws Exception {
        List<MlClient.RankedTopic> ranked = client.recommend(
                List.of(new MlClient.RecommendCandidate(3L, 0.2, 0.9, 0.0)), 5);

        assertThat(ranked).containsExactly(new MlClient.RankedTopic(3L, 1, 0.67, "Low mastery"));
        JsonNode sent = mapper.readTree(lastBody.get());
        assertThat(sent.get("top_k").asInt()).isEqualTo(5);
        assertThat(sent.at("/topics/0/knowledge_score").asDouble()).isEqualTo(0.2);
        assertThat(sent.at("/topics/0/importance").asDouble()).isEqualTo(0.9);
    }

    @Test
    void errorsAreWrappedInMlServiceException() {
        server.removeContext("/api/recommend");
        respond("/api/recommend", 422, "{\"detail\":\"bad\"}");
        assertThatThrownBy(() -> client.recommend(List.of(), null)).isInstanceOf(MlServiceException.class);

        server.stop(0);
        assertThatThrownBy(() -> client.scoreKnowledge(List.of())).isInstanceOf(MlServiceException.class);
    }

    private void respond(String path, int status, String json) {
        server.createContext(path, exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastUpgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            lastProtocol.set(exchange.getProtocol());
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }
}
