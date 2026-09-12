package com.japanese.learning.dto;

import java.time.Instant;

public record ContentLearningStatus(
        String state,
        String label,
        Instant nextReviewAt
) {
    public static ContentLearningStatus notStarted() {
        return new ContentLearningStatus("NEW", "새 콘텐츠", null);
    }
}
