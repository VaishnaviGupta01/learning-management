package com.lms.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;

/**
 * Enrollment plus aggregate progress of a student in a course.
 */
@Entity
@Table(name = "student_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_id"}))
public class StudentProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "completion_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionPercentage = BigDecimal.ZERO;

    @Column(name = "topics_completed", nullable = false)
    private int topicsCompleted = 0;

    @Column(name = "total_study_minutes", nullable = false)
    private int totalStudyMinutes = 0;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public BigDecimal getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(BigDecimal completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public int getTopicsCompleted() {
        return topicsCompleted;
    }

    public void setTopicsCompleted(int topicsCompleted) {
        this.topicsCompleted = topicsCompleted;
    }

    public int getTotalStudyMinutes() {
        return totalStudyMinutes;
    }

    public void setTotalStudyMinutes(int totalStudyMinutes) {
        this.totalStudyMinutes = totalStudyMinutes;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }
}
