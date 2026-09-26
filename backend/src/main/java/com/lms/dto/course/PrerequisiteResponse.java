package com.lms.dto.course;

import com.lms.entity.TopicPrerequisite;

/** Edge meaning "topic requires prerequisiteTopic". */
public record PrerequisiteResponse(
        Long id,
        Long topicId,
        String topicTitle,
        Long prerequisiteTopicId,
        String prerequisiteTopicTitle) {

    public static PrerequisiteResponse from(TopicPrerequisite p) {
        return new PrerequisiteResponse(p.getId(), p.getTopic().getId(), p.getTopic().getTitle(),
                p.getPrerequisiteTopic().getId(), p.getPrerequisiteTopic().getTitle());
    }
}
