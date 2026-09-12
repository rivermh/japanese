package com.japanese.content.controller;

import com.japanese.content.dto.QuizAnswerResult;
import com.japanese.content.dto.QuizQuestionDetails;
import com.japanese.content.dto.QuizQuestionPage;
import com.japanese.content.service.QuizQuestionService;
import com.japanese.account.service.CurrentUserService;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quiz")
public class QuizApiController {

    private final QuizQuestionService quizQuestionService;
    private final CurrentUserService currentUserService;

    public QuizApiController(QuizQuestionService quizQuestionService, CurrentUserService currentUserService) {
        this.quizQuestionService = quizQuestionService; this.currentUserService = currentUserService;
    }

    @GetMapping("/questions")
    public QuizQuestionPage questions(
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return quizQuestionService.findPage(level, page, size);
    }

    @GetMapping("/questions/{id}")
    public ResponseEntity<QuizQuestionDetails> question(@PathVariable Long id) {
        return ResponseEntity.of(quizQuestionService.findById(id));
    }

    @PostMapping("/questions/{id}/answer")
    public QuizAnswerResult answer(
            @PathVariable Long id, @RequestParam String answer
    ) {
        return quizQuestionService.answer(id, answer, currentUserService.currentAccount())
                .orElseThrow(() -> new NoSuchElementException("Quiz question not found: " + id));
    }
}
