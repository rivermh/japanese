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

    /**
     * {@code NormalizedVocabularyMeaningLanguagePolicy} could not resolve a production
     * {@code Meaning.languageTag} for this candidate's {@code sourceRef} - either the source is not
     * the one canonical JLPT-MAX Vocabulary source this codebase has a ratified {@code "ko"} policy
     * for, or it is unrecognized entirely. Never defaulted to {@code "ko"}. Grammar candidates never
     * carry this code (Grammar has no {@code Meaning}).
     */
    VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED,

    VOCAB_EXAMPLE_TEXT_TOO_LONG,

    GRAMMAR_PATTERN_MISSING,
    GRAMMAR_PATTERN_TOO_LONG,
    GRAMMAR_CONNECTION_TOO_LONG,

    /**
     * The candidate's {@code frontExample} maps onto production {@code Example} (Ticket 4E-8
     * hardening - see {@code NormalizedGrammarCandidatePromotionService}'s javadoc): one of
     * {@code frontExampleJapaneseText}, {@code frontExampleReading}, {@code frontExampleTranslation}
     * is non-null and would exceed production {@code Example}'s 1000-char column limit. Distinct from
     * {@link #VOCAB_EXAMPLE_TEXT_TOO_LONG}, which only ever applies to Vocabulary candidates.
     */
    GRAMMAR_EXAMPLE_TEXT_TOO_LONG,

    /**
     * Production {@code Grammar.explanation} (NOT NULL, max 2000) is composed as
     * {@code meaningGloss + "\n\n" + nuance} (Ticket 4E-8's ratified mapping - see
     * {@code NormalizedCandidatePromotionReadinessService.composeGrammarExplanation}) and that
     * composed value would exceed the 2000-char column limit.
     */
    GRAMMAR_EXPLANATION_TOO_LONG,

    /**
     * {@code meaningGloss} or {@code nuance} is blank/missing, so the ratified
     * {@code meaningGloss + "\n\n" + nuance} composition cannot produce a real
     * {@code Grammar.explanation} value at all (Ticket 4E-8 hardening). Distinct from
     * {@link #GRAMMAR_EXPLANATION_TOO_LONG}, which only ever applies once a composed value exists.
     */
    GRAMMAR_EXPLANATION_SOURCE_MISSING,

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
     * There is no ratified policy for deriving a production {@code ContentItem.slug}/global identity
     * for this candidate's type. As of Ticket 4E-0, {@code ProductionContentSlugPolicy} resolves this
     * for both {@code VOCABULARY} and {@code GRAMMAR} (a source-independent opaque
     * {@code <prefix>-UUID} slug - see that class's javadoc), so this code is not expected to appear
     * for either candidate type today; it remains here only for a future candidate type this policy
     * does not yet support.
     */
    PRODUCTION_IDENTITY_POLICY_UNRESOLVED,

    /**
     * An {@code ImportedSourceRecord} already exists for this candidate's
     * {@code (sourceRef, noteType, sourceNoteId)} identity and is linked to a production
     * {@code ContentItem}. Promoting this candidate again would be redundant at best.
     */
    ALREADY_PROMOTED
}
