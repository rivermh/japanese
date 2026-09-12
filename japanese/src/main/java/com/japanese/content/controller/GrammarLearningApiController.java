package com.japanese.content.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.content.dto.GrammarComparisonDetails;
import com.japanese.content.dto.GrammarConfirmationAnswerResult;
import com.japanese.content.dto.GrammarConfirmationQuestionDetails;
import com.japanese.content.dto.GrammarLearningDetails;
import com.japanese.content.service.GrammarLearningService;
import com.japanese.learning.dto.GrammarWeakness;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grammars")
public class GrammarLearningApiController {
    private final GrammarLearningService grammarLearningService;
    private final CurrentUserService currentUserService;
    public GrammarLearningApiController(GrammarLearningService grammarLearningService, CurrentUserService currentUserService) {
        this.grammarLearningService=grammarLearningService; this.currentUserService=currentUserService;
    }
    @GetMapping("/{slug}/learning")
    public GrammarLearningDetails learning(@PathVariable String slug) { return grammarLearningService.publicDetails(slug); }
    @GetMapping("/{slug}/confirmation")
    public List<GrammarConfirmationQuestionDetails> confirmation(@PathVariable String slug) { return grammarLearningService.confirmationQuestions(slug); }
    @GetMapping("/{slug}/compare/{otherSlug}")
    public ResponseEntity<GrammarComparisonDetails> comparison(@PathVariable String slug, @PathVariable String otherSlug) {
        return ResponseEntity.of(grammarLearningService.publicComparison(slug, otherSlug));
    }
    @PostMapping("/confirmations/{questionId}/answer")
    public GrammarConfirmationAnswerResult answer(@PathVariable Long questionId, @RequestParam Long choiceId) {
        return grammarLearningService.answer(currentUserService.currentAccount(), questionId, choiceId);
    }
    @GetMapping("/weaknesses")
    public List<GrammarWeakness> weaknesses() { return grammarLearningService.weaknesses(currentUserService.currentAccount()); }
}
