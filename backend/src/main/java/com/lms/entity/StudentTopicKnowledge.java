package com.lms.entity;

import java.time.Instant;

import com.lms.entity.enums.TopicClassification;

import jakarta.persistence.*;

/**
 * Per-student knowledge state for a topic (mastery 0..1 and weak/strong classification).
 */
@Entity
@Table(name = "student_topic_knowledge", uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "topic_id"}))
public class StudentTopicKnowledge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "mastery_score", nullable = false)
    private double masteryScore = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 30)
    private TopicClassification classification = TopicClassification.NOT_STARTED;

    @Column(name = "attempts_count", nullable = false)
    private int attemptsCount = 0;

    @Column(name = "correct_count", nullable = false)
    private int correctCount = 0;

    @Column(name = "last_assessed_at")
    private Instant lastAssessedAt;

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public double getMasteryScore() {
        return masteryScore;
    }

    public void setMasteryScore(double masteryScore) {
        this.masteryScore = masteryScore;
    }

    public TopicClassification getClassification() {
        return classification;
    }

    public void setClassification(TopicClassification classification) {
        this.classification = classification;
    }

    public int getAttemptsCount() {
        return attemptsCount;
    }

    public void setAttemptsCount(int attemptsCount) {
        this.attemptsCount = attemptsCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public Instant getLastAssessedAt() {
        return lastAssessedAt;
    }

    public void setLastAssessedAt(Instant lastAssessedAt) {
        this.lastAssessedAt = lastAssessedAt;
    }
}
