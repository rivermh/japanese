package com.japanese.content.controller;

import com.japanese.content.service.NormalizedCandidatePairReviewConflictException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * JLPT-MAX Ticket 4C: mirrors {@link AdminContentReviewExceptionHandler}'s exception-to-HTTP-status
 * shape, but scoped only to {@link AdminNormalizedCandidateReviewController} - kept as its own advice
 * rather than added to the shared production-review advice's {@code assignableTypes}, since this
 * ticket's private-review domain is deliberately not merged with that one (JLPT-MAX Ticket 4C step
 * 3). POST decision/reanalyze failures are already caught inline in the controller (PRG
 * flash-message pattern, matching {@code AdminContentReviewController}); this advice only ever
 * applies to a GET list/detail request whose path parameters do not resolve.
 */
@RestControllerAdvice(assignableTypes = AdminNormalizedCandidateReviewController.class)
public class AdminNormalizedCandidateReviewExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalidRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(NormalizedCandidatePairReviewConflictException.class)
    ResponseEntity<Map<String, String>> conflict(NormalizedCandidatePairReviewConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
    }
}
