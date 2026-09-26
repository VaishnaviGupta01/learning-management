package com.lms.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.lms.dto.course.CourseRequest;
import com.lms.dto.course.CourseResponse;
import com.lms.entity.enums.RoleName;
import com.lms.security.CustomUserDetailsService;
import com.lms.security.JwtService;
import com.lms.security.SecurityConfig;
import com.lms.security.UserPrincipal;
import com.lms.service.CourseService;
import com.lms.service.TopicService;

import io.jsonwebtoken.JwtException;

/**
 * Controller slice with the real security chain (JWT filter, entry point, method security) and mocked services.
 * Covers the auth failure modes: no token, bad/expired JWT, wrong role, plus validation.
 */
@WebMvcTest(CourseController.class)
@Import(SecurityConfig.class)
class CourseControllerWebMvcTest {

    @Autowired MockMvc mvc;

    @MockBean CourseService courseService;
    @MockBean TopicService topicService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService userDetailsService;

    private static final String BODY = "{\"code\":\"CS101\",\"title\":\"Intro\"}";

    @Test
    void missingTokenIs401WithJsonError() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void invalidOrExpiredJwtIs401() throws Exception {
        when(jwtService.extractSubject("expired.jwt")).thenThrow(new JwtException("JWT expired"));
        mvc.perform(get("/api/courses").header("Authorization", "Bearer expired.jwt"))
                .andExpect(status().isUnauthorized());
        verify(courseService, never()).listCourses(any());
    }

    @Test
    void jwtForDisabledUserIs401() throws Exception {
        tokenFor("off.jwt", new UserPrincipal(3L, "off@x.io", "h", RoleName.INSTRUCTOR, false));
        mvc.perform(get("/api/courses").header("Authorization", "Bearer off.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotCreateCourse() throws Exception {
        tokenFor("student.jwt", new UserPrincipal(1L, "s@x.io", "h", RoleName.STUDENT, true));
        mvc.perform(post("/api/courses").header("Authorization", "Bearer student.jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(courseService, never()).createCourse(any(), any());
    }

    @Test
    void instructorCreatesCourse() throws Exception {
        UserPrincipal instructor = new UserPrincipal(2L, "i@x.io", "h", RoleName.INSTRUCTOR, true);
        tokenFor("instructor.jwt", instructor);
        when(courseService.createCourse(any(CourseRequest.class), eq(instructor))).thenReturn(
                new CourseResponse(10L, "CS101", "Intro", null, false, 2L, "I N", 0, Instant.now(), Instant.now()));

        mvc.perform(post("/api/courses").header("Authorization", "Bearer instructor.jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.code").value("CS101"));
    }

    @Test
    void validationErrorsAre400WithFieldErrors() throws Exception {
        tokenFor("instructor.jwt", new UserPrincipal(2L, "i@x.io", "h", RoleName.INSTRUCTOR, true));
        mvc.perform(post("/api/courses").header("Authorization", "Bearer instructor.jwt")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"bad code!\",\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code", containsString("letters")))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void anyAuthenticatedRoleCanListCourses() throws Exception {
        UserPrincipal student = new UserPrincipal(1L, "s@x.io", "h", RoleName.STUDENT, true);
        tokenFor("student.jwt", student);
        when(courseService.listCourses(student)).thenReturn(List.of());
        mvc.perform(get("/api/courses").header("Authorization", "Bearer student.jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private void tokenFor(String token, UserPrincipal user) {
        when(jwtService.extractSubject(token)).thenReturn(user.getUsername());
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);
    }
}
