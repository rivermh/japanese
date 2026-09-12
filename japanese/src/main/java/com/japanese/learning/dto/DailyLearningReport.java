package com.japanese.learning.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyLearningReport(
        LocalDate date,
        int newWordCount,
        int newGrammarCount,
        int reviewCount,
        int retrainCount,
        int correctCount,
        int incorrectCount,
        int regularGoalCompleted,
        int dailyGoal,
        int remainingDueReviewCount,
        int earnedExperience,
        int currentStreak,
        int tomorrowReviewCount,
        int upcomingReviewCount,
        int quizAttemptCount,
        int quizCorrectCount,
        int quizIncorrectCount,
        int quizExperience,
        List<DailyContentActivity> activities
) {
}
