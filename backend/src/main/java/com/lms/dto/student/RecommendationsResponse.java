package com.lms.dto.student;

import java.time.Instant;
import java.util.List;

import com.lms.entity.enums.TopicClassification;

/**
 * {@code recommendations}: unlocked topics ranked by priority.
 * {@code blocked}: topics with incomplete prerequisites; they are not ranked, only pointed at what to finish first.
 * {@code stale} is true when ml-service was unavailable and the last saved ranking is returned instead.
 */
public record RecommendationsResponse(
        Instant generatedAt,
        boolean stale,
        List<Item> recommendations,
        List<BlockedTopic> blocked) {

    public record Item(
            Long id,
            int rank,
            Long topicId,
            String topicTitle,
            Long courseId,
            String courseTitle,
            double priority,
            double knowledgeScore,
            TopicClassification classification,
            double importance,
            double urgency,
            String reason,
            Long resourceId,
            String resourceTitle) {
    }

    public record BlockedTopic(
            Long topicId,
            String topicTitle,
            Long courseId,
            List<Long> completeFirstTopicIds,
            String message) {
    }
}
