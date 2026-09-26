package com.lms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.question.QuestionResponse;
import com.lms.dto.quiz.AttemptResultResponse;
import com.lms.dto.quiz.AttemptResultResponse.QuestionResult;
import com.lms.dto.quiz.AttemptStartResponse;
import com.lms.dto.quiz.AttemptStartResponse.AttemptOption;
import com.lms.dto.quiz.AttemptStartResponse.AttemptQuestion;
import com.lms.dto.quiz.AttemptSummaryResponse;
import com.lms.dto.quiz.QuizRequest;
import com.lms.dto.quiz.QuizResponse;
import com.lms.dto.quiz.SubmitAttemptRequest;
import com.lms.entity.Course;
import com.lms.entity.Question;
import com.lms.entity.QuestionAttempt;
import com.lms.entity.QuestionOption;
import com.lms.entity.Quiz;
import com.lms.entity.QuizAttempt;
import com.lms.entity.QuizQuestion;
import com.lms.entity.Topic;
import com.lms.entity.enums.CreatedByType;
import com.lms.entity.enums.QuestionStatus;
import com.lms.entity.enums.QuizType;
import com.lms.exception.BadRequestException;
import com.lms.exception.ConflictException;
import com.lms.exception.ResourceNotFoundException;
import com.lms.repository.QuestionRepository;
import com.lms.repository.QuizAttemptRepository;
import com.lms.repository.QuizRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.UserRepository;
import com.lms.security.UserPrincipal;

