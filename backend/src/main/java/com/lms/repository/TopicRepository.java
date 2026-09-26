package com.lms.repository;

import java.util.List;

import com.lms.entity.Topic;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByModuleIdOrderByOrderIndexAsc(Long moduleId);
    List<Topic> findByModuleCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(Long courseId);
    long countByModuleCourseId(Long courseId);
}
