package com.lms.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lms.dto.student.ActivityResponse;
import com.lms.dto.student.CourseProgressResponse;
import com.lms.dto.student.RecommendationsResponse;
import com.lms.dto.student.TopicKnowledgeResponse;
import com.lms.security.UserPrincipal;
import com.lms.service.KnowledgeService;
import com.lms.service.ProgressService;
import com.lms.service.RecommendationService;
import com.lms.service.StudentActivityService;

@RestController
@RequestMapping("/api/students/me")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final ProgressService progressService;
    private final KnowledgeService knowledgeService;
    private final RecommendationService recommendationService;
    private final StudentActivityService activityService;

    public StudentController(ProgressService progressService, KnowledgeService knowledgeService,
                             RecommendationService recommendationService, StudentActivityService activityService) {
        this.progressService = progressService;
        this.knowledgeService = knowledgeService;
        this.recommendationService = recommendationService;
        this.activityService = activityService;
    }

    @GetMapping("/progress")
    public List<CourseProgressResponse> progress(@AuthenticationPrincipal UserPrincipal user) {
        return progressService.progressFor(user.getId());
    }

    @GetMapping("/topics/knowledge")
    public List<TopicKnowledgeResponse> knowledge(@RequestParam(required = false) Long courseId,
                                                  @AuthenticationPrincipal UserPrincipal user) {
        return knowledgeService.knowledgeFor(user.getId(), courseId);
    }

    /** Streak, recent study time and revisions due today; {@code tz} is an IANA zone such as Asia/Kolkata. */
    @GetMapping("/activity")
    public ActivityResponse activity(@RequestParam(required = false) String tz,
                                     @AuthenticationPrincipal UserPrincipal user) {
        return activityService.activity(user.getId(), tz);
    }

    /** Recomputes, stores and returns recommendations; {@code courseId} limits it to one course. */
    @GetMapping("/recommendations")
    public RecommendationsResponse recommendations(@RequestParam(required = false) Long courseId,
                                                   @AuthenticationPrincipal UserPrincipal user) {
        return recommendationService.recommend(user, courseId);
    }
}
