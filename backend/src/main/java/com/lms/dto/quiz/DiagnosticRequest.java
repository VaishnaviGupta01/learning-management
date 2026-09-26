package com.lms.dto.quiz;

import java.util.List;

/** Optional body for a diagnostic quiz; when {@code topicIds} is null or empty, all course topics are used. */
public record DiagnosticRequest(List<Long> topicIds) {
}
