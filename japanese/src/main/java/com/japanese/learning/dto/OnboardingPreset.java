package com.japanese.learning.dto;

public record OnboardingPreset(String key, String label, int dailyNewWordLimit,
                               int dailyNewGrammarLimit, int dailyGoal) {
}
