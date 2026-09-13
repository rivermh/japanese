package com.japanese.content.dto;

import java.util.List;

public record QuizQuestionDetails(
        Long id,
        long sourceNoteId,
        String level,
        String questionType,
        String label,
        String instruction,
        String promptJapanese,
        String promptKorean,
        List<String> choices,
        @com.fasterxml.jackson.annotation.JsonIgnore String answerJapanese,
        @com.fasterxml.jackson.annotation.JsonIgnore String answerKorean,
        @com.fasterxml.jackson.annotation.JsonIgnore String explanation
) {
}
