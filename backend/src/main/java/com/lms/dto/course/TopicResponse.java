package com.lms.dto.course;

import java.util.List;

import com.lms.entity.Topic;
import com.lms.entity.enums.Difficulty;

public record TopicResponse(
        Long id,
        Long moduleId,
        Long courseId,
        String title,
        String description,
        int orderIndex,
        Difficulty difficulty,
        Integer estimatedMinutes,
        List<Long> prerequisiteIds) {

    public static TopicResponse from(Topic t, List<Long> prerequisiteIds) {
        return new TopicResponse(t.getId(), t.getModule().getId(), t.getModule().getCourse().getId(),
                t.getTitle(), t.getDescription(), t.getOrderIndex(), t.getDifficulty(), t.getEstimatedMinutes(),
                prerequisiteIds);
    }
}
