package com.lms.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.student.TopicKnowledgeResponse;
import com.lms.entity.QuestionAttempt;
import com.lms.entity.RevisionSchedule;
import com.lms.entity.StudentTopicKnowledge;
import com.lms.entity.enums.QuizType;
import com.lms.entity.enums.RevisionStage;
import com.lms.entity.enums.TopicClassification;
import com.lms.ml.MlClient;
import com.lms.ml.MlClient.KnowledgeScore;
import com.lms.ml.MlClient.TopicEvidence;
import com.lms.ml.MlServiceException;
import com.lms.repository.QuestionAttemptRepository;
import com.lms.repository.RevisionScheduleRepository;
import com.lms.repository.StudentTopicKnowledgeRepository;
import com.lms.repository.TopicRepository;
import com.lms.repository.UserRepository;

/**
 * Knowledge model (Phase 7). For each topic it derives four accuracy ratios from the student's history,
 * sends them to ml-service for the weighted score and band, and upserts {@link StudentTopicKnowledge}.
 *
 * <ul>
 *   <li>diagnostic - accuracy on the topic in the most recent submitted DIAGNOSTIC attempt that covered it</li>
 *   <li>recent_quiz - accuracy over the last {@value #RECENT_WINDOW} answers from ASSESSMENT / ADAPTIVE / REVISION quizzes</li>
 *   <li>practice - accuracy over all answers from PRACTICE quizzes</li>
 *   <li>revision - spaced-repetition stage progress from RevisionSchedule (DAY_1 = 0 ... MASTERED = 1)</li>
 * </ul>
 * A ratio with no underlying data is sent as null; ml-service renormalises the remaining weights.
 */
@Service
@Transactional
public class KnowledgeService {

    static final int RECENT_WINDOW = 10;

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final QuestionAttemptRepository questionAttemptRepository;
    private final RevisionScheduleRepository revisionRepository;
    private final StudentTopicKnowledgeRepository knowledgeRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final MlClient mlClient;

    public KnowledgeService(QuestionAttemptRepository questionAttemptRepository,
                            RevisionScheduleRepository revisionRepository,
                            StudentTopicKnowledgeRepository knowledgeRepository, TopicRepository topicRepository,
                            UserRepository userRepository, MlClient mlClient) {
        this.questionAttemptRepository = questionAttemptRepository;
        this.revisionRepository = revisionRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.mlClient = mlClient;
    }

    /**
     * Recomputes knowledge for the given topics. Attempt/correct counts are always updated; if
     * ml-service is unavailable the previous score and band are kept and a warning is logged, so a
     * quiz submission never fails because of the ML layer.
     */
    public void recalculate(Long studentId, Collection<Long> topicIds) {
        if (topicIds.isEmpty()) {
            return;
        }
        Map<Long, List<QuestionAttempt>> byTopic = new LinkedHashMap<>();
        topicIds.forEach(id -> byTopic.put(id, new ArrayList<>()));
        for (QuestionAttempt qa : questionAttemptRepository.findSubmittedByStudentAndTopics(studentId, topicIds)) {
            byTopic.get(qa.getQuestion().getTopic().getId()).add(qa); // already newest first
        }

        List<TopicEvidence> evidence = byTopic.entrySet().stream()
                .map(e -> evidence(studentId, e.getKey(), e.getValue()))
                .toList();

        Map<Long, KnowledgeScore> scores = Map.of();
        try {
            scores = mlClient.scoreKnowledge(evidence).stream()
                    .collect(Collectors.toMap(KnowledgeScore::topicId, s -> s));
        } catch (MlServiceException e) {
            log.warn("Knowledge scoring skipped for student {} (topics {}): {}", studentId, topicIds, e.getMessage());
        }

        Instant now = Instant.now();
        for (Map.Entry<Long, List<QuestionAttempt>> e : byTopic.entrySet()) {
            Long topicId = e.getKey();
            StudentTopicKnowledge k = knowledgeRepository.findByStudentIdAndTopicId(studentId, topicId)
                    .orElseGet(() -> {
                        StudentTopicKnowledge n = new StudentTopicKnowledge();
                        n.setStudent(userRepository.getReferenceById(studentId));
                        n.setTopic(topicRepository.getReferenceById(topicId));
                        return n;
                    });
            k.setAttemptsCount(e.getValue().size());
            k.setCorrectCount((int) e.getValue().stream().filter(QuestionAttempt::isCorrect).count());
            KnowledgeScore score = scores.get(topicId);
            if (score != null) {
                k.setMasteryScore(score.score());
                k.setClassification(TopicClassification.valueOf(score.classification()));
                k.setLastAssessedAt(now);
            }
            knowledgeRepository.save(k);
        }
    }

    @Transactional(readOnly = true)
    public List<TopicKnowledgeResponse> knowledgeFor(Long studentId, Long courseId) {
        List<StudentTopicKnowledge> rows = courseId == null
                ? knowledgeRepository.findByStudentId(studentId)
                : knowledgeRepository.findByStudentIdAndTopicModuleCourseId(studentId, courseId);
        return rows.stream().map(TopicKnowledgeResponse::from).toList();
    }

    // ------------------------------------------------------------------ evidence

    private TopicEvidence evidence(Long studentId, Long topicId, List<QuestionAttempt> answers) {
        return new TopicEvidence(topicId,
                diagnosticRatio(answers),
                accuracy(answers.stream().filter(ofType(QuizType.ASSESSMENT, QuizType.ADAPTIVE, QuizType.REVISION))
                        .limit(RECENT_WINDOW).toList()),
                accuracy(answers.stream().filter(ofType(QuizType.PRACTICE)).toList()),
                revisionRatio(revisionRepository.findByStudentIdAndTopicId(studentId, topicId).orElse(null)));
    }

    private static Double diagnosticRatio(List<QuestionAttempt> answersNewestFirst) {
        return answersNewestFirst.stream()
                .filter(ofType(QuizType.DIAGNOSTIC))
                .findFirst()
                .map(latest -> accuracy(answersNewestFirst.stream()
                        .filter(qa -> qa.getQuizAttempt().getId().equals(latest.getQuizAttempt().getId()))
                        .toList()))
                .orElse(null);
    }

    static Double revisionRatio(RevisionSchedule schedule) {
        if (schedule == null) {
            return null;
        }
        return schedule.getStage().ordinal() / (double) (RevisionStage.values().length - 1);
    }

    private static Double accuracy(List<QuestionAttempt> answers) {
        if (answers.isEmpty()) {
            return null;
        }
        return answers.stream().filter(QuestionAttempt::isCorrect).count() / (double) answers.size();
    }

    private static Predicate<QuestionAttempt> ofType(QuizType... types) {
        List<QuizType> allowed = List.of(types);
        return qa -> allowed.contains(qa.getQuizAttempt().getQuiz().getType());
    }
}
