package com.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class AuthIntegrationTest extends IntegrationTestSupport {

    @Test
    void registerLoginAndFetchCurrentUser() throws Exception {
        String email = uniqueEmail("alice");
        JsonNode registered = post_("/api/auth/register", null, Map.of(
                "email", email.toUpperCase(), "password", PASSWORD, "firstName", "Alice", "lastName", "Doe"), 201);
        assertThat(registered.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(registered.at("/user/role").asText()).isEqualTo("STUDENT"); // default role
        assertThat(registered.at("/user/email").asText()).isEqualTo(email);   // normalised to lower case

        JsonNode login = post_("/api/auth/login", null, Map.of("email", email, "password", PASSWORD), 200);
        String token = login.get("token").asText();

        JsonNode me = get_("/api/users/me", token, 200);
        assertThat(me.get("email").asText()).isEqualTo(email);
        assertThat(me.has("passwordHash")).isFalse();
    }

    @Test
    void selfRegisteringAsAdminIsBlocked() throws Exception {
        JsonNode error = post_("/api/auth/register", null, Map.of(
                "email", uniqueEmail("mallory"), "password", PASSWORD,
                "firstName", "Mal", "lastName", "Lory", "role", "ADMIN"), 400);
        assertThat(error.at("/fieldErrors/roleAllowed").asText()).contains("ADMIN");
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        Map<String, Object> body = Map.of("email", uniqueEmail("dup"), "password", PASSWORD,
                "firstName", "A", "lastName", "B");
        post_("/api/auth/register", null, body, 201);
        JsonNode error = post_("/api/auth/register", null, body, 409);
        assertThat(error.get("message").asText()).contains("already exists");
    }

    @Test
    void badCredentialsReturn401() throws Exception {
        String email = uniqueEmail("bob");
        post_("/api/auth/register", null, Map.of("email", email, "password", PASSWORD,
                "firstName", "Bob", "lastName", "B"), 201);
        post_("/api/auth/login", null, Map.of("email", email, "password", "wrong-password"), 401);
        post_("/api/auth/login", null, Map.of("email", uniqueEmail("nobody"), "password", PASSWORD), 401);
    }

    @Test
    void invalidRegistrationReturns400WithFieldErrors() throws Exception {
        JsonNode error = post_("/api/auth/register", null, Map.of(
                "email", "not-an-email", "password", "short", "firstName", "", "lastName", "X"), 400);
        assertThat(error.get("fieldErrors").has("email")).isTrue();
        assertThat(error.get("fieldErrors").has("password")).isTrue();
        assertThat(error.get("fieldErrors").has("firstName")).isTrue();
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        get_("/api/users/me", null, 401);
        get_("/api/users/me", "not.a.jwt", 401);
        get_("/api/courses", null, 401);
        get_("/api/health", null, 200);
    }

    @Test
    void seededAdminCanLogIn() throws Exception {
        JsonNode login = post_("/api/auth/login", null,
                Map.of("email", "admin@test.local", "password", "AdminPass123!"), 200);
        assertThat(login.at("/user/role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void studentCannotCreateCourse() throws Exception {
        String student = registerAs("STUDENT");
        JsonNode error = post_("/api/courses", student, Map.of("code", uniqueCode("X"), "title", "Nope"), 403);
        assertThat(error.get("status").asInt()).isEqualTo(403);
    }
}
