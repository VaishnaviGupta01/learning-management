package com.lms.repository;

import java.util.List;

import com.lms.entity.Recommendation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByStudentIdAndDismissedFalseOrderByScoreDesc(Long studentId);
    List<Recommendation> findByStudentIdAndTopicId(Long studentId, Long topicId);
    void deleteByStudentIdAndViewedFalse(Long studentId);
}
