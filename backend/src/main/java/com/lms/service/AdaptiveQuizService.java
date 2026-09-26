package com.lms.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.quiz.AdaptiveQuizRequest;
import com.lms.dto.quiz.AdaptiveQuizResponse;
import com.lms.dto.quiz.AdaptiveQuizResponse.Adjustment;
import com.lms.dto.quiz.AdaptiveQuizResponse.TopicPlan;
import com.lms.dto.quiz.AttemptStartResponse;
import com.lms.entity.Course;
import com.lms.entity.Question;
import com.lms.entity.QuestionAttempt;
import com.lms.entity.Quiz;
import com.lms.entity.RevisionSchedule;
import com.lms.entity.StudentTopicKnowledge;
import com.lms.entity.Topic;
import com.lms.entity.enums.CreatedByType;
import com.lms.entity.enums.Difficulty;
import com.lms.entity.enums.QuestionStatus;
import com.lms.entity.enums.QuizType;
import com.lms.entity.enums.RevisionStage;
import com.lms.entity.enums.TopicClassification;
import com.lms.exception.BadRequestException;
import com.lms.repository.QuestionAttemptRepository;
import com.lms.repository.QuestionRepository;
import com.lms.repository.QuizRepository;
import com.lms.repository.RevisionScheduleRepository;
import com.lms.repository.StudentTopicKnowledgeRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.UserRepository;
import com.lms.security.UserPrincipal;

/**
 * Builds a personal ADAPTIVE quiz on the fly (Phase 9).
 *
 * <p><b>Difficulty per topic</b> from the student's last {@code app.adaptive.window} answers on it:
 * accuracy &gt; 80% moves one tier up from the most recently answered difficulty, 50-80% stays, &lt; 50% moves
 * one tier down and puts the topic on the revision schedule. With no history the topic's own difficulty is used.
 *
 * <p><b>Questions per topic</b> are shared out in proportion to a weight from the topic's knowledge band
 * (WEAK 4, NEEDS_PRACTICE 3, NOT_STARTED 2, MODERATE 2, STRONG 1) using Sainte-Lague allocation, capped
 * by how many approved questions each topic has. Within a topic, questions closest to the target tier win.
 */
@Service
@Transactional
public class AdaptiveQuizService {

    static final double UP_THRESHOLD = 0.8;
    static final double DOWN_THRESHOLD = 0.5;

    private static final Map<TopicClassification, Integer> WEIGHTS = new EnumMap<>(Map.of(
            TopicClassification.WEAK, 4,
            TopicClassification.NEEDS_PRACTICE, 3,
            TopicClassification.NOT_STARTED, 2,
            TopicClassification.MODERATE, 2,
            TopicClassification.STRONG, 1));

    private final CourseService courseService;
    private final QuizService quizService;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAttemptRepository questionAttemptRepository;
    private final StudentTopicKnowledgeRepository knowledgeRepository;
    private final RevisionScheduleRepository revisionRepository;
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final int window;
    private final int defaultQuestionCount;

    public AdaptiveQuizService(CourseService courseService, QuizService quizService, TopicRepository topicRepository,
                               QuestionRepository questionRepository,
                               QuestionAttemptRepository questionAttemptRepository,
                               StudentTopicKnowledgeRepository knowledgeRepository,
                               RevisionScheduleRepository revisionRepository, QuizRepository quizRepository,
                               UserRepository userRepository,
                               @Value("${app.adaptive.window:10}") int window,
                               @Value("${app.adaptive.default-question-count:10}") int defaultQuestionCount) {
        this.courseService = courseService;
        this.quizService = quizService;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.questionAttemptRepository = questionAttemptRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.revisionRepository = revisionRepository;
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
        this.window = window;
        this.defaultQuestionCount = defaultQuestionCount;
    }

