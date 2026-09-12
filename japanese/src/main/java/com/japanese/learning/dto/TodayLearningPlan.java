package com.japanese.learning.dto;

import com.japanese.content.dto.ContentSummary;
import java.util.List;

public record TodayLearningPlan(
        List<ContentSummary> items,
        int reviewCount,
        int newWordCount,
        int newGrammarCount,
        int totalDueReviewCount,
        int remainingDueReviewCount
) {
    public TodayLearningPlan(List<ContentSummary> items, int reviewCount, int newWordCount, int newGrammarCount) {
        this(items, reviewCount, newWordCount, newGrammarCount, reviewCount, 0);
    }

    public int totalCount() { return items.size(); }
}
