package com.japanese.content.service;

/**
 * JLPT-MAX Ticket 4E-1: thrown by {@link NormalizedVocabularyCandidatePromotionService} whenever a
 * candidate cannot be promoted to a production draft right now - not currently
 * {@code READY_FOR_DRAFT_PROMOTION} (per the single authoritative
 * {@link NormalizedCandidatePromotionReadinessService}), already linked to a production
 * {@code ContentItem}, or of a candidate type this ticket does not promote (Grammar). Never thrown
 * after any production row has been written - see that service's class javadoc for the full
 * transaction/rollback contract.
 */
public class NormalizedCandidatePromotionRejectedException extends RuntimeException {
    public NormalizedCandidatePromotionRejectedException(String message) {
        super(message);
    }
}
