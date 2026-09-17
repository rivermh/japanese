package com.japanese.content.importer;

/**
 * One parsed example sentence from a regular-GRAMMAR note's BackHTML {@code section._j4a} block,
 * in original display order (1-based). Deliberately its own type rather than a reuse of
 * {@link NormalizedExample}: that type's {@code meaningLabel} is a Vocabulary-sense-grouping
 * concept with no Grammar equivalent (Grammar examples are never grouped by sense), so reusing it
 * would leave an always-null field with no meaning in this domain. Has no audio field - Ticket 1.5
 * already strips audio markup upstream, and this parser must not reintroduce an audio reference
 * concept.
 */
public record NormalizedGrammarExample(
        int displayOrder,
        String japaneseText,
        String reading,
        String translation
) {
    public NormalizedGrammarExample {
        if (japaneseText == null || japaneseText.isBlank()) {
            throw new IllegalArgumentException("japaneseText is required");
        }
    }
}
