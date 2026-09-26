package com.lms.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.entity.enums.QuestionStatus;
import com.lms.entity.enums.RoleName;
import com.lms.repository.AiInteractionRepository;
import com.lms.repository.CourseRepository;
import com.lms.repository.DocumentChunkRepository;
import com.lms.repository.DocumentRepository;
import com.lms.repository.QuestionRepository;
import com.lms.repository.QuizAttemptRepository;
import com.lms.repository.QuizRepository;
import com.lms.repository.StudySessionRepository;
import com.lms.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class AdminStatsService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final StudySessionRepository sessionRepository;
    private final AiInteractionRepository aiRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    public AdminStatsService(UserRepository userRepository, CourseRepository courseRepository,
                             QuestionRepository questionRepository, QuizRepository quizRepository,
                             QuizAttemptRepository attemptRepository, StudySessionRepository sessionRepository,
                             AiInteractionRepository aiRepository, DocumentRepository documentRepository,
                             DocumentChunkRepository chunkRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.questionRepository = questionRepository;
        this.quizRepository = quizRepository;
        this.attemptRepository = attemptRepository;
        this.sessionRepository = sessionRepository;
        this.aiRepository = aiRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    public Stats stats() {
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        Map<RoleName, Long> byRole = new EnumMap<>(RoleName.class);
        for (RoleName role : RoleName.values()) {
            byRole.put(role, userRepository.countByRoleName(role));
        }
        return new Stats(userRepository.count(), byRole, sessionRepository.countActiveStudentsSince(weekAgo),
                courseRepository.count(), courseRepository.countByPublishedTrue(),
                questionRepository.countByStatus(QuestionStatus.APPROVED),
                questionRepository.countByStatus(QuestionStatus.PENDING_REVIEW),
                quizRepository.count(), attemptRepository.countBySubmittedAtIsNotNull(),
                attemptRepository.countBySubmittedAtAfter(weekAgo),
                aiRepository.count(), aiRepository.countByCreatedAtAfter(weekAgo),
                documentRepository.count(), chunkRepository.count());
    }

    public record Stats(
            long totalUsers,
            Map<RoleName, Long> usersByRole,
            long activeStudentsLast7Days,
            long courses,
            long publishedCourses,
            long approvedQuestions,
            long pendingQuestions,
            long quizzes,
            long submittedAttempts,
            long attemptsLast7Days,
            long aiInteractions,
            long aiInteractionsLast7Days,
            long documents,
            long documentChunks) {
    }
}