    public AdaptiveQuizResponse start(AdaptiveQuizRequest request, UserPrincipal user) {
        Course course = courseService.findCourse(request.courseId());
        CourseAccess.requireView(course, user);
        int questionCount = request.questionCount() != null ? request.questionCount() : defaultQuestionCount;

        // topics that can contribute questions, in course order
        Map<Topic, List<Question>> pool = new LinkedHashMap<>();
        for (Topic topic : selectTopics(course.getId(), request.topicIds())) {
            List<Question> approved = questionRepository.findByTopicIdAndStatus(topic.getId(), QuestionStatus.APPROVED);
            if (!approved.isEmpty()) {
                pool.put(topic, approved);
            }
        }
        if (pool.isEmpty()) {
            throw new BadRequestException("No approved questions are available for the selected topics");
        }

        Map<Long, List<QuestionAttempt>> history = recentAnswers(user.getId(), pool.keySet());
        Map<Long, StudentTopicKnowledge> knowledge = knowledgeRepository
                .findByStudentIdAndTopicModuleCourseId(user.getId(), course.getId()).stream()
                .collect(Collectors.toMap(k -> k.getTopic().getId(), Function.identity()));

        List<Topic> topics = new ArrayList<>(pool.keySet());
        Map<Long, TopicClassification> bands = new LinkedHashMap<>();
        topics.forEach(t -> bands.put(t.getId(), knowledge.containsKey(t.getId())
                ? knowledge.get(t.getId()).getClassification() : TopicClassification.NOT_STARTED));
        List<Topic> slots = allocate(topics, bands, pool, questionCount);

        Quiz quiz = new Quiz();
        quiz.setCourse(course);
        quiz.setTitle("Adaptive practice: " + course.getTitle());
        quiz.setDescription("Built on the fly from recent accuracy and knowledge bands");
        quiz.setType(QuizType.ADAPTIVE);
        quiz.setCreatedByType(CreatedByType.AI); // system-generated
        quiz.setStudent(userRepository.getReferenceById(user.getId()));
        quiz.setPublished(false);

        List<TopicPlan> plan = new ArrayList<>();
        Map<Long, List<Question>> picks = new LinkedHashMap<>();
        for (Topic topic : topics) {
            int allocated = (int) slots.stream().filter(t -> t.getId().equals(topic.getId())).count();
            List<QuestionAttempt> recent = history.getOrDefault(topic.getId(), List.of());
            Double accuracy = recent.isEmpty() ? null
                    : recent.stream().filter(QuestionAttempt::isCorrect).count() / (double) recent.size();
            Difficulty current = recent.isEmpty() ? topic.getDifficulty() : recent.get(0).getQuestion().getDifficulty();
            Adjustment adjustment = adjustment(accuracy);
            Difficulty target = shift(current, adjustment);
            boolean scheduled = adjustment == Adjustment.DOWN && scheduleRevision(user.getId(), topic);

            picks.put(topic.getId(), pickQuestions(pool.get(topic), target, allocated));
            plan.add(new TopicPlan(topic.getId(), topic.getTitle(), bands.get(topic.getId()), accuracy, recent.size(),
                    current, target, adjustment, allocated, scheduled));
        }

        // add questions in slot order so weak topics are spread through the quiz
        Map<Long, Integer> used = new LinkedHashMap<>();
        for (Topic slot : slots) {
            int i = used.merge(slot.getId(), 1, Integer::sum) - 1;
            QuizService.addQuestion(quiz, picks.get(slot.getId()).get(i));
        }
        quizRepository.save(quiz);

        AttemptStartResponse attempt = quizService.startAttempt(quiz.getId(), user);
        return new AdaptiveQuizResponse(attempt, plan);
    }

    // ------------------------------------------------------------------ difficulty

    static Adjustment adjustment(Double accuracy) {
        if (accuracy == null) {
            return Adjustment.BASELINE;
        }
        if (accuracy > UP_THRESHOLD) {
            return Adjustment.UP;
        }
        return accuracy < DOWN_THRESHOLD ? Adjustment.DOWN : Adjustment.SAME;
    }

