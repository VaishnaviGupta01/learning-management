package com.lms.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.student.RecommendationsResponse;
import com.lms.dto.student.RecommendationsResponse.BlockedTopic;
import com.lms.dto.student.RecommendationsResponse.Item;
import com.lms.entity.Course;
import com.lms.entity.LearningResource;
import com.lms.entity.Recommendation;
import com.lms.entity.RevisionSchedule;
import com.lms.entity.StudentProgress;
import com.lms.entity.StudentTopicKnowledge;
import com.lms.entity.Topic;
import com.lms.entity.TopicPrerequisite;
import com.lms.entity.enums.CreatedByType;
import com.lms.entity.enums.Difficulty;
import com.lms.entity.enums.TopicClassification;
import com.lms.ml.MlClient;
import com.lms.ml.MlClient.RankedTopic;
import com.lms.ml.MlClient.RecommendCandidate;
import com.lms.ml.MlServiceException;
import com.lms.repository.LearningResourceRepository;
import com.lms.repository.RecommendationRepository;
import com.lms.repository.RevisionScheduleRepository;
import com.lms.repository.StudentProgressRepository;
import com.lms.repository.StudentTopicKnowledgeRepository;
import com.lms.repository.TopicPrerequisiteRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.UserRepository;
import com.lms.security.UserPrincipal;
import com.lms.util.GraphUtils;

/**
 * Recommendation engine (Phase 8).
 *
 * <ol>
 *   <li>Scope: one course, or every course the student has progress in.</li>
 *   <li>Gate on prerequisites with {@link GraphUtils}: a topic is <em>unlocked</em> when every ancestor in the
 *       prerequisite graph has mastery &ge; {@code app.recommendations.prerequisite-mastery}. Locked topics are
 *       not ranked; they are returned as "complete X first", where X are the incomplete ancestors the student
 *       can work on right now.</li>
 *   <li>Rank unlocked topics in ml-service using knowledge score, topic importance and revision urgency.</li>
 *   <li>Replace the student's stored recommendations with the top K.</li>
 * </ol>
 * If ml-service is down, the last stored ranking is returned with {@code stale = true}.
 */
