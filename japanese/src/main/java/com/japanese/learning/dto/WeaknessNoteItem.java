package com.japanese.learning.dto;

import com.japanese.content.dto.ContentSummary;
import java.time.Instant;

public record WeaknessNoteItem(
        ContentSummary content,
        WeaknessStatus status,
        long regularAttemptCount,
        long regularIncorrectCount,
        long confirmationAttemptCount,
        long confirmationIncorrectCount,
        long consecutiveIncorrectCount,
        Instant lastIncorrectAt,
        String reason
) { }
