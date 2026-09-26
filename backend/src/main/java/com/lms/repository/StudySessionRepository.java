package com.lms.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.lms.entity.StudySession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    List<StudySession> findByStudentIdOrderByStartedAtDesc(Long studentId);
    List<StudySession> findByStudentIdAndStartedAtBetween(Long studentId, Instant from, Instant to);
    Optional<StudySession> findFirstByStudentIdAndEndedAtIsNull(Long studentId);

    @Query("select coalesce(sum(s.durationMinutes), 0) from StudySession s where s.student.id = :studentId and s.course.id = :courseId")
    long totalMinutesByStudentAndCourse(@Param("studentId") Long studentId, @Param("courseId") Long courseId);
}
