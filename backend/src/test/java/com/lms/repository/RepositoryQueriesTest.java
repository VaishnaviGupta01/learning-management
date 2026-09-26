package com.lms.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.lms.entity.Course;
import com.lms.entity.CourseModule;
import com.lms.entity.Question;
import com.lms.entity.QuestionAttempt;
import com.lms.entity.Quiz;
import com.lms.entity.QuizAttempt;
import com.lms.entity.Role;
import com.lms.entity.Topic;
import com.lms.entity.TopicPrerequisite;
import com.lms.entity.User;
import com.lms.entity.enums.QuestionStatus;
import com.lms.entity.enums.RoleName;

/** JPA slice: the hand-written JPQL aggregations the progress, analytics and graph features depend on. */
@DataJpaTest
class RepositoryQueriesTest {

    @Autowired TestEntityManager em;
    @Autowired QuestionAttemptRepository questionAttempts;
    @Autowired QuestionRepository questions;
    @Autowired TopicRepository topics;
    @Autowired TopicPrerequisiteRepository prerequisites;

    private User student;
    private User other;
    private Course course;
    private Topic arrays;
    private Topic trees;
    private Quiz quiz;

    @BeforeEach
    void setUp() {
        Role studentRole = persistRole(RoleName.STUDENT);
        Role instructorRole = persistRole(RoleName.INSTRUCTOR);
        User instructor = user("inst@x.io", instructorRole);
        student = user("s1@x.io", studentRole);
        other = user("s2@x.io", studentRole);

        course = new Course();
        course.setCode("DSA");
        course.setTitle("DSA");
        course.setInstructor(instructor);
        em.persist(course);
        CourseModule second = module(course, "Second", 1);
        CourseModule first = module(course, "First", 0);
        trees = topic(second, "Trees", 0);
        arrays = topic(first, "Arrays", 0);

        quiz = new Quiz();
        quiz.setCourse(course);
        quiz.setTitle("Q");
        em.persist(quiz);
    }

    @Test
    void topicsAreOrderedByModuleThenTopicOrder() {
        assertThat(topics.findByModuleCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(course.getId()))
                .extracting(Topic::getTitle).containsExactly("Arrays", "Trees");
    }

    @Test
    void distinctCorrectCountsOnlyApprovedQuestionsOnce() {
        Question a1 = question(arrays, QuestionStatus.APPROVED);
        Question a2 = question(arrays, QuestionStatus.APPROVED);
        Question pending = question(arrays, QuestionStatus.PENDING_REVIEW);
        QuizAttempt first = attempt(student);
        QuizAttempt second = attempt(student);
        answer(first, a1, true);
        answer(second, a1, true);      // same question right twice -> counted once
        answer(first, a2, false);
        answer(first, pending, true);  // not approved -> not counted
        answer(attempt(other), a2, true); // other student

        Map<Long, Long> mastered = counts(questionAttempts.countDistinctCorrectApprovedByTopic(student.getId(), course.getId()));
        assertThat(mastered).containsExactly(Map.entry(arrays.getId(), 1L));
        assertThat(counts(questions.countByCourseGroupedByTopic(course.getId(), QuestionStatus.APPROVED)))
                .containsEntry(arrays.getId(), 2L);
    }

    @Test
    void accuracyAggregatesPerTopicForOneStudentAndForTheCourse() {
        Question a = question(arrays, QuestionStatus.APPROVED);
        Question t = question(trees, QuestionStatus.APPROVED);
        QuizAttempt mine = attempt(student);
        answer(mine, a, true);
        answer(mine, a, false);
        answer(mine, t, false);
        answer(attempt(other), a, true);

        Map<Long, long[]> perStudent = accuracy(questionAttempts.accuracyByTopic(student.getId(), course.getId()));
        assertThat(perStudent.get(arrays.getId())).containsExactly(2, 1);
        assertThat(perStudent.get(trees.getId())).containsExactly(1, 0);

        Map<Long, long[]> course = accuracy(questionAttempts.courseAccuracyByTopic(this.course.getId()));
        assertThat(course.get(arrays.getId())).containsExactly(3, 2);
        assertThat(counts(questionAttempts.studentsByTopic(this.course.getId()))).containsEntry(arrays.getId(), 2L);
    }

    @Test
    void submittedAnswersForTopicsAreNewestFirst() {
        Question a = question(arrays, QuestionStatus.APPROVED);
        QuizAttempt older = attempt(student);
        older.setSubmittedAt(Instant.parse("2026-01-01T00:00:00Z"));
        QuizAttempt newer = attempt(student);
        newer.setSubmittedAt(Instant.parse("2026-02-01T00:00:00Z"));
        QuizAttempt unsubmitted = attempt(student);
        unsubmitted.setSubmittedAt(null);
        answer(older, a, false);
        answer(newer, a, true);
        answer(unsubmitted, a, true);

        List<QuestionAttempt> result = questionAttempts.findSubmittedByStudentAndTopics(student.getId(), List.of(arrays.getId()));
        assertThat(result).extracting(QuestionAttempt::isCorrect).containsExactly(true, false);
    }

    @Test
    void prerequisitesAreFoundByCourse() {
        TopicPrerequisite edge = new TopicPrerequisite();
        edge.setTopic(trees);
        edge.setPrerequisiteTopic(arrays);
        em.persist(edge);
        em.flush();

        assertThat(prerequisites.findByTopicModuleCourseId(course.getId())).hasSize(1);
        prerequisites.deleteByTopicIdOrPrerequisiteTopicId(arrays.getId(), arrays.getId());
        assertThat(prerequisites.findByTopicModuleCourseId(course.getId())).isEmpty();
    }

    // ------------------------------------------------------------------ builders

    private Role persistRole(RoleName name) {
        Role r = new Role();
        r.setName(name);
        return em.persist(r);
    }

    private User user(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("h");
        u.setFirstName("F");
        u.setLastName("L");
        u.setRole(role);
        return em.persist(u);
    }

    private CourseModule module(Course c, String title, int order) {
        CourseModule m = new CourseModule();
        m.setCourse(c);
        m.setTitle(title);
        m.setOrderIndex(order);
        return em.persist(m);
    }

    private Topic topic(CourseModule m, String title, int order) {
        Topic t = new Topic();
        t.setModule(m);
        t.setTitle(title);
        t.setOrderIndex(order);
        return em.persist(t);
    }

    private Question question(Topic topic, QuestionStatus status) {
        Question q = new Question();
        q.setTopic(topic);
        q.setText("q");
        q.setStatus(status);
        return em.persist(q);
    }

    private QuizAttempt attempt(User who) {
        QuizAttempt a = new QuizAttempt();
        a.setQuiz(quiz);
        a.setStudent(who);
        a.setStartedAt(Instant.now());
        a.setSubmittedAt(Instant.now());
        return em.persist(a);
    }

    private void answer(QuizAttempt attempt, Question q, boolean correct) {
        QuestionAttempt qa = new QuestionAttempt();
        qa.setQuizAttempt(attempt);
        qa.setQuestion(q);
        qa.setCorrect(correct);
        em.persist(qa);
        em.flush();
    }

    private static Map<Long, Long> counts(List<TopicStatsView.Count> rows) {
        return rows.stream().collect(Collectors.toMap(TopicStatsView.Count::getTopicId, TopicStatsView.Count::getTotal));
    }

    private static Map<Long, long[]> accuracy(List<TopicStatsView.Accuracy> rows) {
        return rows.stream().collect(Collectors.toMap(TopicStatsView.Accuracy::getTopicId,
                r -> new long[]{r.getTotal(), r.getCorrect()}));
    }
}
