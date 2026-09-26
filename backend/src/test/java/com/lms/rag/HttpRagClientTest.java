package com.lms.rag;

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

/** Real HTTP round trip: snake_case payload, HTTP/1.1, and FastAPI error details surfaced safely. */
class HttpRagClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastUpgrade = new AtomicReference<>();
    private HttpServer server;
    private HttpRagClient client;
    private volatile int status = 200;
    private volatile String responseJson = "{\"reply\":\"Hi\",\"model\":\"claude-opus-5\",\"stop_reason\":\"end_turn\","
            + "\"refused\":false,\"input_tokens\":12,\"output_tokens\":3}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tutor/chat", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastUpgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new HttpRagClient(RestClient.builder(), mapper,
                "http://127.0.0.1:" + server.getAddress().getPort(), 2000);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsSnakeCaseContextAndParsesReply() throws Exception {
        RagClient.TutorReply reply = client.chat(new RagClient.TutorRequest("What is a heap?",
                List.of(new RagClient.Turn("user", "hi")),
                new RagClient.StudentContext("Sam", "DSA", null, "Heaps", null,
                        new RagClient.KnowledgeLevel("Heaps", "WEAK", 0.2), List.of())));

        assertThat(reply).isEqualTo(new RagClient.TutorReply("Hi", "claude-opus-5", "end_turn", false, 12, 3));
        JsonNode sent = mapper.readTree(lastBody.get());
        assertThat(sent.get("message").asText()).isEqualTo("What is a heap?");
        assertThat(sent.at("/history/0/role").asText()).isEqualTo("user");
        assertThat(sent.at("/context/student_name").asText()).isEqualTo("Sam");
        assertThat(sent.at("/context/topic_knowledge/mastery_score").asDouble()).isEqualTo(0.2);
        assertThat(sent.get("context").has("course_description")).isFalse(); // nulls omitted
        assertThat(lastUpgrade.get()).isNull();
    }

    @Test
    void errorDetailFromFastApiBecomesUserMessage() {
        status = 503;
        responseJson = "{\"detail\":\"AI tutor is not configured (set ANTHROPIC_API_KEY)\"}";
        assertThatThrownBy(() -> client.chat(new RagClient.TutorRequest("x", List.of(), null)))
                .isInstanceOf(RagServiceException.class)
                .extracting(e -> ((RagServiceException) e).getUserMessage())
                .isEqualTo("AI tutor is not configured (set ANTHROPIC_API_KEY)");

        server.stop(0);
        assertThatThrownBy(() -> client.chat(new RagClient.TutorRequest("x", List.of(), null)))
                .isInstanceOf(RagServiceException.class)
                .extracting(e -> ((RagServiceException) e).getUserMessage())
                .isEqualTo(HttpRagClient.DEFAULT_USER_MESSAGE);
    }
}
