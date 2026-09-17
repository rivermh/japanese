package com.japanese.content.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * JLPT-MAX Ticket 4C: pins {@link NormalizedCandidatePairReview}'s constructor invariants -
 * self-match/cross-type/cross-source-ref rejection (mirroring {@link NormalizedCandidateMatchPair}'s
 * own invariants, since this entity's identity is the same two-candidate pair) and canonical
 * left/right ordering of both the candidates and their per-candidate {@code normalizedAt} snapshots
 * regardless of argument order.
 */
class NormalizedCandidatePairReviewTest {

    @Test
    void selfReviewIsRejected() {
        NormalizedContentCandidate candidate = candidate(NormalizedCandidateType.VOCABULARY, 1L);

        assertThatIllegalArgumentException().isThrownBy(() -> new NormalizedCandidatePairReview(
                candidate, candidate, Instant.now(), Instant.now(), NormalizedCandidateMatchAssessment.EXACT_DUPLICATE,
                HumanReviewDecision.SAME_CONTENT, reviewer(), null, Instant.now()));
    }

    @Test
    void crossTypeReviewIsRejected() {
        NormalizedContentCandidate vocabulary = candidate(NormalizedCandidateType.VOCABULARY, 1L);
        NormalizedContentCandidate grammar = candidate(NormalizedCandidateType.GRAMMAR, 2L);

        assertThatIllegalArgumentException().isThrownBy(() -> new NormalizedCandidatePairReview(
                vocabulary, grammar, Instant.now(), Instant.now(), NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE,
                HumanReviewDecision.SAME_CONTENT, reviewer(), null, Instant.now()));
    }

    @Test
    void crossSourceRefReviewIsRejected() {
        NormalizedContentCandidate refA = candidate(NormalizedCandidateType.VOCABULARY, 1L, "source-a");
        NormalizedContentCandidate refB = candidate(NormalizedCandidateType.VOCABULARY, 2L, "source-b");

        assertThatIllegalArgumentException().isThrownBy(() -> new NormalizedCandidatePairReview(
                refA, refB, Instant.now(), Instant.now(), NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE,
                HumanReviewDecision.SAME_CONTENT, reviewer(), null, Instant.now()));
    }

    @Test
    void orderingIsCanonicalRegardlessOfArgumentOrderIncludingSnapshots() {
        NormalizedContentCandidate lower = candidate(NormalizedCandidateType.VOCABULARY, 5L);
        NormalizedContentCandidate higher = candidate(NormalizedCandidateType.VOCABULARY, 9L);
        Instant lowerSnapshot = Instant.parse("2026-01-01T00:00:00Z");
        Instant higherSnapshot = Instant.parse("2026-02-01T00:00:00Z");

        NormalizedCandidatePairReview ascendingArgs = new NormalizedCandidatePairReview(
                lower, higher, lowerSnapshot, higherSnapshot, NormalizedCandidateMatchAssessment.CONFLICT,
                HumanReviewDecision.DISTINCT_CONTENT, reviewer(), null, Instant.now());
        NormalizedCandidatePairReview descendingArgs = new NormalizedCandidatePairReview(
                higher, lower, higherSnapshot, lowerSnapshot, NormalizedCandidateMatchAssessment.CONFLICT,
                HumanReviewDecision.DISTINCT_CONTENT, reviewer(), null, Instant.now());

        assertThat(ascendingArgs.getLeftCandidate().getId()).isEqualTo(5L);
        assertThat(ascendingArgs.getRightCandidate().getId()).isEqualTo(9L);
        assertThat(ascendingArgs.getLeftNormalizedAtSnapshot()).isEqualTo(lowerSnapshot);
        assertThat(ascendingArgs.getRightNormalizedAtSnapshot()).isEqualTo(higherSnapshot);

        assertThat(descendingArgs.getLeftCandidate().getId()).isEqualTo(5L);
        assertThat(descendingArgs.getRightCandidate().getId()).isEqualTo(9L);
        assertThat(descendingArgs.getLeftNormalizedAtSnapshot()).isEqualTo(lowerSnapshot);
        assertThat(descendingArgs.getRightNormalizedAtSnapshot()).isEqualTo(higherSnapshot);
    }

    @Test
    void recordDecisionNeverChangesLeftOrRightCandidate() {
        NormalizedContentCandidate lower = candidate(NormalizedCandidateType.GRAMMAR, 1L);
        NormalizedContentCandidate higher = candidate(NormalizedCandidateType.GRAMMAR, 2L);
        NormalizedCandidatePairReview review = new NormalizedCandidatePairReview(
                lower, higher, Instant.now(), Instant.now(), NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE,
                HumanReviewDecision.NEEDS_FOLLOWUP, reviewer(), "first note", Instant.now());

        Instant newLeftSnapshot = Instant.now().plusSeconds(60);
        Instant newRightSnapshot = Instant.now().plusSeconds(120);
        review.recordDecision(HumanReviewDecision.SAME_CONTENT, reviewer(), "changed my mind", Instant.now(),
                newLeftSnapshot, newRightSnapshot, NormalizedCandidateMatchAssessment.EXACT_DUPLICATE);

        assertThat(review.getLeftCandidate().getId()).isEqualTo(1L);
        assertThat(review.getRightCandidate().getId()).isEqualTo(2L);
        assertThat(review.getDecision()).isEqualTo(HumanReviewDecision.SAME_CONTENT);
        assertThat(review.getNote()).isEqualTo("changed my mind");
        assertThat(review.getLeftNormalizedAtSnapshot()).isEqualTo(newLeftSnapshot);
        assertThat(review.getRightNormalizedAtSnapshot()).isEqualTo(newRightSnapshot);
        assertThat(review.getAssessmentSnapshot()).isEqualTo(NormalizedCandidateMatchAssessment.EXACT_DUPLICATE);
    }

    private UserAccount reviewer() {
        return new UserAccount("reviewer", null, "hash", "Reviewer", UserRole.ADMIN);
    }

    private NormalizedContentCandidate candidate(NormalizedCandidateType type, long id) {
        return candidate(type, id, "ref");
    }

    private NormalizedContentCandidate candidate(NormalizedCandidateType type, long id, String sourceRef) {
        NormalizedContentCandidate candidate = new NormalizedContentCandidate(type, sourceRef, id,
                "identity-" + id, NormalizedCandidateQualityState.CLEAN, Instant.now());
        try {
            Field idField = NormalizedContentCandidate.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(candidate, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return candidate;
    }
}
