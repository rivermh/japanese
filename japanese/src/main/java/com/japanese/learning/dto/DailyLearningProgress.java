package com.japanese.learning.dto;

public record DailyLearningProgress(
        int goal,
        long completed,
        long correctAnswers
) {

    public long remaining() {
        return Math.max(goal - completed, 0);
    }

    public int completionPercent() {
        if (goal <= 0) {
            return 100;
        }
        return (int) Math.min(100, completed * 100 / goal);
    }
}
