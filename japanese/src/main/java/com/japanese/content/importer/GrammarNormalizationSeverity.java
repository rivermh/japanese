package com.japanese.content.importer;

/** How urgently a {@link GrammarNormalizationWarning} needs human attention before promotion. */
public enum GrammarNormalizationSeverity {
    /** The candidate is missing content required for promotion; {@code hasNoFatalIssues} is false. */
    FATAL,
    /** The candidate can be produced but a person should look at it before it is trusted. */
    REVIEW_REQUIRED,
    /** Worth recording (e.g. schema drift) but does not by itself require review. */
    INFORMATIONAL
}
