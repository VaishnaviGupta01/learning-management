package com.lms.repository;

import java.util.Collection;
import java.util.List;

import com.lms.entity.LearningResource;
import com.lms.entity.enums.Difficulty;
import com.lms.entity.enums.ResourceType;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {

    List<LearningResource> findByTopicIdOrderByOrderIndexAsc(Long topicId);
    List<LearningResource> findByTopicIdAndType(Long topicId, ResourceType type);
    List<LearningResource> findByTopicIdAndDifficulty(Long topicId, Difficulty difficulty);
    List<LearningResource> findByTopicIdIn(Collection<Long> topicIds);
}
