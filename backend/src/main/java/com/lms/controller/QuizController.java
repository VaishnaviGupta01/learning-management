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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lms.dto.quiz.AttemptResultResponse;
import com.lms.dto.quiz.AttemptStartResponse;
import com.lms.dto.quiz.AttemptSummaryResponse;
import com.lms.dto.quiz.DiagnosticRequest;
import com.lms.dto.quiz.QuizRequest;
import com.lms.dto.quiz.QuizResponse;
import com.lms.dto.quiz.SubmitAttemptRequest;
import com.lms.security.UserPrincipal;
import com.lms.service.DiagnosticService;
import com.lms.service.QuizService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class QuizController {

    private static final String MANAGERS = "hasAnyRole('INSTRUCTOR','ADMIN')";
    private static final String STUDENT = "hasRole('STUDENT')";

    private final QuizService quizService;
    private final DiagnosticService diagnosticService;

    public QuizController(QuizService quizService, DiagnosticService diagnosticService) {
        this.quizService = quizService;
        this.diagnosticService = diagnosticService;
    }

    // ------------------------------------------------------------------ quizzes

    @PostMapping("/courses/{courseId}/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGERS)
    public QuizResponse create(@PathVariable Long courseId, @Valid @RequestBody QuizRequest request,
                               @AuthenticationPrincipal UserPrincipal user) {
        return quizService.createQuiz(courseId, request, user);
    }

    @GetMapping("/courses/{courseId}/quizzes")
    public List<QuizResponse> list(@PathVariable Long courseId, @AuthenticationPrincipal UserPrincipal user) {
        return quizService.listQuizzes(courseId, user);
    }

    @GetMapping("/quizzes/{quizId}")
    public QuizResponse get(@PathVariable Long quizId, @AuthenticationPrincipal UserPrincipal user) {
        return quizService.getQuiz(quizId, user);
    }

    @PatchMapping("/quizzes/{quizId}/publish")
    @PreAuthorize(MANAGERS)
    public QuizResponse publish(@PathVariable Long quizId, @RequestParam(defaultValue = "true") boolean published,
                                @AuthenticationPrincipal UserPrincipal user) {
        return quizService.setPublished(quizId, published, user);
    }

    @DeleteMapping("/quizzes/{quizId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGERS)
    public void delete(@PathVariable Long quizId, @AuthenticationPrincipal UserPrincipal user) {
        quizService.deleteQuiz(quizId, user);
    }

    @PostMapping("/courses/{courseId}/diagnostic")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(STUDENT)
    public AttemptStartResponse diagnostic(@PathVariable Long courseId,
                                           @RequestBody(required = false) DiagnosticRequest request,
                                           @AuthenticationPrincipal UserPrincipal user) {
        return diagnosticService.startDiagnostic(courseId, request, user);
    }

    // ------------------------------------------------------------------ attempts

    @PostMapping("/quizzes/{quizId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(STUDENT)
    public AttemptStartResponse startAttempt(@PathVariable Long quizId, @AuthenticationPrincipal UserPrincipal user) {
        return quizService.startAttempt(quizId, user);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize(STUDENT)
    public AttemptResultResponse submit(@PathVariable Long attemptId, @Valid @RequestBody SubmitAttemptRequest request,
                                        @AuthenticationPrincipal UserPrincipal user) {
        return quizService.submitAttempt(attemptId, request, user);
    }

    @GetMapping("/attempts/me")
    @PreAuthorize(STUDENT)
    public List<AttemptSummaryResponse> myAttempts(@AuthenticationPrincipal UserPrincipal user) {
        return quizService.myAttempts(user);
    }

    @GetMapping("/attempts/{attemptId}")
    public AttemptResultResponse result(@PathVariable Long attemptId, @AuthenticationPrincipal UserPrincipal user) {
        return quizService.getAttemptResult(attemptId, user);
    }
}
