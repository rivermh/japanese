package com.japanese.content.controller;

import com.japanese.content.service.QuizQuestionService;
import com.japanese.content.dto.QuizAnswerResult;
import com.japanese.account.service.CurrentUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class QuizController {

    private final QuizQuestionService quizQuestionService;
    private final CurrentUserService currentUserService;

    public QuizController(QuizQuestionService quizQuestionService, CurrentUserService currentUserService) {
        this.quizQuestionService = quizQuestionService; this.currentUserService = currentUserService;
    }

    @GetMapping("/quiz")
    public String quiz(
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        model.addAttribute("questions", quizQuestionService.findPage(level, page, 10));
        model.addAttribute("level", level == null ? "" : level);
        return "quiz";
    }

    @GetMapping("/quiz/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("question", quizQuestionService.findById(id).orElseThrow());
        return "quiz-detail";
    }

    @PostMapping("/quiz/{id}/answer")
    public String answer(@PathVariable Long id, @RequestParam String answer, Model model) {
        model.addAttribute("question", quizQuestionService.findById(id).orElseThrow());
        QuizAnswerResult result = quizQuestionService.answer(id, answer, currentUserService.currentAccount()).orElseThrow();
        model.addAttribute("result", result);
        return "quiz-result";
    }
}
