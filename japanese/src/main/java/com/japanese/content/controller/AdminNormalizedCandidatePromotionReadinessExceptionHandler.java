package com.japanese.content.controller;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * JLPT-MAX Ticket 4D: mirrors {@code AdminNormalizedCandidateReviewExceptionHandler}'s shape, scoped
 * only to {@link AdminNormalizedCandidatePromotionReadinessController}. An invalid enum path/query
 * value (e.g. an unknown {@code candidateType}/{@code overallStatus}/{@code issueCode}) is handled by
 * Spring's own default conversion-failure mapping to 400, exactly like
 * {@code AdminNormalizedCandidateReviewController}'s existing {@code assessment}/{@code decision}
 * query parameters - no extra handling is added here for that case.
 */
@RestControllerAdvice(assignableTypes = AdminNormalizedCandidatePromotionReadinessController.class)
public class AdminNormalizedCandidatePromotionReadinessExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }
}
