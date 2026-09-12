package com.japanese.content.dto;

import com.japanese.content.entity.ContentType;
import java.util.List;

public record ContentFilterOptions(
        List<ContentType> types,
        List<LevelOption> levels,
        List<CategoryOption> categories
) {
    public record LevelOption(String system, String code, String name) {
    }

    public record CategoryOption(String slug, String name) {
    }
}
