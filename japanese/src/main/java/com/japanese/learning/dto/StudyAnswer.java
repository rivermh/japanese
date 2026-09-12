package com.japanese.learning.dto;

import com.japanese.content.dto.ContentSummary;
import com.japanese.learning.entity.StudyResult;

public record StudyAnswer(
        ContentSummary content,
        StudyResult result,
        int earnedExperience,
        StudyOverview overview
) {
}
