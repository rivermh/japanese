package com.japanese.learning.dto;

import com.japanese.content.dto.ContentSummary;
import java.time.Instant;

public record StudyQueueItem(ContentSummary content, Instant addedAt) {
}
