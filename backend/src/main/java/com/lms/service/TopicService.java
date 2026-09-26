package com.lms.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.course.LearningPathResponse;
import com.lms.dto.course.PrerequisiteResponse;
import com.lms.dto.course.TopicRequest;
import com.lms.dto.course.TopicResponse;
import com.lms.entity.Course;
import com.lms.entity.CourseModule;
import com.lms.entity.Topic;
import com.lms.entity.TopicPrerequisite;
import com.lms.entity.enums.Difficulty;
import com.lms.exception.BadRequestException;
import com.lms.exception.ConflictException;
import com.lms.exception.ResourceNotFoundException;
import com.lms.repository.CourseModuleRepository;
import com.lms.repository.TopicPrerequisiteRepository;
import com.lms.repository.TopicRepository;
import com.lms.security.UserPrincipal;
import com.lms.util.GraphUtils;

@Service
@Transactional
public class TopicService {

    private final TopicRepository topicRepository;
    private final CourseModuleRepository moduleRepository;
    private final TopicPrerequisiteRepository prerequisiteRepository;
    private final CourseService courseService;

    public TopicService(TopicRepository topicRepository, CourseModuleRepository moduleRepository,
                        TopicPrerequisiteRepository prerequisiteRepository, CourseService courseService) {
        this.topicRepository = topicRepository;
        this.moduleRepository = moduleRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.courseService = courseService;
    }

    // ------------------------------------------------------------------ topics

    @Transactional(readOnly = true)
    public List<TopicResponse> listCourseTopics(Long courseId, UserPrincipal user) {
        Course course = courseService.findCourse(courseId);
        CourseAccess.requireView(course, user);
        return toResponses(courseTopics(courseId), courseId);
    }

    @Transactional(readOnly = true)
    public TopicResponse getTopic(Long topicId, UserPrincipal user) {
        Topic topic = findTopic(topicId);
        CourseAccess.requireView(courseOf(topic), user);
        return TopicResponse.from(topic, prerequisiteIds(topicId));
    }

    public TopicResponse createTopic(Long moduleId, TopicRequest request, UserPrincipal user) {
        CourseModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module", moduleId));
        CourseAccess.requireManage(module.getCourse(), user);
        Topic topic = new Topic();
        topic.setModule(module);
        topic.setOrderIndex(request.orderIndex() != null ? request.orderIndex() : module.getTopics().size());
        applyTopicFields(topic, request);
        module.getTopics().add(topic);
        topicRepository.save(topic);
        return TopicResponse.from(topic, List.of());
    }

    public TopicResponse updateTopic(Long topicId, TopicRequest request, UserPrincipal user) {
        Topic topic = findTopic(topicId);
        CourseAccess.requireManage(courseOf(topic), user);
        if (request.orderIndex() != null) {
            topic.setOrderIndex(request.orderIndex());
        }
        applyTopicFields(topic, request);
        return TopicResponse.from(topic, prerequisiteIds(topicId));
    }

    public void deleteTopic(Long topicId, UserPrincipal user) {
        Topic topic = findTopic(topicId);
        CourseAccess.requireManage(courseOf(topic), user);
        prerequisiteRepository.deleteByTopicIdOrPrerequisiteTopicId(topicId, topicId);
        topic.getModule().getTopics().remove(topic); // orphanRemoval deletes it
        topicRepository.flush(); // surface FK violations (questions, resources, ...) as 409 here
    }

    // ------------------------------------------------------------------ prerequisites

    @Transactional(readOnly = true)
    public List<PrerequisiteResponse> listPrerequisites(Long topicId, UserPrincipal user) {
        Topic topic = findTopic(topicId);
        CourseAccess.requireView(courseOf(topic), user);
        return prerequisiteRepository.findByTopicId(topicId).stream().map(PrerequisiteResponse::from).toList();
    }

