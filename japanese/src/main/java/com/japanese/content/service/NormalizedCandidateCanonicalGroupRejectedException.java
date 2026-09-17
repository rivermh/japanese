package com.japanese.content.service;

/**
 * JLPT-MAX Ticket 4E-3A: thrown by {@link NormalizedCandidateCanonicalGroupService} whenever a
 * canonical-group creation or dissolution request cannot be honored right now - a non-VOCABULARY
 * candidate, fewer than two distinct participants, a canonical id not among the participants, an
 * incomplete or contradictory C(N,2) SAME_CONTENT clique (missing/DISTINCT_CONTENT/NEEDS_FOLLOWUP/
 * stale edge), a participant already reserved by another current group, a participant already linked
 * to a production {@code ContentItem}, or an attempt to dissolve a group that is not currently ACTIVE.
 * Never thrown after any row has been written - see that service's class javadoc for the full
 * transaction/rollback contract.
 */
public class NormalizedCandidateCanonicalGroupRejectedException extends RuntimeException {
    public NormalizedCandidateCanonicalGroupRejectedException(String message) {
        super(message);
    }
}
