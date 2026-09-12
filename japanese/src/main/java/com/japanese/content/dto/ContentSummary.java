package com.japanese.content.dto;

import com.japanese.content.entity.ContentType;
import java.util.List;

public record ContentSummary(
        Long id,
        String slug,
        ContentType type,
        String title,
        String reading,
        String description,
        List<LevelSummary> levels,
        List<CategorySummary> categories
) {

    public record LevelSummary(String system, String code, String name) {
    }

    public record CategorySummary(String slug, String name) {
    }
}
