package com.lms.seed;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.dto.course.CourseRequest;
import com.lms.dto.course.ModuleRequest;
import com.lms.dto.course.TopicRequest;
import com.lms.dto.question.OptionRequest;
import com.lms.dto.question.QuestionRequest;
import com.lms.dto.question.QuestionResponse;
import com.lms.dto.quiz.AdaptiveQuizRequest;
import com.lms.dto.quiz.AdaptiveQuizResponse;
import com.lms.dto.quiz.AttemptStartResponse;
import com.lms.dto.quiz.DiagnosticRequest;
import com.lms.dto.quiz.QuizRequest;
import com.lms.dto.quiz.SubmitAttemptRequest;
import com.lms.entity.User;
import com.lms.entity.enums.Difficulty;
import com.lms.entity.enums.QuizType;
import com.lms.entity.enums.RoleName;
import com.lms.ml.MlClient;
import com.lms.ml.MlServiceException;
import com.lms.repository.UserRepository;
import com.lms.security.UserPrincipal;
import com.lms.service.AdaptiveQuizService;
import com.lms.service.AuthService;
import com.lms.service.CourseService;
import com.lms.service.DiagnosticService;
import com.lms.service.QuestionService;
import com.lms.service.QuizService;
import com.lms.service.RecommendationService;
import com.lms.service.TopicService;