    /**
     * Adds "topic requires prerequisite". Both topics must be in the same course, and the whole course
     * graph (existing edges plus the new one) must stay acyclic, not just the immediate pair.
     */
    public PrerequisiteResponse addPrerequisite(Long topicId, Long prerequisiteTopicId, UserPrincipal user) {
        Topic topic = findTopic(topicId);
        Course course = courseOf(topic);
        CourseAccess.requireManage(course, user);
        Topic prerequisite = findTopic(prerequisiteTopicId);

        if (topicId.equals(prerequisiteTopicId)) {
            throw new BadRequestException("A topic cannot be its own prerequisite");
        }
        if (!courseOf(prerequisite).getId().equals(course.getId())) {
            throw new BadRequestException("Prerequisite must belong to the same course");
        }
        if (prerequisiteRepository.existsByTopicIdAndPrerequisiteTopicId(topicId, prerequisiteTopicId)) {
            throw new ConflictException("Prerequisite already exists");
        }

        List<Topic> topics = courseTopics(course.getId());
        Map<Long, List<Long>> graph = buildGraph(topics, prerequisiteRepository.findByTopicModuleCourseId(course.getId()));
        graph.get(prerequisiteTopicId).add(topicId);
        GraphUtils.findCycle(graph).ifPresent(cycle -> {
            Map<Long, String> titles = topics.stream().collect(Collectors.toMap(Topic::getId, Topic::getTitle));
            String path = cycle.stream().map(titles::get).collect(Collectors.joining(" -> "));
            throw new BadRequestException("Adding this prerequisite would create a cycle: " + path);
        });

        TopicPrerequisite edge = new TopicPrerequisite();
        edge.setTopic(topic);
        edge.setPrerequisiteTopic(prerequisite);
        return PrerequisiteResponse.from(prerequisiteRepository.save(edge));
    }

    public void removePrerequisite(Long topicId, Long prerequisiteTopicId, UserPrincipal user) {
        Topic topic = findTopic(topicId);
        CourseAccess.requireManage(courseOf(topic), user);
        if (!prerequisiteRepository.existsByTopicIdAndPrerequisiteTopicId(topicId, prerequisiteTopicId)) {
            throw new ResourceNotFoundException("Prerequisite " + prerequisiteTopicId + " of topic " + topicId + " not found");
        }
        prerequisiteRepository.deleteByTopicIdAndPrerequisiteTopicId(topicId, prerequisiteTopicId);
    }

    /** Course topics in topological order; ties keep module/topic order. */
    @Transactional(readOnly = true)
    public LearningPathResponse learningPath(Long courseId, UserPrincipal user) {
        Course course = courseService.findCourse(courseId);
        CourseAccess.requireView(course, user);
        List<Topic> topics = courseTopics(courseId);
        Map<Long, List<Long>> graph = buildGraph(topics, prerequisiteRepository.findByTopicModuleCourseId(courseId));
        List<Long> order = GraphUtils.topologicalSort(graph);

        Map<Long, Topic> byId = topics.stream().collect(Collectors.toMap(Topic::getId, Function.identity()));
        List<Topic> ordered = order.stream().map(byId::get).toList();
        return new LearningPathResponse(courseId, toResponses(ordered, courseId));
    }

    // ------------------------------------------------------------------ helpers

    private Topic findTopic(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", topicId));
    }

    private static Course courseOf(Topic topic) {
        return topic.getModule().getCourse();
    }

    private List<Topic> courseTopics(Long courseId) {
        return topicRepository.findByModuleCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(courseId);
    }

    private List<Long> prerequisiteIds(Long topicId) {
        return prerequisiteRepository.findByTopicId(topicId).stream()
                .map(p -> p.getPrerequisiteTopic().getId()).toList();
    }

    private List<TopicResponse> toResponses(List<Topic> topics, Long courseId) {
        Map<Long, List<Long>> prereqs = prerequisiteRepository.findByTopicModuleCourseId(courseId).stream()
                .collect(Collectors.groupingBy(p -> p.getTopic().getId(),
                        Collectors.mapping(p -> p.getPrerequisiteTopic().getId(), Collectors.toList())));
        return topics.stream().map(t -> TopicResponse.from(t, prereqs.getOrDefault(t.getId(), List.of()))).toList();
    }

    /** Adjacency map prerequisite -> dependents, keyed in course order so sorting is deterministic. */
    private static Map<Long, List<Long>> buildGraph(List<Topic> topics, List<TopicPrerequisite> edges) {
        Map<Long, List<Long>> graph = new LinkedHashMap<>();
        topics.forEach(t -> graph.put(t.getId(), new ArrayList<>()));
        for (TopicPrerequisite e : edges) {
            graph.get(e.getPrerequisiteTopic().getId()).add(e.getTopic().getId());
        }
        return graph;
    }

    private static void applyTopicFields(Topic topic, TopicRequest request) {
        topic.setTitle(request.title().trim());
        topic.setDescription(request.description());
        topic.setEstimatedMinutes(request.estimatedMinutes());
        if (request.importance() != null) {
            topic.setImportance(request.importance());
        }
        if (request.difficulty() != null) {
            topic.setDifficulty(request.difficulty());
        } else if (topic.getDifficulty() == null) {
            topic.setDifficulty(Difficulty.MEDIUM);
        }
    }
}
