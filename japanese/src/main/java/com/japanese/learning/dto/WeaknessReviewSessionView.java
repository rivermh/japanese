package com.japanese.learning.dto;

import com.japanese.learning.entity.WeaknessReviewSessionState;

public record WeaknessReviewSessionView(String sessionKey, Long sessionId, WeaknessReviewSessionState state,
                                        int totalCount, int completedCount, String currentSlug) {
    public boolean completed() { return state == WeaknessReviewSessionState.COMPLETED; }
    public int currentNumber() { return completed() ? totalCount : completedCount + 1; }
    public int remainingCount() { return Math.max(totalCount - completedCount, 0); }
}
