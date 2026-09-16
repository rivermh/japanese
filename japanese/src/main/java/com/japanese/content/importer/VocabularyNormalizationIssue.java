package com.japanese.content.importer;

import static com.japanese.content.importer.VocabularyNormalizationSeverity.FATAL;
import static com.japanese.content.importer.VocabularyNormalizationSeverity.INFORMATIONAL;
import static com.japanese.content.importer.VocabularyNormalizationSeverity.REVIEW_REQUIRED;

/**
 * Every distinct problem {@link VocabularyNormalizationParser} can detect while turning a raw
 * private-staging vocabulary note into a {@link VocabularyNormalizationResult}. Severity is fixed
 * per issue so callers never have to guess whether a given code is blocking.
 */
public enum VocabularyNormalizationIssue {
    MISSING_ENTRY_ID(FATAL),
    MISSING_EXPRESSION(FATAL),
    MISSING_READING(FATAL),
    MISSING_MEANING(FATAL),
    EMPTY_PART_OF_SPEECH(REVIEW_REQUIRED),
    INVALID_JLPT_LEVEL(REVIEW_REQUIRED),
    EXAMPLE_PARSE_FALLBACK(REVIEW_REQUIRED),
    PITCH_ACCENT_PARSE_FAILED(REVIEW_REQUIRED),
    AUDIO_REFERENCE_UNEXPECTED(REVIEW_REQUIRED),
    UNKNOWN_EXTRA_FIELD(INFORMATIONAL);

    private final VocabularyNormalizationSeverity severity;

    VocabularyNormalizationIssue(VocabularyNormalizationSeverity severity) {
        this.severity = severity;
    }

    public VocabularyNormalizationSeverity severity() {
        return severity;
    }
}
