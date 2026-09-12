package com.japanese.content.dto;

import java.util.List;

public record QuizQuestionPage(
        List<QuizQuestionDetails> questions,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
