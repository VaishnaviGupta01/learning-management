package com.lms.entity;

import com.lms.entity.enums.CreatedByType;

import jakarta.persistence.*;

/**
 * A suggested topic/resource for a student, ranked by score.
 */
@Entity
@Table(name = "recommendations")
public class Recommendation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resource_id")
    private LearningResource resource;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "score", nullable = false)
    private double score = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private CreatedByType source = CreatedByType.AI;

    @Column(name = "viewed", nullable = false)
    private boolean viewed = false;

    @Column(name = "dismissed", nullable = false)
    private boolean dismissed = false;

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

    public LearningResource getResource() {
        return resource;
    }

    public void setResource(LearningResource resource) {
        this.resource = resource;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public CreatedByType getSource() {
        return source;
    }

    public void setSource(CreatedByType source) {
        this.source = source;
    }

    public boolean isViewed() {
        return viewed;
    }

    public void setViewed(boolean viewed) {
        this.viewed = viewed;
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void setDismissed(boolean dismissed) {
        this.dismissed = dismissed;
    }
}
