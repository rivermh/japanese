package com.japanese.learning.dto;

import java.util.List;

public record OnboardingRequest(String currentLevel, String targetLevel, List<String> categories,
                                String preset, Integer dailyNewWordLimit,
                                Integer dailyNewGrammarLimit, Integer dailyGoal) {
}
