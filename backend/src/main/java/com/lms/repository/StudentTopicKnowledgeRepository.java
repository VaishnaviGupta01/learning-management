package com.lms.repository;

import java.util.List;
import java.util.Optional;

import com.lms.entity.StudentTopicKnowledge;
import com.lms.entity.enums.TopicClassification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentTopicKnowledgeRepository extends JpaRepository<StudentTopicKnowledge, Long> {

    Optional<StudentTopicKnowledge> findByStudentIdAndTopicId(Long studentId, Long topicId);
    List<StudentTopicKnowledge> findByStudentId(Long studentId);
    List<StudentTopicKnowledge> findByStudentIdAndClassification(Long studentId, TopicClassification classification);
    List<StudentTopicKnowledge> findByStudentIdAndTopicModuleCourseId(Long studentId, Long courseId);
    List<StudentTopicKnowledge> findByTopicId(Long topicId);
    List<StudentTopicKnowledge> findByTopicModuleCourseId(Long courseId);
    List<StudentTopicKnowledge> findByTopicModuleCourseIdAndClassification(Long courseId, TopicClassification classification);
}
