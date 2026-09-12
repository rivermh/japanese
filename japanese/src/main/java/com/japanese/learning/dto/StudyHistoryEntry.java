package com.japanese.learning.dto;

import com.japanese.content.entity.ContentType;
import com.japanese.learning.entity.StudyResult;
import java.time.Instant;

public record StudyHistoryEntry(
        String slug,
        ContentType contentType,
        String title,
        String reading,
        StudyResult result,
        Instant studiedAt
) {
}
