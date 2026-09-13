package com.japanese.learning.dto;

public record WeeklyQuizAggregate(long attemptCount, long correctCount, long incorrectCount, long experience) {
    public int accuracyPercent() { return attemptCount == 0 ? 0 : (int) (correctCount * 100 / attemptCount); }
}
