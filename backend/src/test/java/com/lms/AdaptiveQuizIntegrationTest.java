package com.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.lms.repository.RevisionScheduleRepository;

/** Phase 9: adaptive difficulty, knowledge-weighted question allocation, automatic revision scheduling. */
class AdaptiveQuizIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RevisionScheduleRepository revisionRepository;

    /** questionId -> question JSON (with options and difficulty) */
    private final Map<Long, JsonNode> questions = new HashMap<>();

    @Test
    void difficultyMovesWithRecentAccuracyAndWeakTopicsGetMoreQuestions() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long module = createModule(instructor, courseId, "M");
        long strong = createTopic(instructor, module, "Strong topic");
        long weak = createTopic(instructor, module, "Weak topic");
        long middling = createTopic(instructor, module, "Middling topic");
        Map<String, List<Long>> s = tiers(instructor, strong);
        Map<String, List<Long>> w = tiers(instructor, weak);
        Map<String, List<Long>> m = tiers(instructor, middling);

        // history: strong 3/3 right at MEDIUM, weak 0/3 at MEDIUM, middling 2/3 at MEDIUM
        practice(instructor, student, courseId, Map.of(s.get("MEDIUM").get(0), true, s.get("MEDIUM").get(1), true,
                s.get("MEDIUM").get(2), true));
        practice(instructor, student, courseId, Map.of(w.get("MEDIUM").get(0), false, w.get("MEDIUM").get(1), false,
                w.get("MEDIUM").get(2), false));
        practice(instructor, student, courseId, Map.of(m.get("MEDIUM").get(0), true, m.get("MEDIUM").get(1), true,
                m.get("MEDIUM").get(2), false));

        JsonNode res = post_("/api/quizzes/adaptive/start", student,
                Map.of("courseId", courseId, "questionCount", 9), 201);
        Map<Long, JsonNode> plan = new HashMap<>();
        res.get("plan").forEach(p -> plan.put(p.get("topicId").asLong(), p));

        // >80% -> up, <50% -> down (+ revision), 50-80% -> same
        assertPlan(plan.get(strong), "STRONG", 1.0, "UP", "HARD", false);
        assertPlan(plan.get(weak), "WEAK", 0.0, "DOWN", "EASY", true);
        assertPlan(plan.get(middling), "MODERATE", 2 / 3.0, "SAME", "MEDIUM", false);

        // weights WEAK 4, MODERATE 2, STRONG 1 over 9 slots (Sainte-Lague) -> 5 / 3 / 1
        assertThat(plan.get(weak).get("questionsAllocated").asInt()).isEqualTo(5);
        assertThat(plan.get(middling).get("questionsAllocated").asInt()).isEqualTo(3);
        assertThat(plan.get(strong).get("questionsAllocated").asInt()).isEqualTo(1);

        JsonNode attempt = res.get("attempt");
        assertThat(attempt.get("quizType").asText()).isEqualTo("ADAPTIVE");
        assertThat(attempt.toString()).doesNotContain("\"correct\"");
        Map<Long, List<String>> difficultyByTopic = new HashMap<>();
        attempt.get("questions").forEach(q -> difficultyByTopic
                .computeIfAbsent(q.get("topicId").asLong(), k -> new ArrayList<>()).add(q.get("difficulty").asText()));
        assertThat(difficultyByTopic.get(strong)).containsExactly("HARD");
        // 3 EASY exist; the other 2 come from the nearest tier
        assertThat(difficultyByTopic.get(weak)).hasSize(5).containsOnly("EASY", "MEDIUM")
                .filteredOn("EASY"::equals).hasSize(3);
        assertThat(difficultyByTopic.get(middling)).containsOnly("MEDIUM").hasSize(3);

        // the weak topic went on the revision schedule, first review tomorrow
        long studentId = get_("/api/users/me", student, 200).get("id").asLong();
        var schedule = revisionRepository.findByStudentIdAndTopicId(studentId, weak).orElseThrow();
        assertThat(schedule.isActive()).isTrue();
        assertThat(schedule.getStage().name()).isEqualTo("DAY_1");
        assertThat(revisionRepository.findByStudentIdAndTopicId(studentId, strong)).isEmpty();

        // a second adaptive start does not re-create the existing schedule
        JsonNode again = post_("/api/quizzes/adaptive/start", student, Map.of("courseId", courseId), 201);
        again.get("plan").forEach(p -> assertThat(p.get("revisionScheduled").asBoolean()).isFalse());
    }

    @Test
    void noHistoryStartsAtTopicDifficultyAndFiltersTopics() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long module = createModule(instructor, courseId, "M");
        long hardTopic = post_("/api/modules/" + module + "/topics", instructor,
                Map.of("title", "Hard topic", "difficulty", "HARD"), 201).get("id").asLong();
        long other = createTopic(instructor, module, "Other");
        tiers(instructor, hardTopic);
        tiers(instructor, other);

        JsonNode res = post_("/api/quizzes/adaptive/start", student,
                Map.of("courseId", courseId, "questionCount", 2, "topicIds", List.of(hardTopic)), 201);
        JsonNode p = res.at("/plan/0");
        assertThat(res.get("plan")).hasSize(1);
        assertThat(p.get("adjustment").asText()).isEqualTo("BASELINE");
        assertThat(p.get("recentAccuracy").isNull()).isTrue();
        assertThat(p.get("targetDifficulty").asText()).isEqualTo("HARD");
        res.at("/attempt/questions").forEach(q -> assertThat(q.get("difficulty").asText()).isEqualTo("HARD"));
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        createTopic(instructor, createModule(instructor, courseId, "M"), "Empty topic");

        post_("/api/quizzes/adaptive/start", student, Map.of("courseId", courseId), 400);          // no questions
        post_("/api/quizzes/adaptive/start", student, Map.of("courseId", courseId, "questionCount", 0), 400);
        post_("/api/quizzes/adaptive/start", student, Map.of(), 400);                               // courseId missing
        post_("/api/quizzes/adaptive/start", instructor, Map.of("courseId", courseId), 403);
        post_("/api/quizzes/adaptive/start", student, Map.of("courseId", createCourse(instructor, false)), 404);
    }

    // ------------------------------------------------------------------ helpers

    private void assertPlan(JsonNode p, String band, double accuracy, String adjustment, String target, boolean revision) {
        assertThat(p.get("classification").asText()).isEqualTo(band);
        assertThat(p.get("recentAccuracy").asDouble()).isCloseTo(accuracy, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(p.get("currentDifficulty").asText()).isEqualTo("MEDIUM");
        assertThat(p.get("adjustment").asText()).isEqualTo(adjustment);
        assertThat(p.get("targetDifficulty").asText()).isEqualTo(target);
        assertThat(p.get("revisionScheduled").asBoolean()).isEqualTo(revision);
    }

    /** Three approved questions per difficulty tier. */
    private Map<String, List<Long>> tiers(String instructor, long topicId) throws Exception {
        Map<String, List<Long>> ids = new HashMap<>();
        for (String difficulty : List.of("EASY", "MEDIUM", "HARD")) {
            for (int i = 0; i < 3; i++) {
                JsonNode q = post_("/api/topics/" + topicId + "/questions", instructor, Map.of(
                        "text", difficulty + " q" + i, "difficulty", difficulty, "options", List.of(
                                Map.of("text", "right", "correct", true), Map.of("text", "wrong", "correct", false))),
                        201);
                questions.put(q.get("id").asLong(), q);
                ids.computeIfAbsent(difficulty, k -> new ArrayList<>()).add(q.get("id").asLong());
            }
        }
        return ids;
    }

    private void practice(String instructor, String student, long courseId, Map<Long, Boolean> answers) throws Exception {
        long quizId = post_("/api/courses/" + courseId + "/quizzes", instructor, Map.of("title", "Practice",
                "type", "PRACTICE", "published", true, "questionIds", List.copyOf(answers.keySet())), 201)
                .get("id").asLong();
        long attemptId = post_("/api/quizzes/" + quizId + "/attempts", student, null, 201).get("attemptId").asLong();
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