@Service
@Transactional
public class QuizService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final CourseService courseService;

    public QuizService(QuizRepository quizRepository, QuizAttemptRepository attemptRepository,
                       QuestionRepository questionRepository, TopicRepository topicRepository,
                       UserRepository userRepository, CourseService courseService) {
        this.quizRepository = quizRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
    }

    // ------------------------------------------------------------------ quiz management

    public QuizResponse createQuiz(Long courseId, QuizRequest request, UserPrincipal user) {
        Course course = courseService.findCourse(courseId);
        CourseAccess.requireManage(course, user);

        Quiz quiz = new Quiz();
        quiz.setCourse(course);
        quiz.setTitle(request.title().trim());
        quiz.setDescription(request.description());
        quiz.setType(request.type() != null ? request.type() : QuizType.PRACTICE);
        quiz.setTimeLimitMinutes(request.timeLimitMinutes());
        quiz.setPassingScore(request.passingScore());
        quiz.setCreatedByType(CreatedByType.INSTRUCTOR);
        quiz.setCreatedBy(userRepository.getReferenceById(user.getId()));
        quiz.setPublished(Boolean.TRUE.equals(request.published()));

        if (request.topicId() != null) {
            Topic topic = topicRepository.findById(request.topicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", request.topicId()));
            if (!topic.getModule().getCourse().getId().equals(courseId)) {
                throw new BadRequestException("Topic " + topic.getId() + " does not belong to this course");
            }
            quiz.setTopic(topic);
        }

        Set<Long> seen = new HashSet<>();
        for (Long questionId : request.questionIds()) {
            if (!seen.add(questionId)) {
                throw new BadRequestException("Question " + questionId + " is listed more than once");
            }
            Question question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Question", questionId));
            if (!question.getTopic().getModule().getCourse().getId().equals(courseId)) {
                throw new BadRequestException("Question " + questionId + " does not belong to this course");
            }
            if (question.getStatus() != QuestionStatus.APPROVED) {
                throw new BadRequestException("Question " + questionId + " is not approved");
            }
            addQuestion(quiz, question);
        }
        return toResponse(quizRepository.save(quiz), true);
    }

    /** Managers see all shared quizzes of the course; students only published ones. Personal quizzes are excluded. */
    @Transactional(readOnly = true)
    public List<QuizResponse> listQuizzes(Long courseId, UserPrincipal user) {
        Course course = courseService.findCourse(courseId);
        CourseAccess.requireView(course, user);
        boolean manager = CourseAccess.canManage(course, user);
        List<Quiz> quizzes = manager
                ? quizRepository.findByCourseIdAndStudentIsNull(courseId)
                : quizRepository.findByCourseIdAndPublishedTrueAndStudentIsNull(courseId);
        return quizzes.stream().map(q -> toResponse(q, false)).toList();
    }

    @Transactional(readOnly = true)
    public QuizResponse getQuiz(Long quizId, UserPrincipal user) {
        Quiz quiz = findQuiz(quizId);
        boolean manager = CourseAccess.canManage(quiz.getCourse(), user);
        if (!manager && !canTake(quiz, user)) {
            throw new ResourceNotFoundException("Quiz", quizId);
        }
        return toResponse(quiz, manager);
    }

    public QuizResponse setPublished(Long quizId, boolean published, UserPrincipal user) {
        Quiz quiz = findQuiz(quizId);
        CourseAccess.requireManage(quiz.getCourse(), user);
        if (quiz.getStudent() != null) {
            throw new BadRequestException("Personal quizzes cannot be published");
        }
        quiz.setPublished(published);
        return toResponse(quiz, true);
    }

    public void deleteQuiz(Long quizId, UserPrincipal user) {
        Quiz quiz = findQuiz(quizId);
        CourseAccess.requireManage(quiz.getCourse(), user);
        quizRepository.delete(quiz);
        quizRepository.flush(); // quizzes with attempts -> 409
    }

    // ------------------------------------------------------------------ attempts

    /**
     * Starts (or resumes) the caller's attempt. The response contains no correctness flags and
     * no explanations.
     */
    public AttemptStartResponse startAttempt(Long quizId, UserPrincipal user) {
        Quiz quiz = findQuiz(quizId);
        if (!canTake(quiz, user)) {
            throw new ResourceNotFoundException("Quiz", quizId);
        }
        QuizAttempt attempt = attemptRepository
                .findFirstByQuizIdAndStudentIdAndSubmittedAtIsNull(quizId, user.getId())
                .orElseGet(() -> {
                    QuizAttempt a = new QuizAttempt();
                    a.setQuiz(quiz);
                    a.setStudent(userRepository.getReferenceById(user.getId()));
                    a.setStartedAt(Instant.now());
                    return attemptRepository.save(a);
                });

        List<AttemptQuestion> questions = quiz.getQuizQuestions().stream()
                .map(qq -> {
                    Question q = qq.getQuestion();
                    List<AttemptOption> options = q.getOptions().stream()
                            .map(o -> new AttemptOption(o.getId(), o.getText()))
                            .toList();
                    return new AttemptQuestion(q.getId(), q.getTopic().getId(), q.getText(), q.getDifficulty(),
                            qq.getPoints(), options);
                })
                .toList();
        return new AttemptStartResponse(attempt.getId(), quiz.getId(), quiz.getTitle(), quiz.getType(),
                quiz.getTimeLimitMinutes(), attempt.getStartedAt(), questions);
    }

    /**
     * Grades an attempt. Only question ids and selected option ids are read from the client;
     * correctness comes exclusively from {@link QuestionOption#isCorrect()} in the database.
     */
    public AttemptResultResponse submitAttempt(Long attemptId, SubmitAttemptRequest request, UserPrincipal user) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt", attemptId));
        if (!attempt.getStudent().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your attempt");
        }
        if (attempt.getSubmittedAt() != null) {
            throw new ConflictException("Attempt already submitted");
        }

        Quiz quiz = attempt.getQuiz();
        Map<Long, QuizQuestion> quizQuestions = quiz.getQuizQuestions().stream()
                .collect(Collectors.toMap(qq -> qq.getQuestion().getId(), Function.identity()));

        Map<Long, SubmitAttemptRequest.Answer> answers = new HashMap<>();
        for (SubmitAttemptRequest.Answer a : request.answers()) {
            if (!quizQuestions.containsKey(a.questionId())) {
                throw new BadRequestException("Question " + a.questionId() + " is not part of this quiz");
            }
            if (answers.put(a.questionId(), a) != null) {
                throw new BadRequestException("Question " + a.questionId() + " answered more than once");
            }
        }

        Instant now = Instant.now();
        BigDecimal score = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
        for (QuizQuestion qq : quiz.getQuizQuestions()) {
            Question question = qq.getQuestion();
            SubmitAttemptRequest.Answer answer = answers.get(question.getId());
            QuestionOption selected = null;
            if (answer != null && answer.selectedOptionId() != null) {
                selected = question.getOptions().stream()
                        .filter(o -> o.getId().equals(answer.selectedOptionId()))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("Option " + answer.selectedOptionId()
                                + " does not belong to question " + question.getId()));
            }
            boolean correct = selected != null && selected.isCorrect();

            QuestionAttempt qa = new QuestionAttempt();
            qa.setQuizAttempt(attempt);
            qa.setQuestion(question);
            qa.setSelectedOption(selected);
            qa.setCorrect(correct);
            qa.setTimeSpentSeconds(answer == null ? null : answer.timeSpentSeconds());
            qa.setAnsweredAt(answer == null ? null : now);
            attempt.getQuestionAttempts().add(qa);

            maxScore = maxScore.add(qq.getPoints());
            if (correct) {
                score = score.add(qq.getPoints());
            }
        }

        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setPercentage(maxScore.signum() == 0 ? BigDecimal.ZERO
                : score.multiply(HUNDRED).divide(maxScore, 2, RoundingMode.HALF_UP));
        attempt.setSubmittedAt(now);
        attemptRepository.flush();
        return toResult(attempt);
    }

    /** Result of a submitted attempt; visible to its student and to the course's managers. */
    @Transactional(readOnly = true)
    public AttemptResultResponse getAttemptResult(Long attemptId, UserPrincipal user) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt", attemptId));
        boolean owner = attempt.getStudent().getId().equals(user.getId());
        if (!owner && !CourseAccess.canManage(attempt.getQuiz().getCourse(), user)) {
            throw new ResourceNotFoundException("Attempt", attemptId);
        }
        if (attempt.getSubmittedAt() == null) {
            throw new ConflictException("Attempt has not been submitted yet");
        }
        return toResult(attempt);
    }

    @Transactional(readOnly = true)
    public List<AttemptSummaryResponse> myAttempts(UserPrincipal user) {
        return attemptRepository.findByStudentIdOrderByStartedAtDesc(user.getId()).stream()
                .map(AttemptSummaryResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    static void addQuestion(Quiz quiz, Question question) {
        QuizQuestion qq = new QuizQuestion();
        qq.setQuiz(quiz);
        qq.setQuestion(question);
        qq.setOrderIndex(quiz.getQuizQuestions().size());
        qq.setPoints(BigDecimal.ONE);
        quiz.getQuizQuestions().add(qq);
    }

    /** Personal quizzes: only their student. Shared quizzes: published quiz in a published course. */
    private static boolean canTake(Quiz quiz, UserPrincipal user) {
        if (quiz.getStudent() != null) {
            return quiz.getStudent().getId().equals(user.getId());
        }
        return quiz.isPublished() && quiz.getCourse().isPublished();
    }

    private Quiz findQuiz(Long quizId) {
        return quizRepository.findById(quizId).orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
    }

    private static QuizResponse toResponse(Quiz q, boolean includeQuestions) {
        List<QuestionResponse> questions = includeQuestions
                ? q.getQuizQuestions().stream().map(qq -> QuestionResponse.from(qq.getQuestion())).toList()
                : null;
        return new QuizResponse(q.getId(), q.getCourse().getId(), q.getTopic() == null ? null : q.getTopic().getId(),
                q.getTitle(), q.getDescription(), q.getType(), q.getTimeLimitMinutes(), q.getPassingScore(),
                q.getCreatedByType(), q.isPublished(), q.getStudent() == null ? null : q.getStudent().getId(),
                q.getQuizQuestions().size(), q.getCreatedAt(), questions);
    }

    private static AttemptResultResponse toResult(QuizAttempt attempt) {
        Quiz quiz = attempt.getQuiz();
        Map<Long, QuestionAttempt> byQuestion = attempt.getQuestionAttempts().stream()
                .collect(Collectors.toMap(qa -> qa.getQuestion().getId(), Function.identity()));

        List<QuestionResult> results = quiz.getQuizQuestions().stream()
                .map(qq -> {
                    Question q = qq.getQuestion();
                    QuestionAttempt qa = byQuestion.get(q.getId());
                    Long correctOptionId = q.getOptions().stream()
                            .filter(QuestionOption::isCorrect).map(QuestionOption::getId).findFirst().orElse(null);
                    return new QuestionResult(q.getId(), q.getTopic().getId(),
                            qa == null || qa.getSelectedOption() == null ? null : qa.getSelectedOption().getId(),
                            correctOptionId, qa != null && qa.isCorrect(), qq.getPoints(), q.getExplanation());
                })
                .toList();

        Boolean passed = quiz.getPassingScore() == null || attempt.getPercentage() == null
                ? null
                : attempt.getPercentage().compareTo(quiz.getPassingScore()) >= 0;
        return new AttemptResultResponse(attempt.getId(), quiz.getId(), quiz.getTitle(), attempt.getStudent().getId(),
                attempt.getStartedAt(), attempt.getSubmittedAt(), attempt.getScore(), attempt.getMaxScore(),
                attempt.getPercentage(), passed, results);
    }
}
