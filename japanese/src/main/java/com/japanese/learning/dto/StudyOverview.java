package com.japanese.learning.dto;

public record StudyOverview(
        String displayName,
        CharacterStatus character,
        long totalAnswers,
        long correctAnswers,
        long dueReviewCount
) {
}
