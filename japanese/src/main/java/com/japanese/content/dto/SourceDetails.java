package com.japanese.content.dto;

public record SourceDetails(
        String sourceRef,
        String displayName,
        String version,
        String licenseSummary,
        String licenseUrl,
        String attribution,
        String usageNote
) {
}
