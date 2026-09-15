package com.japanese.learning.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public record DueReviewCriteria(
        String learnerKey,
        Instant asOf,
        Collection<Long> levelIds,
        Collection<Long> categoryIds
) {
    public DueReviewCriteria {
        levelIds = levelIds == null ? List.of() : List.copyOf(levelIds);
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
    }

    boolean filterLevels() { return !levelIds.isEmpty(); }
    boolean filterCategories() { return !categoryIds.isEmpty(); }
    Collection<Long> queryLevelIds() { return filterLevels() ? levelIds : List.of(-1L); }
    Collection<Long> queryCategoryIds() { return filterCategories() ? categoryIds : List.of(-1L); }
}
