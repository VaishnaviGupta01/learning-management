package com.lms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** MockMvc helpers shared by the end-to-end API tests. Every test creates its own users/courses. */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "Password123!";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected static String uniqueEmail(String prefix) {
        return prefix + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
    }

    protected static String uniqueCode(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet() + "-" + (System.nanoTime() % 1_000_000);
    }

    /** Registers a user with the given role and returns their bearer token. */
    protected String registerAs(String role) throws Exception {
        JsonNode body = call(post("/api/auth/register"), null, Map.of(
                "email", uniqueEmail(role.toLowerCase()),
                "password", PASSWORD,
                "firstName", "Test",
                "lastName", role,
                "role", role), 201);
        return body.get("token").asText();
    }

    protected JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int expectedStatus)
            throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString();
        if (result.getResponse().getStatus() != expectedStatus) {
            throw new AssertionError("Expected " + expectedStatus + " but got " + result.getResponse().getStatus()
                    + " for " + result.getRequest().getMethod() + " " + result.getRequest().getRequestURI()
                    + ": " + content);
        }
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }

    protected JsonNode get_(String url, String token, int status) throws Exception {
        return call(get(url), token, null, status);
    }

    protected JsonNode post_(String url, String token, Object body, int status) throws Exception {
        return call(post(url), token, body, status);
    }

    protected JsonNode put_(String url, String token, Object body, int status) throws Exception {
        return call(put(url), token, body, status);
    }

    protected JsonNode patch_(String url, String token, int status) throws Exception {
        return call(patch(url), token, null, status);
    }

    protected JsonNode delete_(String url, String token, int status) throws Exception {
        return call(delete(url), token, null, status);
    }

    // ------------------------------------------------------------------ domain helpers

    protected long createCourse(String token, boolean published) throws Exception {
        return post_("/api/courses", token, Map.of(
                "code", uniqueCode("DSA"), "title", "Data Structures", "published", published), 201)
                .get("id").asLong();
    }

    protected long createModule(String token, long courseId, String title) throws Exception {
        return post_("/api/courses/" + courseId + "/modules", token, Map.of("title", title), 201).get("id").asLong();
    }

    protected long createTopic(String token, long moduleId, String title) throws Exception {
        return post_("/api/modules/" + moduleId + "/topics", token, Map.of("title", title), 201).get("id").asLong();
    }

    /** Creates a 4-option question whose correct answer is the option at {@code correctIndex}. */
    protected JsonNode createQuestion(String token, long topicId, String text, int correctIndex) throws Exception {
        var options = new java.util.ArrayList<Map<String, Object>>();
        for (int i = 0; i < 4; i++) {
            options.add(Map.of("text", text + " option " + i, "correct", i == correctIndex));
        }
        return post_("/api/topics/" + topicId + "/questions", token, Map.of(
                "text", text, "explanation", "Because " + correctIndex, "options", options), 201);
    }
}
