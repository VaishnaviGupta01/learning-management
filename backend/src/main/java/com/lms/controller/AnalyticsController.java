package com.lms.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lms.dto.analytics.InstructorAnalyticsResponse;
import com.lms.security.UserPrincipal;
import com.lms.service.AdminStatsService;
import com.lms.service.InstructorAnalyticsService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private final InstructorAnalyticsService analyticsService;
    private final AdminStatsService adminStatsService;

    public AnalyticsController(InstructorAnalyticsService analyticsService, AdminStatsService adminStatsService) {
        this.analyticsService = analyticsService;
        this.adminStatsService = adminStatsService;
    }

    /** Course analytics incl. topic performance and recommendation evaluation (Precision@k / Recall@k). */
    @GetMapping("/instructor/analytics")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public InstructorAnalyticsResponse instructorAnalytics(@RequestParam Long courseId,
                                                           @RequestParam(defaultValue = "5") @Min(1) @Max(20) int k,
                                                           @AuthenticationPrincipal UserPrincipal user) {
        return analyticsService.analytics(courseId, k, user);
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminStatsService.Stats adminStats() {
        return adminStatsService.stats();
    }
}
