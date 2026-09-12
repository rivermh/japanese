package com.japanese.learning.dto;

import java.time.LocalDate;

public record LearningActivityDay(
        LocalDate date,
        int newCount,
        int reviewCount,
        int retrainCount,
        int quizCount,
        int earnedExperience
) {
    public int totalActivityCount() {
        return newCount + reviewCount + retrainCount + quizCount;
    }
}
