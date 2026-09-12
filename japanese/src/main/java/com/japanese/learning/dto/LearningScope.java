package com.japanese.learning.dto;

import java.util.List;

public record LearningScope(
        List<String> levelCodes,
        List<String> categorySlugs
) {
    public boolean allLevels() {
        return levelCodes == null || levelCodes.isEmpty();
    }

    public boolean allCategories() {
        return categorySlugs == null || categorySlugs.isEmpty();
    }
}
