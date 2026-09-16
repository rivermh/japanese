package com.japanese.content.importer;

/** One sense of a vocabulary entry's Meaning field, in original order (1-based). */
public record NormalizedMeaning(int senseOrder, String text) {
    public NormalizedMeaning {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text is required");
    }
}
