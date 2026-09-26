package com.lms.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lms.dto.course.CourseDetailResponse;
import com.lms.dto.course.CourseRequest;
import com.lms.dto.course.CourseResponse;
import com.lms.dto.course.LearningPathResponse;
import com.lms.dto.course.ModuleRequest;
import com.lms.dto.course.ModuleResponse;
import com.lms.dto.course.TopicResponse;
import com.lms.security.UserPrincipal;
import com.lms.service.CourseService;
import com.lms.service.TopicService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class CourseController {

    private static final String MANAGERS = "hasAnyRole('INSTRUCTOR','ADMIN')";

    private final CourseService courseService;
    private final TopicService topicService;

    public CourseController(CourseService courseService, TopicService topicService) {
        this.courseService = courseService;
        this.topicService = topicService;
    }

    // ------------------------------------------------------------------ courses

    @GetMapping("/courses")
    public List<CourseResponse> list(@AuthenticationPrincipal UserPrincipal user) {
        return courseService.listCourses(user);
    }

    @GetMapping("/courses/{courseId}")
    public CourseDetailResponse get(@PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal user) {
        return courseService.getCourse(courseId, user);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGERS)
    public CourseResponse create(@Valid @RequestBody CourseRequest request, @AuthenticationPrincipal UserPrincipal user) {
        return courseService.createCourse(request, user);
    }

    @PutMapping("/courses/{courseId}")
    @PreAuthorize(MANAGERS)
    public CourseResponse update(@PathVariable Long courseId, @Valid @RequestBody CourseRequest request,
                                 @AuthenticationPrincipal UserPrincipal user) {
        return courseService.updateCourse(courseId, request, user);
    }

    @PatchMapping("/courses/{courseId}/publish")
    @PreAuthorize(MANAGERS)
    public CourseResponse publish(@PathVariable Long courseId,
                                  @RequestParam(defaultValue = "true") boolean published,
                                  @AuthenticationPrincipal UserPrincipal user) {
        return courseService.setPublished(courseId, published, user);
    }

    @DeleteMapping("/courses/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGERS)
    public void delete(@PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal user) {
        courseService.deleteCourse(courseId, user);
    }

    @GetMapping("/courses/{courseId}/topics")
    public List<TopicResponse> topics(@PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal user) {
        return topicService.listCourseTopics(courseId, user);
    }

    @GetMapping("/courses/{courseId}/learning-path")
    public LearningPathResponse learningPath(@PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal user) {
        return topicService.learningPath(courseId, user);
    }

    // ------------------------------------------------------------------ modules

    @GetMapping("/courses/{courseId}/modules")
    public List<ModuleResponse> modules(@PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal user) {
        return courseService.listModules(courseId, user);
    }

    @PostMapping("/courses/{courseId}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGERS)
    public ModuleResponse createModule(@PathVariable Long courseId, @Valid @RequestBody ModuleRequest request,
                                       @AuthenticationPrincipal UserPrincipal user) {
        return courseService.createModule(courseId, request, user);
    }

    @PutMapping("/modules/{moduleId}")
    @PreAuthorize(MANAGERS)
    public ModuleResponse updateModule(@PathVariable Long moduleId, @Valid @RequestBody ModuleRequest request,
                                       @AuthenticationPrincipal UserPrincipal user) {
        return courseService.updateModule(moduleId, request, user);
    }

    @DeleteMapping("/modules/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGERS)
    public void deleteModule(@PathVariable Long moduleId, @AuthenticationPrincipal UserPrincipal user) {
        courseService.deleteModule(moduleId, user);
    }
}
