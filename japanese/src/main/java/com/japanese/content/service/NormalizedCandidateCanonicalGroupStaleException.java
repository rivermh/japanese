package com.japanese.content.service;

/**
 * JLPT-MAX Ticket 4E-3A: thrown when a canonical-group creation or dissolution submission's render-time
 * evidence no longer matches the current (locked) state - a participating candidate's
 * {@code expectedNormalizedAt} no longer matches its current {@code normalizedAt}, a required pairwise
 * review's {@code expectedReviewVersion} no longer matches its current {@code @Version}, or a group
 * dissolution's {@code expectedVersion} no longer matches the group header's current {@code @Version}.
 * Mirrors {@code NormalizedCandidatePromotionStaleException}'s shape/intent exactly (kept as its own
 * sibling type, not a subtype of {@link NormalizedCandidateCanonicalGroupRejectedException}, since "the
 * admin's page was rendered against stale evidence" is a categorically different concern from "this
 * evidence, even if current, would not support the request"). Never thrown after any row has been
 * written.
 */
public class NormalizedCandidateCanonicalGroupStaleException extends RuntimeException {
    public NormalizedCandidateCanonicalGroupStaleException(String message) {
        super(message);
    }
}
