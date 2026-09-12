package com.japanese.content.dto;

public record CategoryOverview(
        String slug,
        String name,
        long publicContentCount
) {
}
