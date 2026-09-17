package com.japanese.content.entity;

/**
 * Every raw-source domain a {@link NormalizedContentCandidate} can represent. Sized for what the
 * normalization parsers actually produce today (VOCABULARY, GRAMMAR) - adding a future domain
 * (e.g. IT/hotel/business/keigo, or a COMPREHENSIVE Practice/Question shape) means adding a new
 * constant plus its own detail table here, never repurposing an existing one.
 */
public enum NormalizedCandidateType {
    VOCABULARY,
    GRAMMAR
}
