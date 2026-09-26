package com.lms.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.lms.dto.tutor.AiInteractionResponse;
import com.lms.dto.tutor.TutorChatRequest;
import com.lms.dto.tutor.TutorChatResponse;
import com.lms.entity.AiInteraction;
import com.lms.entity.Course;
import com.lms.entity.StudentTopicKnowledge;
import com.lms.entity.Topic;
import com.lms.entity.User;
import com.lms.entity.enums.AiInteractionType;
import com.lms.entity.enums.TopicClassification;
import com.lms.exception.BadRequestException;
import com.lms.exception.ResourceNotFoundException;
import com.lms.exception.ServiceUnavailableException;
import com.lms.rag.RagClient;
import com.lms.rag.RagClient.KnowledgeLevel;
import com.lms.rag.RagClient.StudentContext;
import com.lms.rag.RagClient.TutorReply;
import com.lms.rag.RagClient.TutorRequest;
import com.lms.rag.RagClient.Turn;
import com.lms.rag.RagServiceException;
import com.lms.repository.AiInteractionRepository;
import com.lms.repository.CourseRepository;
import com.lms.repository.StudentTopicKnowledgeRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.UserRepository;
import com.lms.security.UserPrincipal;

/**
 * AI tutor proxy (Phase 10). Builds the student's context (course, topic, knowledge level), forwards the turn
 * to rag-service, which holds the LLM credentials, and logs every exchange - including failed ones - to
 * {@code ai_interactions}.
 *
 * <p>Not {@code @Transactional}: the LLM call can take many seconds, so the database work runs in short
 * separate transactions around it, and a failed call is still logged.
 */
@Service
public class AiTutorService {

    private static final int MAX_WEAK_TOPICS = 5;

    private final RagClient ragClient;
    private final CourseRepository courseRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final StudentTopicKnowledgeRepository knowledgeRepository;
    private final AiInteractionRepository interactionRepository;
    private final TransactionTemplate tx;

    public AiTutorService(RagClient ragClient, CourseRepository courseRepository, TopicRepository topicRepository,
                          UserRepository userRepository, StudentTopicKnowledgeRepository knowledgeRepository,
                          AiInteractionRepository interactionRepository, PlatformTransactionManager txManager) {
        this.ragClient = ragClient;
        this.courseRepository = courseRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.interactionRepository = interactionRepository;
        this.tx = new TransactionTemplate(txManager);
    }

    public TutorChatResponse chat(TutorChatRequest request, UserPrincipal user) {
        Scope scope = tx.execute(status -> resolveScope(request, user));
        StudentContext context = tx.execute(status -> buildContext(scope, user));
        List<Turn> history = request.history() == null ? List.of()
                : request.history().stream().map(t -> new Turn(t.role(), t.content())).toList();

        long started = System.nanoTime();
        TutorReply reply;
        try {
            reply = ragClient.chat(new TutorRequest(request.message(), history, context));
        } catch (RagServiceException e) {
            log(user, scope, request.message(), null, elapsedMs(started));
            throw new ServiceUnavailableException(e.getUserMessage());
        }
        long latencyMs = elapsedMs(started);
        AiInteraction saved = log(user, scope, request.message(), reply, latencyMs);
        return new TutorChatResponse(saved.getId(), reply.reply(), reply.model(), reply.refused(), latencyMs,
                reply.grounded(), reply.notInMaterial(), reply.sources() == null ? List.of()
                        : reply.sources().stream().map(TutorChatResponse.Source::from).toList());
    }

    /** The caller's most recent tutor exchanges, newest first. */
    public List<AiInteractionResponse> history(UserPrincipal user, int limit) {
        return tx.execute(status -> interactionRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, limit))
                .map(AiInteractionResponse::from)
                .getContent());
    }

    // ------------------------------------------------------------------ context

    private Scope resolveScope(TutorChatRequest request, UserPrincipal user) {
        Topic topic = null;
        Course course = null;
        if (request.topicId() != null) {
            topic = topicRepository.findById(request.topicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", request.topicId()));
            course = topic.getModule().getCourse();
            if (request.courseId() != null && !request.courseId().equals(course.getId())) {
                throw new BadRequestException("Topic " + topic.getId() + " does not belong to course " + request.courseId());
            }
        } else if (request.courseId() != null) {
            course = courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Course", request.courseId()));
        }
        if (course != null) {
            CourseAccess.requireView(course, user);
        }
        return new Scope(course == null ? null : course.getId(), topic == null ? null : topic.getId());
    }

    private StudentContext buildContext(Scope scope, UserPrincipal user) {
        User me = userRepository.findById(user.getId()).orElseThrow();
        Course course = scope.courseId() == null ? null : courseRepository.findById(scope.courseId()).orElseThrow();
        Topic topic = scope.topicId() == null ? null : topicRepository.findById(scope.topicId()).orElseThrow();

        KnowledgeLevel topicKnowledge = null;
        List<KnowledgeLevel> weakTopics = List.of();
        if (user.isStudent() && course != null) {
            List<StudentTopicKnowledge> known = knowledgeRepository
                    .findByStudentIdAndTopicModuleCourseId(user.getId(), course.getId());
            if (topic != null) {
                topicKnowledge = known.stream().filter(k -> k.getTopic().getId().equals(topic.getId()))
                        .findFirst().map(AiTutorService::level).orElse(null);
            }
            weakTopics = known.stream()
                    .filter(k -> k.getClassification() == TopicClassification.WEAK
                            || k.getClassification() == TopicClassification.NEEDS_PRACTICE)
                    .sorted(Comparator.comparingDouble(StudentTopicKnowledge::getMasteryScore))
                    .limit(MAX_WEAK_TOPICS)
                    .map(AiTutorService::level)
                    .toList();
        }
        return new StudentContext(scope.courseId(), me.getFirstName(),
                course == null ? null : course.getTitle(), course == null ? null : course.getDescription(),
                topic == null ? null : topic.getTitle(), topic == null ? null : topic.getDescription(),
                topicKnowledge, weakTopics);
    }

    private static KnowledgeLevel level(StudentTopicKnowledge k) {
        return new KnowledgeLevel(k.getTopic().getTitle(), k.getClassification().name(), k.getMasteryScore());
    }

    // ------------------------------------------------------------------ logging

    private AiInteraction log(UserPrincipal user, Scope scope, String prompt, TutorReply reply, long latencyMs) {
        return tx.execute(status -> {
            AiInteraction i = new AiInteraction();
            i.setUser(userRepository.getReferenceById(user.getId()));
            i.setCourse(scope.courseId() == null ? null : courseRepository.getReferenceById(scope.courseId()));
            i.setTopic(scope.topicId() == null ? null : topicRepository.getReferenceById(scope.topicId()));
            i.setType(AiInteractionType.CHAT);
            i.setPrompt(prompt);
            i.setLatencyMs(latencyMs);
            if (reply != null) {
                i.setResponse(reply.reply());
                i.setModelName(reply.model() != null ? reply.model() : reply.notInMaterial() ? "none:not-in-material" : null);
                i.setInputTokens(reply.inputTokens());
                i.setOutputTokens(reply.outputTokens());
            }
            return interactionRepository.save(i);
        });
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private record Scope(Long courseId, Long topicId) {
    }
}
