package com.lms.repository;

import java.util.List;
import java.util.Optional;

import com.lms.entity.QuestionOption;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    List<QuestionOption> findByQuestionIdOrderByOrderIndexAsc(Long questionId);
    Optional<QuestionOption> findFirstByQuestionIdAndCorrectTrue(Long questionId);
}
