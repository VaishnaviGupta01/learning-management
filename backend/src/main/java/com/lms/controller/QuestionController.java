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

import com.lms.dto.question.QuestionRequest;
import com.lms.dto.question.QuestionResponse;
import com.lms.entity.enums.QuestionStatus;
import com.lms.security.UserPrincipal;
import com.lms.service.QuestionService;

import jakarta.validation.Valid;

/** Question bank endpoints; all of them expose answers, so all are restricted to instructors/admins. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/topics/{topicId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse create(@PathVariable Long topicId, @Valid @RequestBody QuestionRequest request,
                                   @AuthenticationPrincipal UserPrincipal user) {
        return questionService.createQuestion(topicId, request, user);
    }

    @GetMapping("/topics/{topicId}/questions")
    public List<QuestionResponse> listByTopic(@PathVariable Long topicId,
                                              @RequestParam(required = false) QuestionStatus status,
                                              @AuthenticationPrincipal UserPrincipal user) {
        return questionService.listTopicQuestions(topicId, status, user);
    }

    @GetMapping("/questions/pending")
    public List<QuestionResponse> pendingReview(@AuthenticationPrincipal UserPrincipal user) {
        return questionService.listPendingReview(user);
    }

    @GetMapping("/questions/{questionId}")
    public QuestionResponse get(@PathVariable Long questionId, @AuthenticationPrincipal UserPrincipal user) {
        return questionService.getQuestion(questionId, user);
    }

    @PutMapping("/questions/{questionId}")
    public QuestionResponse update(@PathVariable Long questionId, @Valid @RequestBody QuestionRequest request,
                                   @AuthenticationPrincipal UserPrincipal user) {
        return questionService.updateQuestion(questionId, request, user);
    }

    @PatchMapping("/questions/{questionId}/approve")
    public QuestionResponse approve(@PathVariable Long questionId, @AuthenticationPrincipal UserPrincipal user) {
        return questionService.approve(questionId, user);
    }

    @PatchMapping("/questions/{questionId}/reject")
    public QuestionResponse reject(@PathVariable Long questionId, @AuthenticationPrincipal UserPrincipal user) {
        return questionService.reject(questionId, user);
    }

    @DeleteMapping("/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long questionId, @AuthenticationPrincipal UserPrincipal user) {
        questionService.deleteQuestion(questionId, user);
    }
}
