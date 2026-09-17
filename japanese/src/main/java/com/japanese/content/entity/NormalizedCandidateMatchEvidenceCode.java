package com.japanese.content.entity;

/**
 * A single structured comparison outcome recorded as one {@link NormalizedCandidateMatchEvidence}
 * row supporting a {@link NormalizedCandidateMatchPair}'s {@link NormalizedCandidateMatchAssessment}.
 * Every field this ticket's comparator actually reads gets exactly one SAME/DIFFERENT pair here -
 * sized to the actual JLPT-MAX v2.1.1 profiling (Ticket 4B step 8/29), not a speculative superset.
 * Vocabulary comparisons only ever emit the ENTRY_ID/EXPRESSION/READING/MEANING/LEVEL/
 * PART_OF_SPEECH family of codes; Grammar comparisons only ever emit the UNIT_ID/PATTERN/LEVEL/
 * MEANING_GLOSS/CONNECTION/NUANCE family (both share LEVEL).
 */
public enum NormalizedCandidateMatchEvidenceCode {
    SAME_ENTRY_ID,
    DIFFERENT_ENTRY_ID,
    SAME_UNIT_ID,
    DIFFERENT_UNIT_ID,
    SAME_EXPRESSION,
    DIFFERENT_EXPRESSION,
    SAME_READING,
    DIFFERENT_READING,
    SAME_PATTERN,
    DIFFERENT_PATTERN,
    SAME_LEVEL,
    DIFFERENT_LEVEL,
    SAME_MEANING,
    DIFFERENT_MEANING,
    SAME_MEANING_GLOSS,
    DIFFERENT_MEANING_GLOSS,
    SAME_CONNECTION,
    DIFFERENT_CONNECTION,
    SAME_NUANCE,
    DIFFERENT_NUANCE,
    SAME_PART_OF_SPEECH,
    DIFFERENT_PART_OF_SPEECH
}
