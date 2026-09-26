package com.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class CourseIntegrationTest extends IntegrationTestSupport {

    /**
     * The spec's DSA graph: Recursion -> Trees -> Graphs, Arrays -> Searching, Arrays -> Sorting.
     * Topics are created in an order that is NOT already a valid learning order.
     */
    @Test
    void prerequisiteGraphLearningPathAndCycleRejection() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        long courseId = createCourse(instructor, true);
        long m1 = createModule(instructor, courseId, "Foundations");
        long m2 = createModule(instructor, courseId, "Advanced");

        long graphs = createTopic(instructor, m1, "Graphs");
        long arrays = createTopic(instructor, m1, "Arrays");
        long trees = createTopic(instructor, m1, "Trees");
        long recursion = createTopic(instructor, m1, "Recursion");
        long searching = createTopic(instructor, m2, "Searching");
        long sorting = createTopic(instructor, m2, "Sorting");

        addPrereq(instructor, trees, recursion, 201);
        addPrereq(instructor, graphs, trees, 201);
        addPrereq(instructor, searching, arrays, 201);
        addPrereq(instructor, sorting, arrays, 201);

        JsonNode path = get_("/api/courses/" + courseId + "/learning-path", instructor, 200);
        List<String> order = new ArrayList<>();
        path.get("topics").forEach(t -> order.add(t.get("title").asText()));
        assertThat(order).containsExactly("Arrays", "Recursion", "Trees", "Graphs", "Searching", "Sorting");

        // Recursion requires Graphs would close Recursion -> Trees -> Graphs -> Recursion (a transitive cycle)
        JsonNode error = addPrereq(instructor, recursion, graphs, 400);
        assertThat(error.get("message").asText()).contains("cycle").contains("Recursion").contains("Graphs");

        addPrereq(instructor, trees, trees, 400);      // self-loop
        addPrereq(instructor, trees, recursion, 409);  // duplicate edge

        JsonNode detail = get_("/api/courses/" + courseId, instructor, 200);
        assertThat(detail.get("modules")).hasSize(2);
        assertThat(detail.at("/modules/0/topics")).hasSize(4);

        // removing an edge then re-adding the reverse direction is now allowed
        delete_("/api/topics/" + graphs + "/prerequisites/" + trees, instructor, 204);
        addPrereq(instructor, trees, graphs, 201);
    }

    @Test
    void prerequisitesMustBeInSameCourse() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        long a = createTopic(instructor, createModule(instructor, createCourse(instructor, false), "M"), "A");
        long b = createTopic(instructor, createModule(instructor, createCourse(instructor, false), "M"), "B");
        addPrereq(instructor, a, b, 400);
    }

    @Test
    void onlyOwningInstructorOrAdminCanModify() throws Exception {
        String owner = registerAs("INSTRUCTOR");
        String other = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(owner, false);

        Map<String, Object> update = Map.of("code", uniqueCode("UPD"), "title", "Renamed");
        put_("/api/courses/" + courseId, other, update, 403);
        post_("/api/courses/" + courseId + "/modules", other, Map.of("title", "M"), 403);
        post_("/api/courses/" + courseId + "/modules", student, Map.of("title", "M"), 403);

        String admin = post_("/api/auth/login", null,
                Map.of("email", "admin@test.local", "password", "AdminPass123!"), 200).get("token").asText();
        put_("/api/courses/" + courseId, admin, update, 200);
    }

    @Test
    void unpublishedCoursesAreHiddenFromStudents() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, false);

        get_("/api/courses/" + courseId, student, 404);
        assertThat(ids(get_("/api/courses", student, 200))).doesNotContain(courseId);

        patch_("/api/courses/" + courseId + "/publish", instructor, 200);
        get_("/api/courses/" + courseId, student, 200);
        assertThat(ids(get_("/api/courses", student, 200))).contains(courseId);
    }

    @Test
    void duplicateCourseCodeReturns409() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String code = uniqueCode("DUP");
        post_("/api/courses", instructor, Map.of("code", code, "title", "One"), 201);
        post_("/api/courses", instructor, Map.of("code", code, "title", "Two"), 409);
    }

    @Test
    void deletingTopicRemovesItsPrerequisiteEdges() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        long courseId = createCourse(instructor, false);
        long m = createModule(instructor, courseId, "M");
        long a = createTopic(instructor, m, "A");
        long b = createTopic(instructor, m, "B");
        addPrereq(instructor, b, a, 201);

        delete_("/api/topics/" + a, instructor, 204);
        assertThat(get_("/api/topics/" + b + "/prerequisites", instructor, 200)).isEmpty();
        delete_("/api/modules/" + m, instructor, 204);
        assertThat(get_("/api/courses/" + courseId, instructor, 200).get("modules")).isEmpty();
    }

    private JsonNode addPrereq(String token, long topicId, long prerequisiteId, int status) throws Exception {
        return post_("/api/topics/" + topicId + "/prerequisites", token,
                Map.of("prerequisiteTopicId", prerequisiteId), status);
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }
}
