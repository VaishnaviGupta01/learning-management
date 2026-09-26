package com.lms.repository;

import java.util.List;
import java.util.Optional;

import com.lms.entity.Course;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCode(String code);
    boolean existsByCode(String code);
    List<Course> findByInstructorId(Long instructorId);
    List<Course> findByPublishedTrue();
    List<Course> findByTitleContainingIgnoreCase(String title);
}
