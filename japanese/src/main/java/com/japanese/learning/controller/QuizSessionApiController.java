package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.dto.*;
import com.japanese.learning.service.QuizSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/quizzes")
public class QuizSessionApiController {
    private final CurrentUserService currentUser;
    private final QuizSessionService quizzes;

    public QuizSessionApiController(CurrentUserService currentUser, QuizSessionService quizzes) {
        this.currentUser = currentUser;
        this.quizzes = quizzes;
    }

    @PostMapping
    public QuizSessionView start(@Valid @RequestBody(required = false) QuizStartRequest request) {
        QuizStartRequest effective = request == null ? new QuizStartRequest(null, null) : request;
        return quizzes.startOrResume(currentUser.currentAccount(), effective.effectiveMode(), effective.effectiveCount());
    }

    @GetMapping("/{sessionId}")
    public QuizSessionView session(@PathVariable("sessionId") String sessionId) {
        return quizzes.get(currentUser.currentAccount(), sessionId);
    }

    @PostMapping("/{sessionId}/answers")
    public QuizAnswerSubmission answer(@PathVariable("sessionId") String sessionId,
            @Valid @RequestBody QuizAnswerRequest request) {
        return quizzes.answer(currentUser.currentAccount(), sessionId, request.itemId(), request.answer());
    }

    @GetMapping("/{sessionId}/result")
    public QuizSessionResult result(@PathVariable("sessionId") String sessionId) {
        return quizzes.result(currentUser.currentAccount(), sessionId);
    }

    @PostMapping("/{sessionId}/restart")
    public QuizSessionView restart(@PathVariable("sessionId") String sessionId) {
        return quizzes.restart(currentUser.currentAccount(), sessionId);
    }
}
