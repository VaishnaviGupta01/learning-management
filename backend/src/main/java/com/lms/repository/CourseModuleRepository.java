package com.lms.repository;

import java.util.List;

import com.lms.entity.CourseModule;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    List<CourseModule> findByCourseIdOrderByOrderIndexAsc(Long courseId);
    long countByCourseId(Long courseId);
}
