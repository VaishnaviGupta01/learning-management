package com.lms.dto.course;

import java.util.List;

/** Course with its full module/topic tree. */
public record CourseDetailResponse(
        Long id,
        String code,
        String title,
        String description,
        boolean published,
        Long instructorId,
        String instructorName,
        List<ModuleResponse> modules) {
}
