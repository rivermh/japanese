package com.japanese.content.service;

import com.japanese.content.entity.NormalizedCandidateType;

/**
 * A transient (never persisted) summary of one {@code NormalizedCandidateConflictAnalyzer.analyze}
 * run, for logging/reporting only. {@code uniqueCount} is derived (candidateCount minus every
 * candidate that appears in at least one persisted pair), since {@code UNIQUE} itself is never
 * stored as a row.
 */
public record NormalizedCandidateAnalysisSummary(
        NormalizedCandidateType candidateType,
        String sourceRef,
        int candidateCount,
        int uniqueCount,
        int exactDuplicateCount,
        int possibleDuplicateCount,
        int conflictCount,
        int pairCount) {
}
