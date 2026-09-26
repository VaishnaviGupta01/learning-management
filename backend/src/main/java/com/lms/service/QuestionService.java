package com.lms.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.question.OptionRequest;
import com.lms.dto.question.QuestionRequest;
import com.lms.dto.question.QuestionResponse;
import com.lms.entity.Question;
import com.lms.entity.QuestionOption;
import com.lms.entity.Topic;
import com.lms.entity.enums.CreatedByType;
import com.lms.entity.enums.Difficulty;
import com.lms.entity.enums.QuestionStatus;
import com.lms.exception.ConflictException;
import com.lms.exception.ResourceNotFoundException;
import com.lms.repository.QuestionRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.UserRepository;
import com.lms.security.UserPrincipal;

/**
 * Question bank. Instructor-authored questions are APPROVED immediately; AI-generated ones enter as
 * PENDING_REVIEW and must be approved (or rejected) by an instructor who manages the course.
 */
@Service
@Transactional
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;

    public QuestionService(QuestionRepository questionRepository, TopicRepository topicRepository,
                           UserRepository userRepository) {
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
    }

    public QuestionResponse createQuestion(Long topicId, QuestionRequest request, UserPrincipal user) {
        Topic topic = findTopic(topicId);
        CourseAccess.requireManage(topic.getModule().getCourse(), user);
        Question question = newQuestion(topic, request);
        question.setStatus(QuestionStatus.APPROVED);
        question.setCreatedByType(CreatedByType.INSTRUCTOR);
        question.setCreatedBy(userRepository.getReferenceById(user.getId()));
        return QuestionResponse.from(questionRepository.save(question));
    }

    /** Entry point for AI-generated questions (RAG phase): saved as PENDING_REVIEW for instructor review. */
    public QuestionResponse createGeneratedQuestion(Long topicId, QuestionRequest request) {
        Question question = newQuestion(findTopic(topicId), request);
        question.setStatus(QuestionStatus.PENDING_REVIEW);
        question.setCreatedByType(CreatedByType.AI);
        return QuestionResponse.from(questionRepository.save(question));
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listTopicQuestions(Long topicId, QuestionStatus status, UserPrincipal user) {
        Topic topic = findTopic(topicId);
        CourseAccess.requireManage(topic.getModule().getCourse(), user);
        List<Question> questions = status == null
                ? questionRepository.findByTopicId(topicId)
                : questionRepository.findByTopicIdAndStatus(topicId, status);
        return questions.stream().map(QuestionResponse::from).toList();
    }

    /** Review queue: pending questions in every course the caller manages. */
    @Transactional(readOnly = true)
    public List<QuestionResponse> listPendingReview(UserPrincipal user) {
        return questionRepository.findByStatus(QuestionStatus.PENDING_REVIEW).stream()
                .filter(q -> CourseAccess.canManage(q.getTopic().getModule().getCourse(), user))
                .map(QuestionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(Long questionId, UserPrincipal user) {
        return QuestionResponse.from(findManaged(questionId, user));
    }

    public QuestionResponse updateQuestion(Long questionId, QuestionRequest request, UserPrincipal user) {
        Question question = findManaged(questionId, user);
        question.setText(request.text().trim());
        question.setExplanation(request.explanation());
        if (request.difficulty() != null) {
            question.setDifficulty(request.difficulty());
        }
        question.getOptions().clear();
        addOptions(question, request.options());
        questionRepository.flush(); // options already used in attempts cannot be replaced -> 409
        return QuestionResponse.from(question);
    }

    public QuestionResponse approve(Long questionId, UserPrincipal user) {
        return review(questionId, QuestionStatus.APPROVED, user);
    }

    public QuestionResponse reject(Long questionId, UserPrincipal user) {
        return review(questionId, QuestionStatus.REJECTED, user);
    }

    public void deleteQuestion(Long questionId, UserPrincipal user) {
        Question question = findManaged(questionId, user);
        questionRepository.delete(question);
        questionRepository.flush(); // questions used in quizzes/attempts -> 409
    }

    // ------------------------------------------------------------------ helpers

    private QuestionResponse review(Long questionId, QuestionStatus target, UserPrincipal user) {
        Question question = findManaged(questionId, user);
        if (question.getStatus() == target) {
            throw new ConflictException("Question is already " + target);
        }
        question.setStatus(target);
        question.setReviewedBy(userRepository.getReferenceById(user.getId()));
        question.setReviewedAt(Instant.now());
        return QuestionResponse.from(question);
    }

    private Question findManaged(Long questionId, UserPrincipal user) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question", questionId));
        CourseAccess.requireManage(question.getTopic().getModule().getCourse(), user);
        return question;
    }

    private Topic findTopic(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", topicId));
    }

    private static Question newQuestion(Topic topic, QuestionRequest request) {
        Question question = new Question();
        question.setTopic(topic);
        question.setText(request.text().trim());
        question.setExplanation(request.explanation());
        question.setDifficulty(request.difficulty() != null ? request.difficulty() : Difficulty.MEDIUM);
        addOptions(question, request.options());
        return question;
    }

    private static void addOptions(Question question, List<OptionRequest> options) {
        for (int i = 0; i < options.size(); i++) {
            OptionRequest o = options.get(i);
            QuestionOption option = new QuestionOption();
            option.setQuestion(question);
            option.setText(o.text().trim());
            option.setCorrect(o.correct());
            option.setOrderIndex(i);
            question.getOptions().add(option);
        }
    }
}
