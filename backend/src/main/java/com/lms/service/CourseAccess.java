package com.lms.service;

import org.springframework.security.access.AccessDeniedException;

import com.lms.entity.Course;
import com.lms.exception.ResourceNotFoundException;
import com.lms.security.UserPrincipal;

/**
 * Row-level rules on top of the role checks done by {@code @PreAuthorize}:
 * admins manage every course, instructors only the courses they own, and unpublished
 * courses are invisible to everyone who cannot manage them.
 */
final class CourseAccess {

    private CourseAccess() {
    }

    static boolean canManage(Course course, UserPrincipal user) {
        return user.isAdmin()
                || (user.isInstructor() && course.getInstructor().getId().equals(user.getId()));
    }

    static void requireManage(Course course, UserPrincipal user) {
        if (!canManage(course, user)) {
            throw new AccessDeniedException("Not allowed to manage course " + course.getId());
        }
    }

    /** Unpublished courses are reported as not found rather than forbidden, so their existence is not leaked. */
    static void requireView(Course course, UserPrincipal user) {
        if (!course.isPublished() && !canManage(course, user)) {
            throw new ResourceNotFoundException("Course", course.getId());
        }
    }
}
