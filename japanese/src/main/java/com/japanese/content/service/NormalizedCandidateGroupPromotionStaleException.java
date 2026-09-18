package com.japanese.content.service;

/**
 * JLPT-MAX Ticket 4E-3B: thrown when a group-promotion submission's render-time evidence no longer
 * matches the current (locked) state - the submitted {@code expectedGroupVersion} no longer matches
 * the group header's current {@code @Version} (another admin dissolved or otherwise mutated the group
 * since this admin's page was rendered), a member candidate's current {@code normalizedAt} no longer
 * matches the value the group's own membership row recorded at group-creation time, or a group edge's
 * live review no longer matches what that edge's own immutable snapshot recorded (current pair
 * missing/stale, review no longer SAME_CONTENT, review no longer fresh, or review {@code @Version}
 * moved past the edge's own {@code pairReviewVersionSnapshot}). Mirrors
 * {@code NormalizedCandidateCanonicalGroupStaleException}'s shape/intent exactly. Never thrown after
 * any production row has been written.
 */
public class NormalizedCandidateGroupPromotionStaleException extends RuntimeException {
    public NormalizedCandidateGroupPromotionStaleException(String message) {
        super(message);
    }
}
