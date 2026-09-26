package com.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.lms.dto.question.OptionRequest;
import com.lms.dto.question.QuestionRequest;
import com.lms.service.QuestionService;

class QuizIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private QuestionService questionService;

    @Test
    void instructorQuestionsAreApprovedAndQuizGradingIsServerSide() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long topic = createTopic(instructor, createModule(instructor, courseId, "M"), "Arrays");

        JsonNode q1 = createQuestion(instructor, topic, "Q1", 2);
        JsonNode q2 = createQuestion(instructor, topic, "Q2", 0);
        assertThat(q1.get("status").asText()).isEqualTo("APPROVED");
        assertThat(q1.get("createdByType").asText()).isEqualTo("INSTRUCTOR");

        long quizId = post_("/api/courses/" + courseId + "/quizzes", instructor, Map.of(
                "title", "Arrays quiz", "passingScore", 50, "published", true,
                "questionIds", List.of(q1.get("id").asLong(), q2.get("id").asLong())), 201).get("id").asLong();

        // student view: no correctness or explanations anywhere
        JsonNode start = post_("/api/quizzes/" + quizId + "/attempts", student, null, 201);
        String raw = start.toString();
        assertThat(raw).doesNotContain("\"correct\"").doesNotContain("explanation");
        assertThat(get_("/api/quizzes/" + quizId, student, 200).has("questions")).isFalse();
        long attemptId = start.get("attemptId").asLong();

        // starting again resumes the same open attempt
        assertThat(post_("/api/quizzes/" + quizId + "/attempts", student, null, 201).get("attemptId").asLong())
                .isEqualTo(attemptId);

        // answer Q1 wrong while claiming "correct": true, answer Q2 right
        List<Map<String, Object>> answers = List.of(
                Map.of("questionId", q1.get("id").asLong(), "selectedOptionId", optionId(q1, 0), "correct", true),
                Map.of("questionId", q2.get("id").asLong(), "selectedOptionId", optionId(q2, 0)));
        JsonNode result = post_("/api/attempts/" + attemptId + "/submit", student, Map.of(
                "answers", answers, "score", 100), 200);

        assertThat(result.get("score").decimalValue()).isEqualByComparingTo("1");
        assertThat(result.get("maxScore").decimalValue()).isEqualByComparingTo("2");
        assertThat(result.get("percentage").decimalValue()).isEqualByComparingTo("50");
        assertThat(result.get("passed").asBoolean()).isTrue();
        assertThat(result.at("/results/0/correct").asBoolean()).isFalse();
        assertThat(result.at("/results/0/correctOptionId").asLong()).isEqualTo(optionId(q1, 2));
        assertThat(result.at("/results/1/correct").asBoolean()).isTrue();

        post_("/api/attempts/" + attemptId + "/submit", student, Map.of("answers", List.of()), 409);
        assertThat(get_("/api/attempts/" + attemptId, student, 200).get("percentage").decimalValue())
                .isEqualByComparingTo("50");
        assertThat(get_("/api/attempts/me", student, 200)).hasSize(1);
    }

    @Test
    void submitRejectsForeignOptionsAndOtherStudents() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        String intruder = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long topic = createTopic(instructor, createModule(instructor, courseId, "M"), "T");
        JsonNode q1 = createQuestion(instructor, topic, "Q1", 1);
        JsonNode q2 = createQuestion(instructor, topic, "Q2", 1);
        long quizId = post_("/api/courses/" + courseId + "/quizzes", instructor, Map.of(
                "title", "Quiz", "published", true, "questionIds", List.of(q1.get("id").asLong())), 201)
                .get("id").asLong();

        long attemptId = post_("/api/quizzes/" + quizId + "/attempts", student, null, 201).get("attemptId").asLong();

        // option belonging to a different question
        post_("/api/attempts/" + attemptId + "/submit", student, Map.of("answers", List.of(
                Map.of("questionId", q1.get("id").asLong(), "selectedOptionId", optionId(q2, 1)))), 400);
        // question that is not in the quiz
        post_("/api/attempts/" + attemptId + "/submit", student, Map.of("answers", List.of(
                Map.of("questionId", q2.get("id").asLong(), "selectedOptionId", optionId(q2, 1)))), 400);
        // someone else's attempt
        post_("/api/attempts/" + attemptId + "/submit", intruder, Map.of("answers", List.of()), 403);
        get_("/api/attempts/" + attemptId, intruder, 404);
        // instructors cannot take quizzes
        post_("/api/quizzes/" + quizId + "/attempts", instructor, null, 403);
    }

    @Test
    void aiQuestionReviewWorkflow() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        long courseId = createCourse(instructor, true);
        long topic = createTopic(instructor, createModule(instructor, courseId, "M"), "T");

        long generatedId = questionService.createGeneratedQuestion(topic, new QuestionRequest(
                "AI question", "AI explanation", null,
                List.of(new OptionRequest("yes", true), new OptionRequest("no", false)))).id();

        JsonNode pending = get_("/api/questions/pending", instructor, 200);
        assertThat(ids(pending)).contains(generatedId);

        // pending questions cannot be put in a quiz
        post_("/api/courses/" + courseId + "/quizzes", instructor, Map.of(
                "title", "Q", "questionIds", List.of(generatedId)), 400);

        String otherInstructor = registerAs("INSTRUCTOR");
        patch_("/api/questions/" + generatedId + "/approve", otherInstructor, 403);
        assertThat(ids(get_("/api/questions/pending", otherInstructor, 200))).doesNotContain(generatedId);

        JsonNode approved = patch_("/api/questions/" + generatedId + "/approve", instructor, 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("createdByType").asText()).isEqualTo("AI");
        assertThat(approved.get("reviewedAt").isNull()).isFalse();
        patch_("/api/questions/" + generatedId + "/approve", instructor, 409);

        post_("/api/courses/" + courseId + "/quizzes", instructor, Map.of(
                "title", "Q", "questionIds", List.of(generatedId)), 201);
    }

    @Test
    void questionValidation() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        long topic = createTopic(instructor, createModule(instructor, createCourse(instructor, false), "M"), "T");
        JsonNode error = post_("/api/topics/" + topic + "/questions", instructor, Map.of(
                "text", "Two correct?", "options", List.of(
                        Map.of("text", "a", "correct", true), Map.of("text", "b", "correct", true))), 400);
        assertThat(error.get("fieldErrors").has("exactlyOneCorrect")).isTrue();
        post_("/api/topics/" + topic + "/questions", instructor, Map.of(
                "text", "One option", "options", List.of(Map.of("text", "a", "correct", true))), 400);
    }

    @Test
    void diagnosticTakesUpToFiveApprovedQuestionsPerTopic() throws Exception {
        String instructor = registerAs("INSTRUCTOR");
        String student = registerAs("STUDENT");
        long courseId = createCourse(instructor, true);
        long module = createModule(instructor, courseId, "M");
        long topicA = createTopic(instructor, module, "A");
        long topicB = createTopic(instructor, module, "B");
        long topicC = createTopic(instructor, module, "C"); // no questions

        Map<Long, JsonNode> questions = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            JsonNode q = createQuestion(instructor, topicA, "A" + i, i % 4);
            questions.put(q.get("id").asLong(), q);
        }
        for (int i = 0; i < 3; i++) {
            JsonNode q = createQuestion(instructor, topicB, "B" + i, i % 4);
            questions.put(q.get("id").asLong(), q);
        }
        questionService.createGeneratedQuestion(topicB, new QuestionRequest("pending", null, null,
                List.of(new OptionRequest("x", true), new OptionRequest("y", false))));

        JsonNode start = post_("/api/courses/" + courseId + "/diagnostic", student, null, 201);
        assertThat(start.get("quizType").asText()).isEqualTo("DIAGNOSTIC");
        assertThat(start.toString()).doesNotContain("\"correct\"");

        Map<Long, Integer> perTopic = new HashMap<>();
        List<Map<String, Object>> answers = new ArrayList<>();
        for (JsonNode q : start.get("questions")) {
            perTopic.merge(q.get("topicId").asLong(), 1, Integer::sum);
            long qid = q.get("questionId").asLong();
            assertThat(questions).containsKey(qid); // only approved questions
            answers.add(Map.of("questionId", qid, "selectedOptionId", correctOptionId(questions.get(qid))));
        }
        assertThat(perTopic).containsEntry(topicA, 5).containsEntry(topicB, 3).doesNotContainKey(topicC);

        JsonNode result = post_("/api/attempts/" + start.get("attemptId").asLong() + "/submit", student,
                Map.of("answers", answers), 200);
        assertThat(result.get("percentage").decimalValue()).isEqualByComparingTo("100");

        // personal diagnostic quizzes are not listed to other students or in the course quiz list
        assertThat(get_("/api/courses/" + courseId + "/quizzes", instructor, 200)).isEmpty();
        get_("/api/quizzes/" + start.get("quizId").asLong(), registerAs("STUDENT"), 404);

        // topic filter
        JsonNode onlyB = post_("/api/courses/" + courseId + "/diagnostic", student,
                Map.of("topicIds", List.of(topicB)), 201);
        assertThat(onlyB.get("questions")).hasSize(3);
        post_("/api/courses/" + courseId + "/diagnostic", student, Map.of("topicIds", List.of(topicC)), 400);
    }

    private static long optionId(JsonNode question, int index) {
        return question.get("options").get(index).get("id").asLong();
    }

    private static long correctOptionId(JsonNode question) {
        for (JsonNode o : question.get("options")) {
            if (o.get("correct").asBoolean()) {
                return o.get("id").asLong();
            }
        }
        throw new AssertionError("no correct option");
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }
}
