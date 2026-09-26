package com.lms.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.quiz.AttemptStartResponse;
import com.lms.dto.quiz.DiagnosticRequest;
import com.lms.entity.Course;
import com.lms.entity.Question;
import com.lms.entity.Quiz;
import com.lms.entity.Topic;
import com.lms.entity.enums.CreatedByType;
import com.lms.entity.enums.QuestionStatus;
import com.lms.entity.enums.QuizType;
import com.lms.exception.BadRequestException;
import com.lms.repository.QuestionRepository;
import com.lms.repository.QuizRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.UserRepository;
import com.lms.security.UserPrincipal;

/**
 * Builds a personal multi-topic DIAGNOSTIC quiz for a student (up to {@value #QUESTIONS_PER_TOPIC}
 * random approved questions per topic) and immediately starts an attempt on it.
 */
@Service
@Transactional
public class DiagnosticService {

    static final int QUESTIONS_PER_TOPIC = 5;

    private final CourseService courseService;
    private final QuizService quizService;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;

    public DiagnosticService(CourseService courseService, QuizService quizService, TopicRepository topicRepository,
                             QuestionRepository questionRepository, QuizRepository quizRepository,
                             UserRepository userRepository) {
        this.courseService = courseService;
        this.quizService = quizService;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
    }

    public AttemptStartResponse startDiagnostic(Long courseId, DiagnosticRequest request, UserPrincipal user) {
        Course course = courseService.findCourse(courseId);
        CourseAccess.requireView(course, user);

        List<Topic> topics = selectTopics(courseId, request);

        Quiz quiz = new Quiz();
        quiz.setCourse(course);
        quiz.setTitle("Diagnostic: " + course.getTitle());
        quiz.setDescription("Auto-generated diagnostic covering " + topics.size() + " topic(s)");
        quiz.setType(QuizType.DIAGNOSTIC);
        quiz.setCreatedByType(CreatedByType.AI); // system-generated, not instructor-authored
        quiz.setStudent(userRepository.getReferenceById(user.getId()));
        quiz.setPublished(false);

        for (Topic topic : topics) {
            List<Question> approved = new ArrayList<>(
                    questionRepository.findByTopicIdAndStatus(topic.getId(), QuestionStatus.APPROVED));
            Collections.shuffle(approved, ThreadLocalRandom.current());
            approved.stream().limit(QUESTIONS_PER_TOPIC).forEach(q -> QuizService.addQuestion(quiz, q));
        }
        if (quiz.getQuizQuestions().isEmpty()) {
            throw new BadRequestException("No approved questions are available for the selected topics");
        }

        quizRepository.save(quiz);
        return quizService.startAttempt(quiz.getId(), user);
    }

    private List<Topic> selectTopics(Long courseId, DiagnosticRequest request) {
        List<Topic> all = topicRepository.findByModuleCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(courseId);
        if (request == null || request.topicIds() == null || request.topicIds().isEmpty()) {
            return all;
        }
        Set<Long> wanted = new HashSet<>(request.topicIds());
        List<Topic> selected = all.stream().filter(t -> wanted.contains(t.getId())).toList();
        if (selected.size() != wanted.size()) {
            throw new BadRequestException("Some topicIds do not belong to this course");
        }
        return selected;
    }
}
