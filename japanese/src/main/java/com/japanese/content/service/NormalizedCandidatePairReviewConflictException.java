package com.japanese.content.service;

/**
 * JLPT-MAX Ticket 4C: thrown when a decision submission's expected analysis state
 * (pair {@code generatedAt}/{@code assessment}, or an existing review's version) no longer matches
 * the current state - the admin's detail page was rendered against analysis that has since changed
 * (another admin re-analyzed, a candidate refreshed, or another admin already re-reviewed the same
 * pair). Never silently overwritten - the caller must reload and resubmit.
 */
public class NormalizedCandidatePairReviewConflictException extends RuntimeException {
    public NormalizedCandidatePairReviewConflictException(String message) {
        super(message);
    }
}
