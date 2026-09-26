package com.lms.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lms.dto.course.PrerequisiteRequest;
import com.lms.dto.course.PrerequisiteResponse;
import com.lms.dto.course.TopicRequest;
import com.lms.dto.course.TopicResponse;
import com.lms.security.UserPrincipal;
import com.lms.service.TopicService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class TopicController {

    private static final String MANAGERS = "hasAnyRole('INSTRUCTOR','ADMIN')";

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @PostMapping("/modules/{moduleId}/topics")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGERS)
    public TopicResponse create(@PathVariable Long moduleId, @Valid @RequestBody TopicRequest request,
                                @AuthenticationPrincipal UserPrincipal user) {
        return topicService.createTopic(moduleId, request, user);
    }

    @GetMapping("/topics/{topicId}")
    public TopicResponse get(@PathVariable Long topicId, @AuthenticationPrincipal UserPrincipal user) {
        return topicService.getTopic(topicId, user);
    }

    @PutMapping("/topics/{topicId}")
    @PreAuthorize(MANAGERS)
    public TopicResponse update(@PathVariable Long topicId, @Valid @RequestBody TopicRequest request,
                                @AuthenticationPrincipal UserPrincipal user) {
        return topicService.updateTopic(topicId, request, user);
    }

    @DeleteMapping("/topics/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGERS)
    public void delete(@PathVariable Long topicId, @AuthenticationPrincipal UserPrincipal user) {
        topicService.deleteTopic(topicId, user);
    }

    @GetMapping("/topics/{topicId}/prerequisites")
    public List<PrerequisiteResponse> prerequisites(@PathVariable Long topicId,
                                                    @AuthenticationPrincipal UserPrincipal user) {
        return topicService.listPrerequisites(topicId, user);
    }

    @PostMapping("/topics/{topicId}/prerequisites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGERS)
    public PrerequisiteResponse addPrerequisite(@PathVariable Long topicId,
                                                @Valid @RequestBody PrerequisiteRequest request,
                                                @AuthenticationPrincipal UserPrincipal user) {
        return topicService.addPrerequisite(topicId, request.prerequisiteTopicId(), user);
    }

    @DeleteMapping("/topics/{topicId}/prerequisites/{prerequisiteTopicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGERS)
    public void removePrerequisite(@PathVariable Long topicId, @PathVariable Long prerequisiteTopicId,
                                   @AuthenticationPrincipal UserPrincipal user) {
        topicService.removePrerequisite(topicId, prerequisiteTopicId, user);
    }
}
