package com.lms.dto.student;

import java.time.Instant;

import com.lms.entity.StudentTopicKnowledge;
import com.lms.entity.enums.TopicClassification;

public record TopicKnowledgeResponse(
        Long topicId,
        String topicTitle,
        Long courseId,
        double masteryScore,
        TopicClassification classification,
        int attemptsCount,
        int correctCount,
        Instant lastAssessedAt) {

    public static TopicKnowledgeResponse from(StudentTopicKnowledge k) {
        return new TopicKnowledgeResponse(k.getTopic().getId(), k.getTopic().getTitle(),
                k.getTopic().getModule().getCourse().getId(), k.getMasteryScore(), k.getClassification(),
                k.getAttemptsCount(), k.getCorrectCount(), k.getLastAssessedAt());
    }
}
