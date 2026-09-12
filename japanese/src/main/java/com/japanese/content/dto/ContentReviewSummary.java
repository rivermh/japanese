package com.japanese.content.dto;

import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.ReviewStatus;
import java.util.List;

public record ContentReviewSummary(
        Long id,
        String slug,
        ContentType type,
        String title,
        String reading,
        String meaning,
        String level,
        List<String> categories,
        ReviewStatus reviewStatus,
        String reviewNote,
        int exampleCount,
        String sourceRef
) {
}
