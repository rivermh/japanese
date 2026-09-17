package com.japanese.content.service;

/**
 * JLPT-MAX Ticket 4D: a structured reason one {@code NormalizedContentCandidate} is not currently
 * {@code READY_FOR_DRAFT_PROMOTION}, as computed by {@link NormalizedCandidatePromotionReadinessService}.
 * Declaration order here is also this ticket's fixed, deterministic display/sort order for a
 * candidate's issue list - never derived from map/set iteration order.
 *
 * <p>This is a read-only planning signal only. No code in this ticket transitions a candidate,
 * writes a production row, or otherwise acts on any of these codes - see
 * {@link NormalizedCandidatePromotionReadinessService}'s class javadoc for the full boundary.
 */
public enum PromotionReadinessIssueCode {

    /** {@code NormalizedCandidateQualityState.FATAL} - Ticket 4A normalization found a fatal gap. */
    NORMALIZATION_FATAL,

    /** {@code NormalizedCandidateQualityState.REVIEW_REQUIRED} - a person should look at this candidate first. */
    NORMALIZATION_REVIEW_REQUIRED,

    /**
     * A current Ticket 4B pair this candidate participates in has {@code generatedAt} older than
     * one of its two candidates' {@code normalizedAt} - the machine analysis itself is out of date
     * and must be rerun before any human decision about it can be trusted.
     */
    PAIR_ANALYSIS_STALE,

    /** A current, fresh Ticket 4B pair this candidate participates in has no Ticket 4C review row at all. */
    PAIR_UNREVIEWED,

    /**
     * A current, fresh Ticket 4B pair this candidate participates in has a Ticket 4C review, but
     * that review's snapshot no longer matches the current candidates/assessment (STALE).
     */
    PAIR_REVIEW_STALE,

    /** A human reviewer flagged a pair this candidate participates in as {@code NEEDS_FOLLOWUP}. */
    PAIR_NEEDS_FOLLOWUP,

    /**
     * A human reviewer judged a pair this candidate participates in to be {@code SAME_CONTENT}.
     * Ticket 4C never selects a canonical winner or merge strategy, so a candidate on either side of
     * a {@code SAME_CONTENT} pair is never promotion-ready on its own - see
     * {@link NormalizedCandidatePromotionReadinessService}'s class javadoc.
     */
    SAME_CONTENT_CANONICAL_SELECTION_REQUIRED,

    VOCAB_EXPRESSION_MISSING,
    VOCAB_EXPRESSION_TOO_LONG,
    VOCAB_READING_MISSING,
    VOCAB_READING_TOO_LONG,
    VOCAB_PART_OF_SPEECH_TOO_LONG,

    /**
     * The {@code "terminal="+terminalStates+";mora="+mora} serialization (the one existing production
     * {@code Word.pitchAccent} writer's format - see {@code ApkgVocabularyImporter.extractPitchAccent})
     * would exceed {@code Word.pitchAccent}'s 500-char column limit.
     */
    VOCAB_PITCH_ACCENT_TOO_LONG,

    VOCAB_MEANING_MISSING,
    VOCAB_MEANING_TOO_LONG,
    VOCAB_EXAMPLE_TEXT_TOO_LONG,

    GRAMMAR_PATTERN_MISSING,
    GRAMMAR_PATTERN_TOO_LONG,
    GRAMMAR_CONNECTION_TOO_LONG,

    /**
     * Production {@code Grammar.explanation} (NOT NULL, max 2000) has no defined source mapping from
     * a normalized Grammar candidate's {@code meaningGloss}/{@code nuance}/{@code frontExample}
     * fields - Ticket 3B-1 deliberately left this a promotion-time decision (see
     * {@code GrammarNormalizationResult}'s class javadoc), and this ticket does not invent one. Also
     * covers the equally-undecided {@code frontExample}/{@code confusablePatterns} -&gt; production
     * {@code Example}/{@code GrammarRelation}/{@code GrammarComparison} mapping. Every Grammar
     * candidate carries this code today - that is an expected finding, not a bug.
     */
    GRAMMAR_MAPPING_POLICY_UNRESOLVED,

    /**
     * No JLPT level code, no matching {@code Level(system="JLPT", code=...)} row exists yet, or more
     * than one such row exists for the same {@code code} (the {@code (system, code)} pair has no
     * database-level uniqueness constraint today). In the duplicate case the candidate is unmappable
     * because it cannot be resolved to a single production {@code Level} - this ticket never guesses
     * which duplicate row is the "real" one.
     */
    JLPT_LEVEL_UNMAPPABLE,

    /** No {@code ContentSource} row exists for this candidate's {@code sourceRef}. */
    SOURCE_NOT_REGISTERED,

    /** {@code ContentSource.rightsStatus == BLOCKED} for this candidate's {@code sourceRef}. */
    SOURCE_RIGHTS_NOT_ALLOWED,

    /**
     * {@code ContentSource.rightsStatus} is {@code UNKNOWN} or {@code MANUAL_REVIEW_REQUIRED}, or
     * rights are otherwise not yet cleared for release (e.g. required attribution text is missing).
     */
    SOURCE_RIGHTS_MANUAL_REVIEW,

    /**
     * There is no ratified policy in this codebase for deriving a production {@code ContentItem.slug}
     * / global identity from a private candidate. Every candidate carries this code today, by design
     * - see {@link NormalizedCandidatePromotionReadinessService}'s class javadoc. Never resolved by
     * this ticket.
     */
    PRODUCTION_IDENTITY_POLICY_UNRESOLVED,

    /**
     * An {@code ImportedSourceRecord} already exists for this candidate's
     * {@code (sourceRef, noteType, sourceNoteId)} identity and is linked to a production
     * {@code ContentItem}. Promoting this candidate again would be redundant at best.
     */
    ALREADY_PROMOTED
}
