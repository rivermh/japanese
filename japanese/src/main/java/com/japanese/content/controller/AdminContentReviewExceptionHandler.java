package com.japanese.content.controller;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.japanese.content.service.ContentReleaseBatchException;

@RestControllerAdvice(assignableTypes = {
        AdminContentReviewApiController.class,
        AdminContentReviewController.class,
        AdminContentReleaseController.class,
        AdminContentSourceRightsApiController.class
})
public class AdminContentReviewExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "검수 대상을 찾을 수 없습니다."));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalidReview(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(ContentReleaseBatchException.class)
    ResponseEntity<Map<String, String>> invalidBatch(ContentReleaseBatchException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage(), "code", exception.getCode()));
    }
}
