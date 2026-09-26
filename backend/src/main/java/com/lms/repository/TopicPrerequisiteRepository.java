package com.lms.repository;

import java.util.List;

import com.lms.entity.TopicPrerequisite;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicPrerequisiteRepository extends JpaRepository<TopicPrerequisite, Long> {

    List<TopicPrerequisite> findByTopicId(Long topicId);
    List<TopicPrerequisite> findByPrerequisiteTopicId(Long prerequisiteTopicId);
    boolean existsByTopicIdAndPrerequisiteTopicId(Long topicId, Long prerequisiteTopicId);
    void deleteByTopicIdAndPrerequisiteTopicId(Long topicId, Long prerequisiteTopicId);
    List<TopicPrerequisite> findByTopicModuleCourseId(Long courseId);
    void deleteByTopicIdOrPrerequisiteTopicId(Long topicId, Long prerequisiteTopicId);
}
