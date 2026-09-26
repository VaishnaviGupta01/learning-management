package com.lms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.student.CourseProgressResponse;
import com.lms.dto.student.CourseProgressResponse.TopicProgress;
import com.lms.entity.Course;
import com.lms.entity.QuizAttempt;
import com.lms.entity.StudentProgress;
import com.lms.entity.StudySession;
import com.lms.entity.Topic;
import com.lms.entity.enums.QuestionStatus;
import com.lms.repository.QuestionAttemptRepository;
import com.lms.repository.QuestionRepository;
import com.lms.repository.StudentProgressRepository;
import com.lms.repository.StudySessionRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.TopicStatsView;

@Service
@Transactional
public class ProgressService {

    /** Caps a single logged session so an attempt left open for days does not inflate study time. */
    static final int MAX_SESSION_MINUTES = 180;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StudentProgressRepository progressRepository;
    private final StudySessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAttemptRepository questionAttemptRepository;
    private final TopicRepository topicRepository;

    public ProgressService(StudentProgressRepository progressRepository, StudySessionRepository sessionRepository,
                           QuestionRepository questionRepository, QuestionAttemptRepository questionAttemptRepository,
                           TopicRepository topicRepository) {
        this.progressRepository = progressRepository;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.questionAttemptRepository = questionAttemptRepository;
        this.topicRepository = topicRepository;
    }

    /**
     * Called from {@code QuizService.submitAttempt} once the answers are graded and flushed:
     * upserts the course's {@link StudentProgress} (the first submission enrols the student) and
     * logs the attempt as a {@link StudySession}.
     */
    public void updateAfterQuiz(QuizAttempt attempt) {
        Course course = attempt.getQuiz().getCourse();
        Long studentId = attempt.getStudent().getId();

        StudentProgress progress = progressRepository.findByStudentIdAndCourseId(studentId, course.getId())
                .orElseGet(() -> {
                    StudentProgress p = new StudentProgress();
                    p.setStudent(attempt.getStudent());
                    p.setCourse(course);
                    p.setEnrolledAt(attempt.getStartedAt());
                    return p;
                });

        Completion completion = completion(studentId, course.getId(), topicIds(course.getId()));
        progress.setCompletionPercentage(completion.percentage());
        progress.setTopicsCompleted(completion.topicsCompleted());

        int minutes = sessionMinutes(attempt.getStartedAt(), attempt.getSubmittedAt());
        StudySession session = new StudySession();
        session.setStudent(attempt.getStudent());
        session.setCourse(course);
        session.setTopic(attempt.getQuiz().getTopic());
        session.setStartedAt(attempt.getStartedAt());
        session.setEndedAt(attempt.getSubmittedAt());
        session.setDurationMinutes(minutes);
        sessionRepository.save(session);

        progress.setTotalStudyMinutes(progress.getTotalStudyMinutes() + minutes);
        progress.setLastAccessedAt(attempt.getSubmittedAt());
        progressRepository.save(progress);
    }

    /** Every course the student has progress in, with per-topic completion and accuracy computed live. */
    @Transactional(readOnly = true)
    public List<CourseProgressResponse> progressFor(Long studentId) {
        return progressRepository.findByStudentId(studentId).stream()
                .map(p -> toResponse(studentId, p))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private CourseProgressResponse toResponse(Long studentId, StudentProgress p) {
        Course course = p.getCourse();
        List<Topic> topics = topicRepository.findByModuleCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(course.getId());
        Map<Long, Long> approved = counts(questionRepository.countByCourseGroupedByTopic(course.getId(), QuestionStatus.APPROVED));
        Map<Long, Long> mastered = counts(questionAttemptRepository.countDistinctCorrectApprovedByTopic(studentId, course.getId()));
        Map<Long, TopicStatsView.Accuracy> accuracy = questionAttemptRepository.accuracyByTopic(studentId, course.getId())
                .stream().collect(Collectors.toMap(TopicStatsView.Accuracy::getTopicId, a -> a));

        List<TopicProgress> topicProgress = topics.stream().map(t -> {
            long total = approved.getOrDefault(t.getId(), 0L);
            long done = Math.min(mastered.getOrDefault(t.getId(), 0L), total);
            TopicStatsView.Accuracy acc = accuracy.get(t.getId());
            long given = acc == null ? 0 : acc.getTotal();
            long correct = acc == null || acc.getCorrect() == null ? 0 : acc.getCorrect();
            return new TopicProgress(t.getId(), t.getTitle(), total, done, percent(done, total),
                    given, correct, given == 0 ? null : percent(correct, given));
        }).toList();

        Completion completion = completion(approved, mastered, topics.stream().map(Topic::getId).toList());
        return new CourseProgressResponse(course.getId(), course.getCode(), course.getTitle(),
                completion.percentage(), completion.topicsCompleted(), topics.size(), p.getTotalStudyMinutes(),
                p.getEnrolledAt(), p.getLastAccessedAt(), topicProgress);
    }

    private Completion completion(Long studentId, Long courseId, List<Long> topicIds) {
        return completion(counts(questionRepository.countByCourseGroupedByTopic(courseId, QuestionStatus.APPROVED)),
                counts(questionAttemptRepository.countDistinctCorrectApprovedByTopic(studentId, courseId)),
                topicIds);
    }

    /** Course-wide ratio of mastered to approved questions; a topic is completed when all its approved questions are mastered. */
    private static Completion completion(Map<Long, Long> approved, Map<Long, Long> mastered, List<Long> topicIds) {
        long total = 0;
        long done = 0;
        int topicsCompleted = 0;
        for (Long topicId : topicIds) {
            long t = approved.getOrDefault(topicId, 0L);
            long d = Math.min(mastered.getOrDefault(topicId, 0L), t);
            total += t;
            done += d;
            if (t > 0 && d == t) {
                topicsCompleted++;
            }
        }
        return new Completion(percent(done, total), topicsCompleted);
    }

    private List<Long> topicIds(Long courseId) {
        return topicRepository.findByModuleCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(courseId).stream()
                .map(Topic::getId).toList();
    }

    private static Map<Long, Long> counts(List<TopicStatsView.Count> rows) {
        return rows.stream().collect(Collectors.toMap(TopicStatsView.Count::getTopicId, TopicStatsView.Count::getTotal));
    }

    static BigDecimal percent(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    static int sessionMinutes(Instant start, Instant end) {
        long seconds = Math.max(0, Duration.between(start, end).getSeconds());
        int minutes = (int) Math.max(1, (seconds + 59) / 60);
        return Math.min(minutes, MAX_SESSION_MINUTES);
    }

    private record Completion(BigDecimal percentage, int topicsCompleted) {
    }
}
