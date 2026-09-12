package com.japanese.content.dto;

import com.japanese.content.entity.ReviewStatus;
import java.time.Instant;

public record ReviewHistoryEntry(
        ReviewStatus status,
        String note,
        Instant reviewedAt
) {
}
