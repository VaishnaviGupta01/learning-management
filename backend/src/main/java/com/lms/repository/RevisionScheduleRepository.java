package com.lms.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.lms.entity.RevisionSchedule;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RevisionScheduleRepository extends JpaRepository<RevisionSchedule, Long> {

    Optional<RevisionSchedule> findByStudentIdAndTopicId(Long studentId, Long topicId);
    List<RevisionSchedule> findByStudentIdAndActiveTrueOrderByNextReviewAtAsc(Long studentId);
    List<RevisionSchedule> findByStudentIdAndActiveTrueAndNextReviewAtBefore(Long studentId, Instant before);
    List<RevisionSchedule> findByActiveTrueAndNextReviewAtBefore(Instant before);
}
