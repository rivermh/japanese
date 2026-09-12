package com.japanese.learning.dto;

import com.japanese.content.entity.ContentType;
import com.japanese.learning.entity.StudyActivityType;
import com.japanese.learning.entity.StudyResult;
import java.time.Instant;

public record DailyContentActivity(
        String slug,
        ContentType type,
        String title,
        String reading,
        StudyActivityType activityType,
        StudyResult result,
        Instant studiedAt
) {
}
