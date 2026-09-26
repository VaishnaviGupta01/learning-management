package com.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.lms.repository.StudySessionRepository;

/** Phases 6-8: progress tracking, knowledge model and recommendations. */
class LearningAnalyticsIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private FakeMlClient ml;

    @Autowired
    private StudySessionRepository sessionRepository;

    /** questionId -> question JSON (with options) for answering */
    private final Map<Long, JsonNode> questions = new HashMap<>();

    @AfterEach
    void restoreMl() {
        ml.down = false;
    }

    // ------------------------------------------------------------------ Phase 6

    @Test
    void progressIsUpdatedAfterEachQuizSubmission() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long module = createModule(instructor, courseId, "M");
        long topicA = createTopic(instructor, module, "A");
        long topicB = createTopic(instructor, module, "B");
        List<Long> a = questions(instructor, topicA, 4);
        questions(instructor, topicB, 2);
        long quiz = quiz(instructor, courseId, "PRACTICE", a.get(0), a.get(1));

        submit(student, quiz, Map.of(a.get(0), true, a.get(1), false));

        JsonNode course = progressFor(student, courseId);
        assertThat(course.get("completionPercentage").decimalValue()).isEqualByComparingTo("16.67"); // 1 of 6
        assertThat(course.get("totalTopics").asInt()).isEqualTo(2);
        assertThat(course.get("topicsCompleted").asInt()).isZero();
        assertThat(course.get("totalStudyMinutes").asInt()).isEqualTo(1);
        JsonNode topicAProgress = topic(course, topicA);
        assertThat(topicAProgress.get("approvedQuestions").asLong()).isEqualTo(4);
        assertThat(topicAProgress.get("completionPercentage").decimalValue()).isEqualByComparingTo("25");
        assertThat(topicAProgress.get("accuracyPercentage").decimalValue()).isEqualByComparingTo("50");
        assertThat(topic(course, topicB).get("accuracyPercentage").isNull()).isTrue();

        // retake: both right. Distinct mastered questions = 2 of 4; accuracy over history = 3 of 4
        submit(student, quiz, Map.of(a.get(0), true, a.get(1), true));
        course = progressFor(student, courseId);
        assertThat(course.get("completionPercentage").decimalValue()).isEqualByComparingTo("33.33");
        assertThat(topic(course, topicA).get("completionPercentage").decimalValue()).isEqualByComparingTo("50");
        assertThat(topic(course, topicA).get("accuracyPercentage").decimalValue()).isEqualByComparingTo("75");
        assertThat(course.get("totalStudyMinutes").asInt()).isEqualTo(2);

        // mastering all of B completes that topic
        long quizB = quiz(instructor, courseId, "PRACTICE", questionIdsFor(topicB).toArray(Long[]::new));
        submit(student, quizB, allCorrect(questionIdsFor(topicB)));
        assertThat(progressFor(student, courseId).get("topicsCompleted").asInt()).isEqualTo(1);

        long studentId = get_("/api/users/me", student, 200).get("id").asLong();
        assertThat(sessionRepository.findByStudentIdOrderByStartedAtDesc(studentId)).hasSize(3);
        get_("/api/students/me/progress", instructor, 403);
    }

    // ------------------------------------------------------------------ Phase 7

    @Test
    void knowledgeIsRecalculatedForTouchedTopics() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long module = createModule(instructor, courseId, "M");
        long topicA = createTopic(instructor, module, "A");
        long topicB = createTopic(instructor, module, "B");
        List<Long> a = questions(instructor, topicA, 5);
        questions(instructor, topicB, 2);

        // diagnostic: all of A right, all of B wrong
        JsonNode diag = post_("/api/courses/" + courseId + "/diagnostic", student, null, 201);
        Map<Long, Boolean> answers = new HashMap<>();
        diag.get("questions").forEach(q -> answers.put(q.get("questionId").asLong(),
                q.get("topicId").asLong() == topicA));
        post_("/api/attempts/" + diag.get("attemptId").asLong() + "/submit", student,
                Map.of("answers", answerList(answers)), 200);

        Map<Long, JsonNode> knowledge = knowledge(student, courseId);
        assertThat(knowledge.get(topicA).get("masteryScore").asDouble()).isEqualTo(1.0);
        assertThat(knowledge.get(topicA).get("classification").asText()).isEqualTo("STRONG");
        assertThat(knowledge.get(topicA).get("attemptsCount").asInt()).isEqualTo(5);
        assertThat(knowledge.get(topicB).get("masteryScore").asDouble()).isEqualTo(0.0);
        assertThat(knowledge.get(topicB).get("classification").asText()).isEqualTo("WEAK");

        // practice quiz on A, all wrong: (0.4 * 1.0 + 0.2 * 0.0) / 0.6 = 0.6667 -> MODERATE
        long practice = quiz(instructor, courseId, "PRACTICE", a.get(0), a.get(1));
        submit(student, practice, Map.of(a.get(0), false, a.get(1), false));
        knowledge = knowledge(student, courseId);
        assertThat(knowledge.get(topicA).get("masteryScore").asDouble()).isEqualTo(0.6667);
        assertThat(knowledge.get(topicA).get("classification").asText()).isEqualTo("MODERATE");
        assertThat(knowledge.get(topicA).get("correctCount").asInt()).isEqualTo(5);
        assertThat(knowledge.get(topicA).get("attemptsCount").asInt()).isEqualTo(7);

        // assessment (recent_quiz) on A, all right: (0.4 + 0.3 * 1.0 + 0.0) / 0.9 = 0.7778 -> MODERATE
        long assessment = quiz(instructor, courseId, "ASSESSMENT", a.get(2), a.get(3));
        submit(student, assessment, Map.of(a.get(2), true, a.get(3), true));
        assertThat(knowledge(student, courseId).get(topicA).get("masteryScore").asDouble()).isEqualTo(0.7778);
    }

    @Test
    void quizSubmissionSurvivesMlOutage() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long topic = createTopic(instructor, createModule(instructor, courseId, "M"), "T");
        List<Long> q = questions(instructor, topic, 2);
        long quiz = quiz(instructor, courseId, "PRACTICE", q.get(0), q.get(1));

        submit(student, quiz, Map.of(q.get(0), true, q.get(1), true));
        assertThat(knowledge(student, courseId).get(topic).get("masteryScore").asDouble()).isEqualTo(1.0);

        ml.down = true;
        JsonNode result = submit(student, quiz, Map.of(q.get(0), false, q.get(1), false));
        assertThat(result.get("percentage").decimalValue()).isEqualByComparingTo("0");

        JsonNode k = knowledge(student, courseId).get(topic);
        assertThat(k.get("masteryScore").asDouble()).isEqualTo(1.0); // previous score kept
        assertThat(k.get("attemptsCount").asInt()).isEqualTo(4);     // counts still updated
        assertThat(progressFor(student, courseId).get("totalStudyMinutes").asInt()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ Phase 8

    @Test
    void recommendationsRespectPrerequisitesAndRanking() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long module = createModule(instructor, courseId, "DSA");
        long recursion = createTopic(instructor, module, "Recursion");
        long trees = createTopic(instructor, module, "Trees");
        long graphs = createTopic(instructor, module, "Graphs");
        long arrays = createTopic(instructor, module, "Arrays");
        long searching = createTopic(instructor, module, "Searching");
        put_("/api/topics/" + trees, instructor, Map.of("title", "Trees", "importance", 0.9), 200);
        prereq(instructor, trees, recursion);
        prereq(instructor, graphs, trees);
        prereq(instructor, searching, arrays);
        for (long t : List.of(recursion, trees, graphs, arrays, searching)) {
            questions(instructor, t, 2);
        }

        // diagnostic on Recursion (all right) and Arrays (all wrong)
        JsonNode diag = post_("/api/courses/" + courseId + "/diagnostic", student,
                Map.of("topicIds", List.of(recursion, arrays)), 201);
        Map<Long, Boolean> answers = new HashMap<>();
        diag.get("questions").forEach(q -> answers.put(q.get("questionId").asLong(),
                q.get("topicId").asLong() == recursion));
        post_("/api/attempts/" + diag.get("attemptId").asLong() + "/submit", student,
                Map.of("answers", answerList(answers)), 200);

        JsonNode recs = get_("/api/students/me/recommendations?courseId=" + courseId, student, 200);
        assertThat(recs.get("stale").asBoolean()).isFalse();

        // unlocked: Recursion (no prereqs), Trees (Recursion complete), Arrays (no prereqs)
        // priorities: Trees 0.5*1 + 0.3*0.9 = 0.77, Arrays 0.5*1 + 0.15 = 0.65, Recursion 0 + 0.15 = 0.15
        assertThat(titles(recs.get("recommendations"))).containsExactly("Trees", "Arrays", "Recursion");
        JsonNode top = recs.at("/recommendations/0");
        assertThat(top.get("rank").asInt()).isEqualTo(1);
        assertThat(top.get("priority").asDouble()).isEqualTo(0.77);
        assertThat(top.get("classification").asText()).isEqualTo("NOT_STARTED");
        assertThat(recs.at("/recommendations/1/classification").asText()).isEqualTo("WEAK");

        // blocked: Graphs (needs Trees) and Searching (needs Arrays) are not ranked
        Map<String, JsonNode> blocked = new HashMap<>();
        recs.get("blocked").forEach(b -> blocked.put(b.get("topicTitle").asText(), b));
        assertThat(blocked).containsOnlyKeys("Graphs", "Searching");
        assertThat(blocked.get("Graphs").get("message").asText()).isEqualTo("Complete Trees first");
        assertThat(blocked.get("Searching").get("message").asText()).isEqualTo("Complete Arrays first");

        // same data without courseId (courses with progress) gives the same ranking
        assertThat(titles(get_("/api/students/me/recommendations", student, 200).get("recommendations")))
                .containsExactly("Trees", "Arrays", "Recursion");

        // ml-service outage: last stored ranking is returned, flagged stale; gating still computed
        ml.down = true;
        JsonNode stale = get_("/api/students/me/recommendations?courseId=" + courseId, student, 200);
        assertThat(stale.get("stale").asBoolean()).isTrue();
        assertThat(titles(stale.get("recommendations"))).containsExactly("Trees", "Arrays", "Recursion");
        assertThat(stale.get("blocked")).hasSize(2);
    }

    @Test
    void blockedTopicPointsAtTheFirstWorkablePrerequisite() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long module = createModule(instructor, courseId, "DSA");
        long recursion = createTopic(instructor, module, "Recursion");
        long trees = createTopic(instructor, module, "Trees");
        long graphs = createTopic(instructor, module, "Graphs");
        prereq(instructor, trees, recursion);
        prereq(instructor, graphs, trees);

        JsonNode recs = get_("/api/students/me/recommendations?courseId=" + courseId, student, 200);
        assertThat(titles(recs.get("recommendations"))).containsExactly("Recursion");
        Map<String, String> messages = new HashMap<>();
        recs.get("blocked").forEach(b -> messages.put(b.get("topicTitle").asText(), b.get("message").asText()));
        // Graphs is two steps away, but the actionable step is Recursion, not Trees
        assertThat(messages).containsEntry("Trees", "Complete Recursion first")
                .containsEntry("Graphs", "Complete Recursion first");
    }

    // ------------------------------------------------------------------ helpers

    private List<Long> questions(String instructor, long topicId, int count) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JsonNode q = createQuestion(instructor, topicId, "T" + topicId + "Q" + i, i % 4);
            questions.put(q.get("id").asLong(), q);
            ids.add(q.get("id").asLong());
        }
        return ids;
    }

    private List<Long> questionIdsFor(long topicId) {
        return questions.entrySet().stream()
                .filter(e -> e.getValue().get("topicId").asLong() == topicId)
                .map(Map.Entry::getKey).sorted().toList();
    }

    private long quiz(String instructor, long courseId, String type, Long... questionIds) throws Exception {
        return post_("/api/courses/" + courseId + "/quizzes", instructor, Map.of(
                "title", type + " quiz", "type", type, "published", true, "questionIds", List.of(questionIds)), 201)
                .get("id").asLong();
    }

    private JsonNode submit(String student, long quizId, Map<Long, Boolean> correctByQuestion) throws Exception {
        long attemptId = post_("/api/quizzes/" + quizId + "/attempts", student, null, 201).get("attemptId").asLong();
        return post_("/api/attempts/" + attemptId + "/submit", student,
                Map.of("answers", answerList(correctByQuestion)), 200);
    }

    private List<Map<String, Object>> answerList(Map<Long, Boolean> correctByQuestion) {
        List<Map<String, Object>> answers = new ArrayList<>();
        correctByQuestion.forEach((qid, correct) -> {
            for (JsonNode o : questions.get(qid).get("options")) {
                if (o.get("correct").asBoolean() == correct) {
                    answers.add(Map.of("questionId", qid, "selectedOptionId", o.get("id").asLong()));
                    return;
                }
            }
        });
        return answers;
    }

    private static Map<Long, Boolean> allCorrect(List<Long> ids) {
        Map<Long, Boolean> m = new HashMap<>();
        ids.forEach(id -> m.put(id, true));
        return m;
    }

    private void prereq(String instructor, long topic, long prerequisite) throws Exception {
        post_("/api/topics/" + topic + "/prerequisites", instructor, Map.of("prerequisiteTopicId", prerequisite), 201);
    }

    private JsonNode progressFor(String student, long courseId) throws Exception {
        for (JsonNode c : get_("/api/students/me/progress", student, 200)) {
            if (c.get("courseId").asLong() == courseId) {
                return c;
            }
        }
        throw new AssertionError("no progress for course " + courseId);
    }

    private static JsonNode topic(JsonNode courseProgress, long topicId) {
        for (JsonNode t : courseProgress.get("topics")) {
            if (t.get("topicId").asLong() == topicId) {
                return t;
            }
        }
        throw new AssertionError("no topic " + topicId);
    }

    private Map<Long, JsonNode> knowledge(String student, long courseId) throws Exception {
        Map<Long, JsonNode> byTopic = new HashMap<>();
        get_("/api/students/me/topics/knowledge?courseId=" + courseId, student, 200)
                .forEach(k -> byTopic.put(k.get("topicId").asLong(), k));
        return byTopic;
    }

    private static List<String> titles(JsonNode items) {
        List<String> titles = new ArrayList<>();
        items.forEach(i -> titles.add(i.get("topicTitle").asText()));
        return titles;
    }
}
