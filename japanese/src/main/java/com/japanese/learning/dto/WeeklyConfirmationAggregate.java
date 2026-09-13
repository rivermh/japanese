package com.japanese.learning.dto;

public record WeeklyConfirmationAggregate(long attemptCount, long correctCount, long incorrectCount) {
    public int accuracyPercent() { return attemptCount == 0 ? 0 : (int) (correctCount * 100 / attemptCount); }
}
