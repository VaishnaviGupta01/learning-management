package com.lms.repository;

import java.util.List;

import com.lms.entity.Quiz;
import com.lms.entity.enums.QuizType;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByCourseId(Long courseId);
    List<Quiz> findByCourseIdAndPublishedTrue(Long courseId);
    List<Quiz> findByTopicId(Long topicId);
    List<Quiz> findByStudentIdAndType(Long studentId, QuizType type);
    List<Quiz> findByCreatedById(Long createdById);
}
