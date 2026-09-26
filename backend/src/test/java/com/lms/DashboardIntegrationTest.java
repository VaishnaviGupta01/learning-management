package com.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.lms.repository.StudySessionRepository;

/** Phase 12 endpoints: student activity, instructor analytics with recommendation evaluation, admin stats. */
class DashboardIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private StudySessionRepository sessionRepository;

    private final Map<Long, JsonNode> questions = new HashMap<>();

    @Test
    void activityCountsStreakAndStudyTime() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long topic = createTopic(instructor, createModule(instructor, courseId, "M"), "T");
        List<Long> q = questions(instructor, topic, 1);
        long quiz = quiz(instructor, courseId, "PRACTICE", q);

        JsonNode empty = get_("/api/students/me/activity", student, 200);
        assertThat(empty.get("currentStreakDays").asInt()).isZero();
        assertThat(empty.get("last14Days")).hasSize(14);

        // three sessions: today, and moved back to 1 and 2 days ago -> 3-day streak
        for (int i = 0; i < 3; i++) {
            submit(student, quiz, Map.of(q.get(0), true));
        }
        long studentId = get_("/api/users/me", student, 200).get("id").asLong();
        var sessions = sessionRepository.findByStudentIdOrderByStartedAtDesc(studentId);
        for (int i = 1; i < 3; i++) {
            sessions.get(i).setStartedAt(Instant.now().minus(Duration.ofDays(i)));
            sessionRepository.save(sessions.get(i));
        }

        JsonNode activity = get_("/api/students/me/activity?tz=Asia/Kolkata", student, 200);
        assertThat(activity.get("timeZone").asText()).isEqualTo("Asia/Kolkata");
        assertThat(activity.get("currentStreakDays").asInt()).isEqualTo(3);
        assertThat(activity.get("longestStreakDays").asInt()).isEqualTo(3);
        assertThat(activity.get("studiedToday").asBoolean()).isTrue();
        assertThat(activity.get("minutesLast7Days").asInt()).isEqualTo(3);
        get_("/api/students/me/activity?tz=Mars/Base", student, 400);
    }

    @Test
    void instructorAnalyticsAndRecommendationEvaluation() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long module = createModule(instructor, courseId, "M");
        long a = createTopic(instructor, module, "A");
        long b = createTopic(instructor, module, "B");
        List<Long> qa = questions(instructor, a, 4);
        questions(instructor, b, 2);

        // recommendations are stored first (both topics unlocked, A ranked first) ...
        submit(student, quiz(instructor, courseId, "PRACTICE", List.of(qa.get(0))), Map.of(qa.get(0), true));
        get_("/api/students/me/recommendations?courseId=" + courseId, student, 200);
        Thread.sleep(5);

        // ... then the student struggles on A (0%): A was relevant, and it was recommended
        submit(student, quiz(instructor, courseId, "PRACTICE", qa.subList(0, 2)), Map.of(qa.get(0), false, qa.get(1), false));
        // adaptive quiz on A (all right), then a fixed quiz on A (half right)
        JsonNode adaptive = post_("/api/quizzes/adaptive/start", student,
                Map.of("courseId", courseId, "questionCount", 2, "topicIds", List.of(a)), 201);
        Map<Long, Boolean> all = new HashMap<>();
        adaptive.at("/attempt/questions").forEach(x -> all.put(x.get("questionId").asLong(), true));
        answer(student, adaptive.at("/attempt/attemptId").asLong(), all);
        submit(student, quiz(instructor, courseId, "ASSESSMENT", qa.subList(2, 4)), Map.of(qa.get(2), true, qa.get(3), false));

        JsonNode analytics = get_("/api/instructor/analytics?courseId=" + courseId + "&k=2", instructor, 200);
        assertThat(analytics.get("enrolledStudents").asInt()).isEqualTo(1);
        assertThat(analytics.get("submittedAttempts").asLong()).isEqualTo(4);
        JsonNode topicA = analytics.at("/topics/0");
        assertThat(topicA.get("title").asText()).isEqualTo("A");
        assertThat(topicA.get("answers").asLong()).isEqualTo(7);
        assertThat(topicA.get("correctAnswers").asLong()).isEqualTo(4);
        assertThat(topicA.get("accuracyPercentage").decimalValue()).isEqualByComparingTo("57.14");
        assertThat(topicA.get("studentsAttempted").asLong()).isEqualTo(1);
        assertThat(analytics.at("/topics/1/answers").asLong()).isZero();

        JsonNode eval = analytics.get("evaluation");
        assertThat(eval.get("studentsEvaluated").asInt()).isEqualTo(1);
        // later answers only touch A: A accuracy after the recommendations = 3/6 < 60% -> relevant = {A}
        assertThat(eval.get("precisionAtK").asDouble()).isEqualTo(0.5); // 1 hit in top 2
        assertThat(eval.get("recallAtK").asDouble()).isEqualTo(1.0);
        Map<String, JsonNode> paths = new HashMap<>();
        eval.get("paths").forEach(p -> paths.put(p.get("path").asText(), p));
        // A sequence: FIXED 1.0 -> FIXED 0.0 -> ADAPTIVE 1.0 -> FIXED 0.5
        assertThat(paths.get("FIXED").get("followUpPairs").asInt()).isEqualTo(2);
        assertThat(paths.get("FIXED").get("meanGain").asDouble()).isEqualTo(0.0);   // (-1.0 + 1.0) / 2
        assertThat(paths.get("ADAPTIVE").get("followUpPairs").asInt()).isEqualTo(1);
        assertThat(paths.get("ADAPTIVE").get("meanGain").asDouble()).isEqualTo(-0.5);
        assertThat(eval.get("sufficientData").asBoolean()).isFalse();

        get_("/api/instructor/analytics?courseId=" + courseId, registerAs("INSTRUCTOR"), 403);
        get_("/api/instructor/analytics?courseId=" + courseId, student, 403);
    }

    @Test
    void adminStatsAreAdminOnly() throws Exception {
        String admin = post_("/api/auth/login", null,
                Map.of("email", "admin@test.local", "password", "AdminPass123!"), 200).get("token").asText();
        registerAs("STUDENT");
        JsonNode stats = get_("/api/admin/stats", admin, 200);
        assertThat(stats.get("totalUsers").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(stats.at("/usersByRole/ADMIN").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.at("/usersByRole/STUDENT").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.has("aiInteractionsLast7Days")).isTrue();
        get_("/api/admin/stats", registerAs("INSTRUCTOR"), 403);
    }

    // ------------------------------------------------------------------ helpers

    private List<Long> questions(String instructor, long topicId, int n) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            JsonNode q = createQuestion(instructor, topicId, "T" + topicId + "Q" + i, 0);
            questions.put(q.get("id").asLong(), q);
            ids.add(q.get("id").asLong());
        }
        return ids;
    }

    private long quiz(String instructor, long courseId, String type, List<Long> ids) throws Exception {
        return post_("/api/courses/" + courseId + "/quizzes", instructor, Map.of("title", type, "type", type,
                "published", true, "questionIds", ids), 201).get("id").asLong();
    }

    private void submit(String student, long quizId, Map<Long, Boolean> answers) throws Exception {
        answer(student, post_("/api/quizzes/" + quizId + "/attempts", student, null, 201).get("attemptId").asLong(), answers);
    }

    private void answer(String student, long attemptId, Map<Long, Boolean> answers) throws Exception {
        List<Map<String, Object>> body = new ArrayList<>();
        answers.forEach((qid, correct) -> {
            for (JsonNode o : questions.get(qid).get("options")) {
                if (o.get("correct").asBoolean() == correct) {
                    body.add(Map.of("questionId", qid, "selectedOptionId", o.get("id").asLong()));
                    break;
                }
            }
        });
        post_("/api/attempts/" + attemptId + "/submit", student, Map.of("answers", body), 200);
    }
}
