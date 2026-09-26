package com.lms.repository;

import java.util.List;
import java.util.Optional;

import com.lms.entity.StudentProgress;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProgressRepository extends JpaRepository<StudentProgress, Long> {

    Optional<StudentProgress> findByStudentIdAndCourseId(Long studentId, Long courseId);
    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);
    List<StudentProgress> findByStudentId(Long studentId);
    List<StudentProgress> findByCourseId(Long courseId);
    long countByCourseId(Long courseId);
}
