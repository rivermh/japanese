package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.entity.QuizMode;
import com.japanese.learning.service.QuizSessionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class QuizSessionController {
    private final CurrentUserService currentUser;
    private final QuizSessionService quizzes;

    public QuizSessionController(CurrentUserService currentUser, QuizSessionService quizzes) {
        this.currentUser = currentUser;
        this.quizzes = quizzes;
    }

    @PostMapping("/quiz/start")
    public String start() {
        var session = quizzes.startOrResume(currentUser.currentAccount(), QuizMode.QUICK, 5);
        return "redirect:/quiz/session/" + session.sessionId();
    }

    @GetMapping("/quiz/session/{sessionId}")
    public String session(@PathVariable("sessionId") String sessionId, Model model) {
        var session = quizzes.get(currentUser.currentAccount(), sessionId);
        if (session.completed()) return "redirect:/quiz/session/" + sessionId + "/result";
        model.addAttribute("quizSession", session);
        return "quiz-session";
    }

    @PostMapping("/quiz/session/{sessionId}/answer")
    public String answer(@PathVariable("sessionId") String sessionId,
            @RequestParam("itemId") Long itemId, @RequestParam("answer") String answer, Model model) {
        var submission = quizzes.answer(currentUser.currentAccount(), sessionId, itemId, answer);
        model.addAttribute("quizSession", submission.session());
        model.addAttribute("feedback", submission.feedback());
        return "quiz-session";
    }

    @GetMapping("/quiz/session/{sessionId}/result")
    public String result(@PathVariable("sessionId") String sessionId, Model model) {
        model.addAttribute("quizResult", quizzes.result(currentUser.currentAccount(), sessionId));
        return "quiz-session-result";
    }

    @PostMapping("/quiz/session/{sessionId}/restart")
    public String restart(@PathVariable("sessionId") String sessionId) {
        var restarted = quizzes.restart(currentUser.currentAccount(), sessionId);
        return "redirect:/quiz/session/" + restarted.sessionId();
    }
}
