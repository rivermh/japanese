package com.japanese.content.entity;

/**
 * JLPT-MAX Ticket 4E-3A: the two-state, one-way lifecycle of a
 * {@link NormalizedCandidateCanonicalGroup}. There is no intermediate/draft state and no reverse
 * transition - a group is either the currently authoritative human canonical decision for its
 * membership ({@link #ACTIVE}), or it has been superseded and no longer reserves any candidate
 * ({@link #DISSOLVED}). Correcting a canonical decision is always "dissolve this group, create a new
 * one" - never an in-place edit of an existing group's canonical candidate or membership.
 */
public enum NormalizedCandidateCanonicalGroupStatus {
    ACTIVE,
    DISSOLVED
}
