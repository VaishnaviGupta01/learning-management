package com.lms.repository;

import java.util.List;

import com.lms.entity.QuizQuestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByQuizIdOrderByOrderIndexAsc(Long quizId);
    boolean existsByQuizIdAndQuestionId(Long quizId, Long questionId);
    long countByQuizId(Long quizId);
}
