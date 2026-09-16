package com.japanese.content.importer;

/**
 * One parsed example sentence from ExamplesRendered, in original display order (1-based).
 * Deliberately has no audio field - Ticket 1.5 already strips audio markup upstream, and this
 * parser must not reintroduce an audio reference concept.
 */
public record NormalizedExample(
        int displayOrder,
        String meaningLabel,
        String japaneseText,
        String reading,
        String translation
) {
    public NormalizedExample {
        if (japaneseText == null || japaneseText.isBlank()) {
            throw new IllegalArgumentException("japaneseText is required");
        }
    }
}
