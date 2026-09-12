package com.japanese.learning.dto;

import com.japanese.content.dto.ContentSummary;
import java.util.List;

public record StudyCollectionDetails(Long id, String name, List<ContentSummary> contents) {
}
