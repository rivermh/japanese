package com.japanese.content.entity;

/**
 * A human reviewer's judgment about one {@link NormalizedCandidateMatchPair}, recorded by JLPT-MAX
 * Ticket 4C. This is deliberately a separate axis from {@link NormalizedCandidateMatchAssessment}
 * (Ticket 4B's machine-derived relationship signal) and from {@link NormalizedCandidateQualityState}
 * (a single candidate's own completeness signal) - recording a decision here never overwrites either
 * of those, and never itself triggers an automatic merge, candidate delete, canonical-winner
 * selection, or production promotion. It is purely a recorded human judgment for a later ticket to
 * build on.
 */
public enum HumanReviewDecision {

    /** A human reviewer judged the two candidates to represent the same underlying content. */
    SAME_CONTENT,

    /** A human reviewer judged the two candidates to be genuinely distinct content. */
    DISTINCT_CONTENT,

    /**
     * A human reviewer could not yet decide - e.g. the source data is ambiguous, needs
     * cross-checking, or the normalization itself looks suspect - and flagged the pair for later
     * follow-up rather than forcing a same/distinct call now.
     */
    NEEDS_FOLLOWUP
}
