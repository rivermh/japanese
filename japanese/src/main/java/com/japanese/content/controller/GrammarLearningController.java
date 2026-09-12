package com.japanese.content.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.content.service.GrammarLearningService;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class GrammarLearningController {
    private final GrammarLearningService grammarLearningService;
    private final CurrentUserService currentUserService;

    public GrammarLearningController(GrammarLearningService grammarLearningService, CurrentUserService currentUserService) {
        this.grammarLearningService = grammarLearningService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/grammars/{slug}/compare/{otherSlug}")
    public String compare(@PathVariable String slug, @PathVariable String otherSlug, Model model) {
        model.addAttribute("comparison", grammarLearningService.publicComparison(slug, otherSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "grammar-comparison";
    }

    @GetMapping("/grammars/{slug}/confirm")
    public String confirmation(@PathVariable String slug, Model model) {
        model.addAttribute("questions", grammarLearningService.confirmationQuestions(slug));
        model.addAttribute("grammarSlug", slug);
        return "grammar-confirmation";
    }

    @PostMapping("/grammars/confirmations/{questionId}/answer")
    public String answer(@PathVariable Long questionId, @RequestParam Long choiceId, Model model) {
        try {
            model.addAttribute("result", grammarLearningService.answer(currentUserService.currentAccount(), questionId, choiceId));
            return "grammar-confirmation-result";
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
