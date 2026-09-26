package com.lms.dto.course;

import java.util.List;

/** Course topics in a prerequisite-respecting (topological) order. */
public record LearningPathResponse(Long courseId, List<TopicResponse> topics) {
}
