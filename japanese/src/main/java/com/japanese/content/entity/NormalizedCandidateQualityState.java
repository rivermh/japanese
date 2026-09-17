package com.japanese.content.entity;

import java.util.Comparator;
import java.util.List;

/**
 * A pure content-completeness signal for a {@link NormalizedContentCandidate} snapshot, computed
 * from its warnings' severities (worst-wins: declaration order above is worst-to-best). This is
 * deliberately not an approval/review-workflow state - {@code REVIEW_REQUIRED} means "a person
 * should look at this before it is trusted", not "pending human review" in the
 * {@link ReviewStatus} sense, and {@code FATAL} does not mean the row was rejected: fatal
 * candidates are always stored, never filtered out, so a future dedup/review ticket can see
 * everything a source actually produced.
 */
public enum NormalizedCandidateQualityState {
    FATAL(0),
    REVIEW_REQUIRED(1),
    INFORMATIONAL(2),
    CLEAN(3);

    private final int severityRank;

    NormalizedCandidateQualityState(int severityRank) {
        this.severityRank = severityRank;
    }

    /**
     * Worst-wins reduction over a set of states, by each state's explicit {@code severityRank}
     * (not enum declaration order/ordinal) - reordering the constants above cannot silently change
     * this method's meaning, since the rank is a value each constant carries independently.
     */
    public static NormalizedCandidateQualityState worstOf(List<NormalizedCandidateQualityState> states) {
        return states.stream().min(Comparator.comparingInt(state -> state.severityRank)).orElse(CLEAN);
    }
}
