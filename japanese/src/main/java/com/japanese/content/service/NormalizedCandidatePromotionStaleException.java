package com.japanese.content.service;

/**
 * JLPT-MAX Ticket 4E-1 hardening (MAJOR 2): thrown when a promotion POST's submitted
 * {@code expectedNormalizedAt} no longer matches the candidate's current (locked) {@code normalizedAt}
 * - the admin's detail page was rendered against a candidate revision that has since been
 * re-normalized. Mirrors {@code NormalizedCandidatePairReviewConflictException}'s shape/intent exactly,
 * kept as its own sibling type (not a subtype of {@link NormalizedCandidatePromotionRejectedException})
 * since "the wrong revision was reviewed" is a categorically different concern from "this revision is
 * not currently promotable" - see {@link NormalizedVocabularyCandidatePromotionService} for where this
 * is thrown and why it is checked before readiness is recomputed. Never thrown after any production row
 * has been written.
 */
public class NormalizedCandidatePromotionStaleException extends RuntimeException {
    public NormalizedCandidatePromotionStaleException(String message) {
        super(message);
    }
}
