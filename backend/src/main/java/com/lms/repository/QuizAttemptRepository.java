package com.lms.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.lms.entity.QuizAttempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findByStudentIdOrderByStartedAtDesc(Long studentId);
    List<QuizAttempt> findByQuizId(Long quizId);
    List<QuizAttempt> findByQuizIdAndStudentId(Long quizId, Long studentId);
    Optional<QuizAttempt> findFirstByQuizIdAndStudentIdAndSubmittedAtIsNull(Long quizId, Long studentId);
    long countByStudentIdAndSubmittedAtIsNotNull(Long studentId);

    @Query("select avg(a.percentage) from QuizAttempt a where a.student.id = :studentId and a.submittedAt is not null")
    BigDecimal averagePercentageByStudent(@Param("studentId") Long studentId);

    @Query("select avg(a.percentage) from QuizAttempt a where a.quiz.course.id = :courseId and a.submittedAt is not null")
    BigDecimal averagePercentageByCourse(@Param("courseId") Long courseId);

    long countBySubmittedAtIsNotNull();
    long countBySubmittedAtAfter(Instant after);

    @Query("select count(a) from QuizAttempt a where a.quiz.course.id = :courseId and a.submittedAt is not null")
    long countSubmittedByCourse(@Param("courseId") Long courseId);
}
