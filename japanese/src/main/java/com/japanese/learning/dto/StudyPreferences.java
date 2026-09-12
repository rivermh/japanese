package com.japanese.learning.dto;

public record StudyPreferences(
        LearningScope scope,
        int dailyNewWordLimit,
        int dailyNewGrammarLimit
) {
}
