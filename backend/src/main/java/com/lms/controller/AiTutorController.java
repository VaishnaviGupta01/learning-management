package com.lms.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lms.dto.tutor.AiInteractionResponse;
import com.lms.dto.tutor.TutorChatRequest;
import com.lms.dto.tutor.TutorChatResponse;
import com.lms.security.UserPrincipal;
import com.lms.service.AiTutorService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Proxies tutor turns to rag-service; the LLM key never leaves that service. Any signed-in user may use it. */
@RestController
@RequestMapping("/api/tutor")
public class AiTutorController {

    private final AiTutorService tutorService;

    public AiTutorController(AiTutorService tutorService) {
        this.tutorService = tutorService;
    }

    @PostMapping("/chat")
    public TutorChatResponse chat(@Valid @RequestBody TutorChatRequest request,
                                  @AuthenticationPrincipal UserPrincipal user) {
        return tutorService.chat(request, user);
    }

    @GetMapping("/history")
    public List<AiInteractionResponse> history(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                                               @AuthenticationPrincipal UserPrincipal user) {
        return tutorService.history(user, limit);
    }
}
