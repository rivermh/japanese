package com.japanese.content.importer;

/** How urgently a {@link VocabularyNormalizationWarning} needs human attention before promotion. */
public enum VocabularyNormalizationSeverity {
    /** The candidate is missing content required for promotion; {@code validForPromotion} is false. */
    FATAL,
    /** The candidate can be produced but a person should look at it before it is trusted. */
    REVIEW_REQUIRED,
    /** Worth recording (e.g. schema drift) but does not by itself require review. */
    INFORMATIONAL
}
