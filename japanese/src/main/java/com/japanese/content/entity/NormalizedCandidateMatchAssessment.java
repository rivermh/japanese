package com.japanese.content.entity;

/**
 * The result of comparing two same-{@link NormalizedCandidateType} {@link NormalizedContentCandidate}
 * snapshots for JLPT-MAX Ticket 4B dedup/conflict analysis. This is a private-domain relationship
 * assessment only - it never triggers an automatic merge, delete, canonical-winner choice, or
 * production identity decision; it exists so a later human-review ticket (4C) has structured
 * evidence to look at.
 *
 * <p>Deliberately a separate axis from {@link NormalizedCandidateQualityState}: quality state is a
 * single candidate's own normalization-completeness signal, this is a relationship between two
 * candidates. The two are never combined into one enum.
 *
 * <p>{@link #UNIQUE} is a computed outcome (no relationship found against anything else in scope)
 * and is never itself persisted as a {@link NormalizedCandidateMatchPair} row - only a relationship
 * that was actually found ({@link #EXACT_DUPLICATE}/{@link #POSSIBLE_DUPLICATE}/{@link #CONFLICT})
 * gets a row. A candidate with zero pair rows referencing it is unique by absence, not by an
 * explicit row.
 */
public enum NormalizedCandidateMatchAssessment {

    /** No relationship was found between this pair (never persisted as a row - see class javadoc). */
    UNIQUE,

    /**
     * The two candidates' meaningful normalized semantic content is the same, making them very
     * likely the same source/domain item. Not an automatic merge, delete, or production identity
     * decision.
     */
    EXACT_DUPLICATE,

    /**
     * A strong identity-adjacent signal matches (e.g. same normalized expression+reading, or same
     * normalized pattern) but some other meaningful semantic field differs (e.g. meanings, level,
     * meaningGloss, connection, nuance) - a plausible duplicate that a human must confirm, never
     * merged automatically.
     */
    POSSIBLE_DUPLICATE,

    /**
     * The two candidates claim the same source-native identity ({@code EntryID}/{@code UnitID}) but
     * a core semantic field that identity is supposed to pin down (expression/reading, or pattern)
     * actually differs - a stronger signal than {@link #POSSIBLE_DUPLICATE} because the source data
     * itself is internally inconsistent about what that identity means.
     */
    CONFLICT
}
