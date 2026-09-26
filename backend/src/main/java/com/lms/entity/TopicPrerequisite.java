package com.lms.entity;

import jakarta.persistence.*;

/**
 * Directed edge: topic requires prerequisiteTopic.
 */
@Entity
@Table(name = "topic_prerequisites", uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "prerequisite_topic_id"}))
public class TopicPrerequisite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_topic_id", nullable = false)
    private Topic prerequisiteTopic;

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public Topic getPrerequisiteTopic() {
        return prerequisiteTopic;
    }

    public void setPrerequisiteTopic(Topic prerequisiteTopic) {
        this.prerequisiteTopic = prerequisiteTopic;
    }
}