@Service
@Transactional
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final CourseService courseService;
    private final TopicRepository topicRepository;
    private final TopicPrerequisiteRepository prerequisiteRepository;
    private final StudentTopicKnowledgeRepository knowledgeRepository;
    private final RevisionScheduleRepository revisionRepository;
    private final StudentProgressRepository progressRepository;
    private final LearningResourceRepository resourceRepository;
    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final MlClient mlClient;
    private final int topK;
    private final double prerequisiteMastery;

    public RecommendationService(CourseService courseService, TopicRepository topicRepository,
                                 TopicPrerequisiteRepository prerequisiteRepository,
                                 StudentTopicKnowledgeRepository knowledgeRepository,
                                 RevisionScheduleRepository revisionRepository,
                                 StudentProgressRepository progressRepository,
                                 LearningResourceRepository resourceRepository,
                                 RecommendationRepository recommendationRepository, UserRepository userRepository,
                                 MlClient mlClient,
                                 @Value("${app.recommendations.top-k:5}") int topK,
                                 @Value("${app.recommendations.prerequisite-mastery:0.6}") double prerequisiteMastery) {
        this.courseService = courseService;
        this.topicRepository = topicRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.revisionRepository = revisionRepository;
        this.progressRepository = progressRepository;
        this.resourceRepository = resourceRepository;
        this.recommendationRepository = recommendationRepository;
        this.userRepository = userRepository;
        this.mlClient = mlClient;
        this.topK = topK;
        this.prerequisiteMastery = prerequisiteMastery;
    }

    public RecommendationsResponse recommend(UserPrincipal student, Long courseId) {
        Long studentId = student.getId();
        Instant now = Instant.now();

        Map<Long, StudentTopicKnowledge> knowledge = knowledgeRepository.findByStudentId(studentId).stream()
                .collect(Collectors.toMap(k -> k.getTopic().getId(), Function.identity()));
        Map<Long, RevisionSchedule> schedules = revisionRepository
                .findByStudentIdAndActiveTrueOrderByNextReviewAtAsc(studentId).stream()
                .collect(Collectors.toMap(s -> s.getTopic().getId(), Function.identity(), (a, b) -> a));
        Predicate<Long> complete = topicId -> {
            StudentTopicKnowledge k = knowledge.get(topicId);
            return k != null && k.getMasteryScore() >= prerequisiteMastery;
        };

        Map<Long, Topic> unlocked = new LinkedHashMap<>();
        List<BlockedTopic> blocked = new ArrayList<>();
        for (Course course : coursesInScope(student, courseId)) {
            gate(course, complete, unlocked, blocked);
        }

        List<RecommendCandidate> candidates = unlocked.values().stream()
                .map(t -> new RecommendCandidate(t.getId(), score(knowledge.get(t.getId())), t.getImportance(),
                        urgency(schedules.get(t.getId()), now)))
                .toList();

        List<RankedTopic> ranked;
        try {
            ranked = candidates.isEmpty() ? List.of() : mlClient.recommend(candidates, topK);
        } catch (MlServiceException e) {
            log.warn("Recommendation ranking unavailable for student {}: {}", studentId, e.getMessage());
            return new RecommendationsResponse(now, true, storedItems(studentId, courseId, knowledge, schedules, now),
                    blocked);
        }

        // Replace the stored set, but keep other courses' rows when only one course was requested.
        List<Recommendation> previous = recommendationRepository.findByStudentIdAndDismissedFalseOrderByScoreDesc(studentId);
        recommendationRepository.deleteAll(courseId == null ? previous
                : previous.stream().filter(r -> inCourse(r, courseId)).toList());
        recommendationRepository.flush();

        List<Item> items = new ArrayList<>();
        for (RankedTopic r : ranked) {
            Topic topic = unlocked.get(r.topicId());
            StudentTopicKnowledge k = knowledge.get(topic.getId());
            LearningResource resource = suggestResource(topic, score(k));

            Recommendation rec = new Recommendation();
            rec.setStudent(userRepository.getReferenceById(studentId));
            rec.setTopic(topic);
            rec.setResource(resource);
            rec.setReason(r.reason());
            rec.setScore(r.priority());
            rec.setSource(CreatedByType.AI);
            recommendationRepository.save(rec);

            items.add(item(rec, r.rank(), k, urgency(schedules.get(topic.getId()), now)));
        }
        return new RecommendationsResponse(now, false, items, blocked);
    }

    // ------------------------------------------------------------------ prerequisite gating

    private void gate(Course course, Predicate<Long> complete, Map<Long, Topic> unlocked, List<BlockedTopic> blocked) {
        List<Topic> topics = topicRepository.findByModuleCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(course.getId());
        Map<Long, Topic> byId = topics.stream().collect(Collectors.toMap(Topic::getId, Function.identity()));

        // topic -> its direct prerequisites (reverse of the learning-order graph)
        Map<Long, List<Long>> requires = new LinkedHashMap<>();
        topics.forEach(t -> requires.put(t.getId(), new ArrayList<>()));
        for (TopicPrerequisite p : prerequisiteRepository.findByTopicModuleCourseId(course.getId())) {
            requires.get(p.getTopic().getId()).add(p.getPrerequisiteTopic().getId());
        }

        for (Topic topic : topics) {
            List<Long> incomplete = incompleteAncestors(requires, topic.getId(), complete);
            if (incomplete.isEmpty()) {
                unlocked.put(topic.getId(), topic);
                continue;
            }
            // Point at the incomplete ancestors that are themselves workable now (their own prerequisites are done).
            List<Long> firstSteps = incomplete.stream()
                    .filter(a -> incompleteAncestors(requires, a, complete).isEmpty())
                    .toList();
            String names = firstSteps.stream().map(id -> byId.get(id).getTitle()).collect(Collectors.joining(", "));
            blocked.add(new BlockedTopic(topic.getId(), topic.getTitle(), course.getId(), firstSteps,
                    "Complete " + names + " first"));
        }
    }

    private static List<Long> incompleteAncestors(Map<Long, List<Long>> requires, Long topicId, Predicate<Long> complete) {
        return GraphUtils.bfs(requires, topicId).stream()
                .filter(id -> !id.equals(topicId) && !complete.test(id))
                .toList();
    }

    // ------------------------------------------------------------------ inputs

    /**
     * Revision urgency in [0, 1]: 0 with no active schedule or a review more than a day away, 0.25 when due
     * within 24h, and from 0.5 (just due) rising to 1.0 at a week overdue.
     */
    static double urgency(RevisionSchedule schedule, Instant now) {
        if (schedule == null) {
            return 0.0;
        }
        long hoursOverdue = Duration.between(schedule.getNextReviewAt(), now).toHours();
        if (hoursOverdue < 0) {
            return hoursOverdue > -24 ? 0.25 : 0.0;
        }
        return Math.min(1.0, 0.5 + 0.5 * hoursOverdue / (7 * 24.0));
    }

    private static double score(StudentTopicKnowledge k) {
        return k == null ? 0.0 : k.getMasteryScore();
    }

    /** Easier material for low mastery, harder for high; falls back to the first resource. */
    private LearningResource suggestResource(Topic topic, double knowledgeScore) {
        List<LearningResource> resources = resourceRepository.findByTopicIdOrderByOrderIndexAsc(topic.getId());
        Difficulty wanted = knowledgeScore < 0.4 ? Difficulty.EASY : knowledgeScore < 0.8 ? Difficulty.MEDIUM : Difficulty.HARD;
        return resources.stream().filter(r -> r.getDifficulty() == wanted).findFirst()
                .orElse(resources.isEmpty() ? null : resources.get(0));
    }

    private List<Course> coursesInScope(UserPrincipal student, Long courseId) {
        if (courseId != null) {
            Course course = courseService.findCourse(courseId);
            CourseAccess.requireView(course, student);
            return List.of(course);
        }
        return progressRepository.findByStudentId(student.getId()).stream().map(StudentProgress::getCourse).toList();
    }

    // ------------------------------------------------------------------ stored fallback

    private List<Item> storedItems(Long studentId, Long courseId, Map<Long, StudentTopicKnowledge> knowledge,
                                   Map<Long, RevisionSchedule> schedules, Instant now) {
        List<Recommendation> stored = recommendationRepository.findByStudentIdAndDismissedFalseOrderByScoreDesc(studentId)
                .stream().filter(r -> courseId == null || inCourse(r, courseId)).toList();
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < stored.size(); i++) {
            Recommendation r = stored.get(i);
            items.add(item(r, i + 1, knowledge.get(r.getTopic().getId()), urgency(schedules.get(r.getTopic().getId()), now)));
        }
        return items;
    }

    private static boolean inCourse(Recommendation r, Long courseId) {
        return r.getTopic() != null && r.getTopic().getModule().getCourse().getId().equals(courseId);
    }

    private static Item item(Recommendation r, int rank, StudentTopicKnowledge k, double urgency) {
        Topic t = r.getTopic();
        Course c = t.getModule().getCourse();
        return new Item(r.getId(), rank, t.getId(), t.getTitle(), c.getId(), c.getTitle(), r.getScore(), score(k),
                k == null ? TopicClassification.NOT_STARTED : k.getClassification(), t.getImportance(), urgency,
                r.getReason(), r.getResource() == null ? null : r.getResource().getId(),
                r.getResource() == null ? null : r.getResource().getTitle());
    }
}
