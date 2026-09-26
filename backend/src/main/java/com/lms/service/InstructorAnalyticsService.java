package com.lms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.analytics.InstructorAnalyticsResponse;
import com.lms.dto.analytics.InstructorAnalyticsResponse.TopicPerformance;
import com.lms.entity.Course;
import com.lms.entity.StudentProgress;
import com.lms.entity.StudentTopicKnowledge;
import com.lms.entity.Topic;
import com.lms.entity.enums.QuestionStatus;
import com.lms.entity.enums.TopicClassification;
import com.lms.repository.DocumentRepository;
import com.lms.repository.QuestionAttemptRepository;
import com.lms.repository.QuestionRepository;
import com.lms.repository.QuizAttemptRepository;
import com.lms.repository.StudentProgressRepository;
import com.lms.repository.StudentTopicKnowledgeRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.TopicStatsView;
import com.lms.security.UserPrincipal;

@Service
@Transactional(readOnly = true)
public class InstructorAnalyticsService {

    private final CourseService courseService;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAttemptRepository questionAttemptRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final StudentProgressRepository progressRepository;
    private final StudentTopicKnowledgeRepository knowledgeRepository;
    private final DocumentRepository documentRepository;
    private final RecommendationEvaluationService evaluationService;

    public InstructorAnalyticsService(CourseService courseService, TopicRepository topicRepository,
                                      QuestionRepository questionRepository,
                                      QuestionAttemptRepository questionAttemptRepository,
                                      QuizAttemptRepository quizAttemptRepository,
                                      StudentProgressRepository progressRepository,
                                      StudentTopicKnowledgeRepository knowledgeRepository,
                                      DocumentRepository documentRepository,
                                      RecommendationEvaluationService evaluationService) {
        this.courseService = courseService;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.questionAttemptRepository = questionAttemptRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.progressRepository = progressRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.documentRepository = documentRepository;
        this.evaluationService = evaluationService;
    }

    public InstructorAnalyticsResponse analytics(Long courseId, int k, UserPrincipal user) {
        Course course = courseService.findCourse(courseId);
        CourseAccess.requireManage(course, user);

        List<Topic> topics = topicRepository.findByModuleCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(courseId);
        Map<Long, Long> approved = questionRepository.countByCourseGroupedByTopic(courseId, QuestionStatus.APPROVED)
                .stream().collect(Collectors.toMap(TopicStatsView.Count::getTopicId, TopicStatsView.Count::getTotal));
        Map<Long, TopicStatsView.Accuracy> accuracy = questionAttemptRepository.courseAccuracyByTopic(courseId)
                .stream().collect(Collectors.toMap(TopicStatsView.Accuracy::getTopicId, a -> a));
        Map<Long, Long> students = questionAttemptRepository.studentsByTopic(courseId)
                .stream().collect(Collectors.toMap(TopicStatsView.Count::getTopicId, TopicStatsView.Count::getTotal));
        List<StudentTopicKnowledge> knowledge = knowledgeRepository.findByTopicModuleCourseId(courseId);
        Map<Long, List<StudentTopicKnowledge>> knowledgeByTopic = knowledge.stream()
                .collect(Collectors.groupingBy(k2 -> k2.getTopic().getId()));

        List<TopicPerformance> performance = topics.stream().map(t -> {
            TopicStatsView.Accuracy a = accuracy.get(t.getId());
            long answers = a == null ? 0 : a.getTotal();
            long correct = a == null || a.getCorrect() == null ? 0 : a.getCorrect();
            List<StudentTopicKnowledge> ks = knowledgeByTopic.getOrDefault(t.getId(), List.of());
            long struggling = ks.stream().filter(x -> x.getClassification() == TopicClassification.WEAK
                    || x.getClassification() == TopicClassification.NEEDS_PRACTICE).count();
            Double mastery = ks.isEmpty() ? null
                    : Math.round(ks.stream().mapToDouble(StudentTopicKnowledge::getMasteryScore).average().orElse(0) * 10000) / 10000.0;
            return new TopicPerformance(t.getId(), t.getTitle(), approved.getOrDefault(t.getId(), 0L), answers, correct,
                    answers == 0 ? null : ProgressService.percent(correct, answers),
                    students.getOrDefault(t.getId(), 0L), struggling, mastery);
        }).toList();

        Map<TopicClassification, Long> distribution = new EnumMap<>(TopicClassification.class);
        for (TopicClassification c : TopicClassification.values()) {
            distribution.put(c, 0L);
        }
        knowledge.forEach(x -> distribution.merge(x.getClassification(), 1L, Long::sum));

        List<StudentProgress> progress = progressRepository.findByCourseId(courseId);
        BigDecimal avgCompletion = progress.isEmpty() ? null : progress.stream()
                .map(StudentProgress::getCompletionPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(progress.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgScore = quizAttemptRepository.averagePercentageByCourse(courseId);

        return new InstructorAnalyticsResponse(course.getId(), course.getTitle(), progress.size(), avgCompletion,
                quizAttemptRepository.countSubmittedByCourse(courseId),
                avgScore == null ? null : avgScore.setScale(2, RoundingMode.HALF_UP),
                questionRepository.countByTopicModuleCourseIdAndStatus(courseId, QuestionStatus.PENDING_REVIEW),
                documentRepository.findByCourseId(courseId).size(),
                distribution, performance, evaluationService.evaluate(courseId, k));
    }
}
