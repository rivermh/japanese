package com.japanese.content.importer;

/**
 * One "헷갈리는 문형" (confusable pattern) comparison entry from a regular-GRAMMAR note's third
 * BackHTML reference card, in original display order (1-based): a related grammar pattern that is
 * easy to confuse with this note's own pattern, plus a short explanation of how they differ.
 * Source-derived only - this is NOT the same thing as the curated/reviewed {@code GrammarRelation}/
 * {@code GrammarComparison} production domain, and this parser does not create or map to either.
 */
public record NormalizedConfusablePattern(
        int displayOrder,
        String pattern,
        String explanation
) {
    public NormalizedConfusablePattern {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern is required");
        }
    }
}
