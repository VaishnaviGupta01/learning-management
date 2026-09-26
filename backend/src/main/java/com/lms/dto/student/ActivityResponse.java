package com.lms.dto.student;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.lms.entity.enums.RevisionStage;

/**
 * Study rhythm for the student dashboard. Days are calendar days in the requested time zone.
 * The current streak still counts when today has no session yet but yesterday had one.
 */
public record ActivityResponse(
        String timeZone,
        int currentStreakDays,
        int longestStreakDays,
        boolean studiedToday,
        int minutesToday,
        int minutesLast7Days,
        List<Day> last14Days,
        List<DueRevision> dueRevisions) {

    public record Day(LocalDate date, int minutes, int sessions) {
    }

    public record DueRevision(Long topicId, String topicTitle, Long courseId, RevisionStage stage, Instant nextReviewAt,
                              boolean overdue) {
    }
}