    static Difficulty shift(Difficulty current, Adjustment adjustment) {
        Difficulty[] tiers = Difficulty.values(); // EASY, MEDIUM, HARD
        int i = current.ordinal() + switch (adjustment) {
            case UP -> 1;
            case DOWN -> -1;
            default -> 0;
        };
        return tiers[Math.max(0, Math.min(tiers.length - 1, i))];
    }

    /** Closest tier first; random order within a tier so repeated quizzes vary. */
    private static List<Question> pickQuestions(List<Question> approved, Difficulty target, int count) {
        List<Question> shuffled = new ArrayList<>(approved);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        shuffled.sort(Comparator.comparingInt(q -> Math.abs(q.getDifficulty().ordinal() - target.ordinal())));
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    /** Creates (or reactivates) the topic's revision schedule, first review in one day. */
    private boolean scheduleRevision(Long studentId, Topic topic) {
        RevisionSchedule schedule = revisionRepository.findByStudentIdAndTopicId(studentId, topic.getId()).orElse(null);
        if (schedule != null && schedule.isActive()) {
            return false;
        }
        if (schedule == null) {
            schedule = new RevisionSchedule();
            schedule.setStudent(userRepository.getReferenceById(studentId));
            schedule.setTopic(topic);
        }
        schedule.setActive(true);
        schedule.setStage(RevisionStage.DAY_1);
        schedule.setNextReviewAt(Instant.now().plus(Duration.ofDays(1)));
        revisionRepository.save(schedule);
        return true;
    }

    // ------------------------------------------------------------------ allocation

    /**
     * Sainte-Lague allocation: each slot goes to the topic with the largest weight / (2 * questions already
     * given + 1) that still has unused questions; ties go to the earlier topic. Stays close to proportional
     * (weights 4:2:1 over 9 questions give 5/3/1), unlike D'Hondt, which over-rewards the heaviest weight.
     */
    static List<Topic> allocate(List<Topic> topics, Map<Long, TopicClassification> bands,
                                Map<Topic, List<Question>> pool, int slots) {
        Map<Long, Integer> given = new LinkedHashMap<>();
        List<Topic> order = new ArrayList<>();
        for (int s = 0; s < slots; s++) {
            Topic best = null;
            double bestQuotient = -1;
            for (Topic t : topics) {
                int n = given.getOrDefault(t.getId(), 0);
                if (n >= pool.get(t).size()) {
                    continue;
                }
                double quotient = WEIGHTS.get(bands.get(t.getId())) / (double) (2 * n + 1);
                if (quotient > bestQuotient) {
                    best = t;
                    bestQuotient = quotient;
                }
            }
            if (best == null) {
                break; // every topic exhausted: the quiz is shorter than requested
            }
            given.merge(best.getId(), 1, Integer::sum);
            order.add(best);
        }
        return order;
    }

    // ------------------------------------------------------------------ inputs

    private List<Topic> selectTopics(Long courseId, List<Long> topicIds) {
        List<Topic> all = topicRepository.findByModuleCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(courseId);
        if (topicIds == null || topicIds.isEmpty()) {
            return all;
        }
        Set<Long> wanted = new HashSet<>(topicIds);
        List<Topic> selected = all.stream().filter(t -> wanted.contains(t.getId())).toList();
        if (selected.size() != wanted.size()) {
            throw new BadRequestException("Some topicIds do not belong to this course");
        }
        return selected;
    }

    /** Last {@link #window} submitted answers per topic, newest first. */
    private Map<Long, List<QuestionAttempt>> recentAnswers(Long studentId, Set<Topic> topics) {
        Map<Long, List<QuestionAttempt>> byTopic = new LinkedHashMap<>();
        List<Long> ids = topics.stream().map(Topic::getId).toList();
        for (QuestionAttempt qa : questionAttemptRepository.findSubmittedByStudentAndTopics(studentId, ids)) {
            List<QuestionAttempt> list = byTopic.computeIfAbsent(qa.getQuestion().getTopic().getId(), k -> new ArrayList<>());
            if (list.size() < window) {
                list.add(qa);
            }
        }
        return byTopic;
    }
}
