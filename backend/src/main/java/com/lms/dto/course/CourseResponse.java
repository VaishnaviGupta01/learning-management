package com.lms.dto.course;

import java.time.Instant;

import com.lms.entity.Course;

/** Course summary used in listings. */
public record CourseResponse(
        Long id,
        String code,
        String title,
        String description,
        boolean published,
        Long instructorId,
        String instructorName,
        int moduleCount,
        Instant createdAt,
        Instant updatedAt) {

    public static CourseResponse from(Course c) {
        return new CourseResponse(c.getId(), c.getCode(), c.getTitle(), c.getDescription(), c.isPublished(),
                c.getInstructor().getId(),
                c.getInstructor().getFirstName() + " " + c.getInstructor().getLastName(),
                c.getModules().size(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
