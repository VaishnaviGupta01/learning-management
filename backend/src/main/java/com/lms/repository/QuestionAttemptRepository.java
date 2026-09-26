package com.lms.repository;

import java.util.List;

import com.lms.entity.QuestionAttempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, Long> {

    List<QuestionAttempt> findByQuizAttemptId(Long quizAttemptId);

    @Query("select qa from QuestionAttempt qa where qa.quizAttempt.student.id = :studentId")
    List<QuestionAttempt> findByStudentId(@Param("studentId") Long studentId);

    @Query("select qa from QuestionAttempt qa where qa.quizAttempt.student.id = :studentId and qa.question.topic.id = :topicId")
    List<QuestionAttempt> findByStudentIdAndTopicId(@Param("studentId") Long studentId, @Param("topicId") Long topicId);

    @Query("select count(qa) from QuestionAttempt qa where qa.quizAttempt.student.id = :studentId and qa.question.topic.id = :topicId and qa.correct = true")
    long countCorrectByStudentIdAndTopicId(@Param("studentId") Long studentId, @Param("topicId") Long topicId);
}
