package com.japanese.content.service;

/**
 * JLPT-MAX Ticket 4E-3B: thrown by {@link NormalizedCandidateGroupPromotionService} whenever a
 * group-promotion attempt cannot be honored right now - the group is not {@code ACTIVE}, a
 * non-VOCABULARY group, a member's group-aware readiness is not satisfied (canonical missing an
 * ordinary readiness requirement, or a non-canonical member missing a non-mapping requirement), any
 * member already has production-linked provenance, or no truthful raw provenance is available for a
 * member that needs a new {@code ImportedSourceRecord}. Never thrown after any production row has
 * been written - see that service's class javadoc for the full transaction/rollback contract.
 */
public class NormalizedCandidateGroupPromotionRejectedException extends RuntimeException {
    public NormalizedCandidateGroupPromotionRejectedException(String message) {
        super(message);
    }
}
