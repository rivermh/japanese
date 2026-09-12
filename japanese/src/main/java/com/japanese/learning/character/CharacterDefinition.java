package com.japanese.learning.character;

public record CharacterDefinition(
        String key,
        String displayName,
        String description,
        String assetDirectory,
        boolean illustrationsAvailable
) {
}