/**
 * Optional demo dataset (SEED_DEMO_DATA=true): 2 instructors, 15 students, the 3 courses / 108 questions in
 * {@code demo/courses.json}, and three weeks of simulated study.
 *
 * <p>Everything goes through the real services, so progress, knowledge, revision schedules and recommendations
 * are computed exactly as for real users. Student answers are <b>simulated</b>: each answer is correct with a
 * probability from the student's ability on the topic and the question's difficulty, and ability grows by the
 * same small amount after every answer regardless of quiz type - the demo does not favour adaptive over fixed
 * quizzes. Afterwards timestamps are moved back into the past (the services always use "now").
 *
 * <p>Runs once: it is skipped when the demo instructor already exists. Deterministic (fixed random seed).
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    static final String PASSWORD = "Demo12345!";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final int STUDENTS = 15;

    private final AuthService authService;
    private final UserRepository userRepository;
    private final CourseService courseService;
    private final TopicService topicService;
    private final QuestionService questionService;
    private final QuizService quizService;
    private final DiagnosticService diagnosticService;
    private final AdaptiveQuizService adaptiveQuizService;
    private final RecommendationService recommendationService;
    private final MlClient mlClient;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private final Random random = new Random(42);
    /** questionId -> answer key */
    private final Map<Long, AnswerKey> answers = new HashMap<>();

    public DemoDataSeeder(AuthService authService, UserRepository userRepository, CourseService courseService,
                          TopicService topicService, QuestionService questionService, QuizService quizService,
                          DiagnosticService diagnosticService, AdaptiveQuizService adaptiveQuizService,
                          RecommendationService recommendationService, MlClient mlClient, JdbcTemplate jdbc,
                          ObjectMapper objectMapper) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.topicService = topicService;
        this.questionService = questionService;
        this.quizService = quizService;
        this.diagnosticService = diagnosticService;
        this.adaptiveQuizService = adaptiveQuizService;
        this.recommendationService = recommendationService;
        this.mlClient = mlClient;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (userRepository.existsByEmailIgnoreCase("instructor1@demo.lms")) {
            log.info("DemoDataSeeder: demo data already present, skipping");
            return;
        }
        long started = System.currentTimeMillis();
        waitForMlService();

        Map<String, UserPrincipal> instructors = Map.of(
                "instructor1", user("instructor1@demo.lms", "Irene", "Adler", RoleName.INSTRUCTOR),
                "instructor2", user("instructor2@demo.lms", "Tom", "Baker", RoleName.INSTRUCTOR));
        List<DemoCourseRef> courses = new ArrayList<>();
        for (DemoCourse c : loadCourses()) {
            courses.add(createCourse(c, instructors.get(c.instructor())));
        }

        String[][] names = {{"Aarav", "Shah"}, {"Maya", "Chen"}, {"Liam", "Okafor"}, {"Sofia", "Rossi"},
                {"Noah", "Kim"}, {"Zara", "Ali"}, {"Ethan", "Brown"}, {"Priya", "Nair"}, {"Lucas", "Silva"},
                {"Amara", "Diallo"}, {"Jonas", "Weber"}, {"Hana", "Sato"}, {"Omar", "Haddad"}, {"Chloe", "Martin"},
                {"Ravi", "Iyer"}};
        for (int i = 0; i < STUDENTS; i++) {
            UserPrincipal student = user(String.format("student%02d@demo.lms", i + 1), names[i][0], names[i][1],
                    RoleName.STUDENT);
            double ability = 0.35 + 0.55 * random.nextDouble();
            for (int c = 0; c < courses.size(); c++) {
                if (c == 2 && i % 3 != 0) {
                    continue; // everyone takes DSA and Python; every third student also takes Databases
                }
                simulateStudent(student, courses.get(c), ability, i % 2 == 0);
            }
        }
        log.info("DemoDataSeeder: created {} courses, {} questions and {} students in {} s", courses.size(),
                answers.size(), STUDENTS, (System.currentTimeMillis() - started) / 1000);
    }

    // ------------------------------------------------------------------ content

    private DemoCourseRef createCourse(DemoCourse c, UserPrincipal instructor) {
        long courseId = courseService.createCourse(new CourseRequest(c.code(), c.title(), c.description(), true),
                instructor).id();
        Map<String, Long> topicIds = new LinkedHashMap<>();
        Map<Long, List<Long>> questionsByTopic = new LinkedHashMap<>();
        for (DemoModule m : c.modules()) {
            long moduleId = courseService.createModule(courseId, new ModuleRequest(m.title(), null, null), instructor).id();
            for (DemoTopic t : m.topics()) {
                long topicId = topicService.createTopic(moduleId, new TopicRequest(t.title(), null, null,
                        t.difficulty(), 30, t.importance()), instructor).id();
                topicIds.put(t.title(), topicId);
                List<Long> ids = new ArrayList<>();
                for (DemoQuestion q : t.questions()) {
                    ids.add(createQuestion(topicId, q, instructor));
                }
                questionsByTopic.put(topicId, ids);
                // fixed practice quiz: the four easy/medium questions
                quizService.createQuiz(courseId, new QuizRequest("Practice: " + t.title(), null, QuizType.PRACTICE,
                        topicId, 15, null, ids.subList(0, 4), true), instructor);
            }
        }
        for (List<String> edge : c.prerequisites()) {
            topicService.addPrerequisite(topicIds.get(edge.get(0)), topicIds.get(edge.get(1)), instructor);
        }
        // fixed end-of-course assessment: every HARD question
        List<Long> hard = questionsByTopic.values().stream().flatMap(ids -> ids.subList(4, 6).stream()).toList();
        long assessmentId = quizService.createQuiz(courseId, new QuizRequest("Final assessment", null,
                QuizType.ASSESSMENT, null, 30, java.math.BigDecimal.valueOf(60), hard, true), instructor).id();
        List<Long> practiceQuizzes = jdbc.queryForList(
                "select id from quizzes where course_id = ? and type = 'PRACTICE' order by id", Long.class, courseId);
        return new DemoCourseRef(courseId, new ArrayList<>(topicIds.values()), practiceQuizzes, assessmentId);
    }

    /** Options are shuffled (the JSON lists the correct answer first). */
    private long createQuestion(long topicId, DemoQuestion q, UserPrincipal instructor) {
        List<OptionRequest> options = new ArrayList<>();
        for (int i = 0; i < q.o().size(); i++) {
            options.add(new OptionRequest(q.o().get(i), i == q.a()));
        }
        Collections.shuffle(options, random);
        QuestionResponse saved = questionService.createQuestion(topicId,
                new QuestionRequest(q.q(), q.e(), Difficulty.valueOf(q.d()), options), instructor);
        long correct = saved.options().stream().filter(o -> o.correct()).findFirst().orElseThrow().id();
        long wrong = saved.options().stream().filter(o -> !o.correct()).findFirst().orElseThrow().id();
        answers.put(saved.id(), new AnswerKey(correct, wrong, saved.difficulty(), topicId));
        return saved.id();
    }

    // ------------------------------------------------------------------ simulation

    /**
     * Three weeks in one course: a diagnostic, practice (fixed or adaptive) before a recommendation snapshot
     * eight days ago, then more study on the recommended topics and, for stronger students, the assessment.
     */
    private void simulateStudent(UserPrincipal student, DemoCourseRef course, double baseAbility, boolean prefersAdaptive) {
        Map<Long, Double> ability = new HashMap<>();
        course.topicIds().forEach(t -> ability.put(t, clamp(baseAbility + (random.nextDouble() - 0.5) * 0.3)));
        Instant day0 = Instant.now().truncatedTo(ChronoUnit.DAYS);

        AttemptStartResponse diag = diagnosticService.startDiagnostic(course.id(), new DiagnosticRequest(null), student);
        answer(student, diag, ability, at(day0, 20));

        for (int d = 18; d >= 9; d -= 3) {
            takeQuiz(student, course, ability, prefersAdaptive, random.nextInt(course.topicIds().size()), at(day0, d));
        }

        // snapshot of recommendations eight days ago; later answers show whether they pointed at real gaps
        recommendationService.recommend(student, course.id());
        jdbc.update("update recommendations set created_at = ? where student_id = ?", ts(at(day0, 8)), student.getId());

        int sessions = 2 + random.nextInt(4);
        for (int s = 0; s < sessions; s++) {
            int daysAgo = Math.max(0, 7 - s * (7 / sessions) - random.nextInt(2));
            takeQuiz(student, course, ability, prefersAdaptive, -1, at(day0, daysAgo));
        }
        if (baseAbility > 0.6) {
            submitQuiz(student, course.assessmentId(), ability, at(day0, 1));
        }
        jdbc.update("""
                update student_progress set enrolled_at = ?,
                  last_accessed_at = (select max(submitted_at) from quiz_attempts a join quizzes q on q.id = a.quiz_id
                                      where a.student_id = student_progress.student_id and q.course_id = student_progress.course_id)
                where student_id = ? and course_id = ?""", ts(at(day0, 20)), student.getId(), course.id());
    }

    /** topicIndex -1 = study the currently top-recommended unlocked topic (falls back to a random one). */
    private void takeQuiz(UserPrincipal student, DemoCourseRef course, Map<Long, Double> ability, boolean adaptive,
                          int topicIndex, Instant when) {
        if (adaptive) {
            AdaptiveQuizResponse res = adaptiveQuizService.start(new AdaptiveQuizRequest(course.id(), 6, null), student);
            answer(student, res.attempt(), ability, when);
            res.plan().stream().filter(AdaptiveQuizResponse.TopicPlan::revisionScheduled).forEach(p ->
                    jdbc.update("update revision_schedules set next_review_at = ? where student_id = ? and topic_id = ?",
                            ts(when.plus(Duration.ofDays(1))), student.getId(), p.topicId()));
            return;
        }
        int index = topicIndex >= 0 ? topicIndex : recommendedTopicIndex(student, course);
        submitQuiz(student, course.practiceQuizIds().get(index), ability, when);
    }

    private int recommendedTopicIndex(UserPrincipal student, DemoCourseRef course) {
        List<Long> recommended = jdbc.queryForList("""
                select r.topic_id from recommendations r join topics t on t.id = r.topic_id
                join course_modules m on m.id = t.module_id where r.student_id = ? and m.course_id = ?
                order by r.score desc""", Long.class, student.getId(), course.id());
        int index = recommended.isEmpty() ? -1 : course.topicIds().indexOf(recommended.get(0));
        return index >= 0 ? index : random.nextInt(course.topicIds().size());
    }

    private void submitQuiz(UserPrincipal student, long quizId, Map<Long, Double> ability, Instant when) {
        answer(student, quizService.startAttempt(quizId, student), ability, when);
    }

    private void answer(UserPrincipal student, AttemptStartResponse attempt, Map<Long, Double> ability, Instant when) {
        List<SubmitAttemptRequest.Answer> list = new ArrayList<>();
        for (AttemptStartResponse.AttemptQuestion q : attempt.questions()) {
            AnswerKey key = answers.get(q.questionId());
            double p = clamp(ability.getOrDefault(key.topicId(), 0.5) + switch (key.difficulty()) {
                case EASY -> 0.15;
                case MEDIUM -> 0.0;
                case HARD -> -0.2;
            });
            boolean correct = random.nextDouble() < p;
            list.add(new SubmitAttemptRequest.Answer(q.questionId(), correct ? key.correct() : key.wrong(),
                    20 + random.nextInt(70)));
            ability.computeIfPresent(key.topicId(), (t, a) -> clamp(a + 0.015)); // same learning rate on every path
        }
        quizService.submitAttempt(attempt.attemptId(), new SubmitAttemptRequest(list), student);

        // move the attempt and its study session back to the simulated time
        Instant start = when.plus(Duration.ofMinutes(random.nextInt(600)));
        Instant end = start.plus(Duration.ofMinutes(2 + list.size()));
        jdbc.update("update quiz_attempts set started_at = ?, submitted_at = ? where id = ?", ts(start), ts(end), attempt.attemptId());
        jdbc.update("update question_attempts set answered_at = ? where quiz_attempt_id = ?", ts(end), attempt.attemptId());
        jdbc.update("""
                update study_sessions set started_at = ?, ended_at = ?, duration_minutes = ?
                where id = (select max(id) from study_sessions where student_id = ?)""",
                ts(start), ts(end), 2 + list.size(), student.getId());
    }

    // ------------------------------------------------------------------ helpers

    private UserPrincipal user(String email, String first, String last, RoleName role) {
        User u = authService.createUser(email, PASSWORD, first, last, role);
        return new UserPrincipal(u.getId(), u.getEmail(), u.getPasswordHash(), role, true);
    }

    /** Knowledge scores come from ml-service; give it a moment if the stack is still starting. */
    private void waitForMlService() throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            try {
                mlClient.scoreKnowledge(List.of());
                return;
            } catch (MlServiceException e) {
                Thread.sleep(2000);
            }
        }
        log.warn("DemoDataSeeder: ml-service unreachable - knowledge scores will stay empty");
    }

    private List<DemoCourse> loadCourses() throws IOException {
        try (InputStream in = new ClassPathResource("demo/courses.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        }
    }

    private static Instant at(Instant day0, int daysAgo) {
        return day0.minus(Duration.ofDays(daysAgo)).plus(Duration.ofHours(8));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static double clamp(double v) {
        return Math.max(0.05, Math.min(0.97, v));
    }

    record DemoCourse(String code, String title, String description, String instructor, List<DemoModule> modules,
                      List<List<String>> prerequisites) {
    }

    record DemoModule(String title, List<DemoTopic> topics) {
    }

    record DemoTopic(String title, Difficulty difficulty, double importance, List<DemoQuestion> questions) {
    }

    record DemoQuestion(String d, String q, List<String> o, int a, String e) {
    }

    private record AnswerKey(long correct, long wrong, Difficulty difficulty, long topicId) {
    }

    private record DemoCourseRef(long id, List<Long> topicIds, List<Long> practiceQuizIds, long assessmentId) {
    }
}
