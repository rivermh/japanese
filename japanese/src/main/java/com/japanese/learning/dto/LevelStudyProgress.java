package com.japanese.learning.dto;

public record LevelStudyProgress(
        String levelCode,
        long learnedContentCount,
        long dueReviewCount
) {
}
