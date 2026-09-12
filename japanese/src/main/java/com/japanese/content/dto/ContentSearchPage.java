package com.japanese.content.dto;

import java.util.List;

public record ContentSearchPage(
        List<ContentSummary> contents,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
