package com.japanese.learning.dto;

import com.japanese.learning.entity.StudyActivityType;
import com.japanese.learning.entity.TodayStudySessionState;

public record TodayStudySessionView(String sessionKey, TodayStudySessionState state, int totalCount, int completedCount,
                                    int reviewCount, int newWordCount, int newGrammarCount, String currentSlug,
                                    StudyActivityType currentActivityType, int completedReviewCount,
                                    int completedNewWordCount, int completedNewGrammarCount) {
    public boolean completed() { return state == TodayStudySessionState.COMPLETED; }
    public int currentNumber() { return completed() ? totalCount : completedCount + 1; }
    public int remainingCount() { return Math.max(totalCount - completedCount, 0); }
}
