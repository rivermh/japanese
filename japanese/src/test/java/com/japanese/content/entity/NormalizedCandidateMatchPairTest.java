package com.japanese.content.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * JLPT-MAX Ticket 4B step 14/15: pins the {@link NormalizedCandidateMatchPair} constructor
 * invariants that must never depend on the caller getting things right - self-match rejection,
 * cross-{@link NormalizedCandidateType} rejection, cross-source-ref rejection (independent-review
 * follow-up, item 6), and canonical (ascending-id) left/right ordering regardless of argument order.
 */
class NormalizedCandidateMatchPairTest {

    @Test
    void selfMatchIsRejected() {
        NormalizedContentCandidate candidate = candidate(NormalizedCandidateType.VOCABULARY, 1L);

        assertThatIllegalArgumentException().isThrownBy(() -> new NormalizedCandidateMatchPair(
                candidate, candidate, NormalizedCandidateMatchAssessment.EXACT_DUPLICATE, Instant.now()));
    }

    @Test
    void crossTypeMatchIsRejected() {
        NormalizedContentCandidate vocabulary = candidate(NormalizedCandidateType.VOCABULARY, 1L);
        NormalizedContentCandidate grammar = candidate(NormalizedCandidateType.GRAMMAR, 2L);

        assertThatIllegalArgumentException().isThrownBy(() -> new NormalizedCandidateMatchPair(
                vocabulary, grammar, NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE, Instant.now()));
    }

    @Test
    void crossSourceRefMatchIsRejected() {
        NormalizedContentCandidate refA = candidate(NormalizedCandidateType.VOCABULARY, 1L, "source-a");
        NormalizedContentCandidate refB = candidate(NormalizedCandidateType.VOCABULARY, 2L, "source-b");

        assertThatIllegalArgumentException().isThrownBy(() -> new NormalizedCandidateMatchPair(
                refA, refB, NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE, Instant.now()));
    }

    @Test
    void uniqueAssessmentIsRejected() {
        NormalizedContentCandidate a = candidate(NormalizedCandidateType.VOCABULARY, 1L);
        NormalizedContentCandidate b = candidate(NormalizedCandidateType.VOCABULARY, 2L);

        assertThatIllegalArgumentException().isThrownBy(() -> new NormalizedCandidateMatchPair(
                a, b, NormalizedCandidateMatchAssessment.UNIQUE, Instant.now()));
    }

    @Test
    void orderingIsCanonicalRegardlessOfArgumentOrder() {
        NormalizedContentCandidate lower = candidate(NormalizedCandidateType.VOCABULARY, 5L);
        NormalizedContentCandidate higher = candidate(NormalizedCandidateType.VOCABULARY, 9L);

        NormalizedCandidateMatchPair ascendingArgs = new NormalizedCandidateMatchPair(
                lower, higher, NormalizedCandidateMatchAssessment.CONFLICT, Instant.now());
        NormalizedCandidateMatchPair descendingArgs = new NormalizedCandidateMatchPair(
                higher, lower, NormalizedCandidateMatchAssessment.CONFLICT, Instant.now());

        assertThat(ascendingArgs.getLeftCandidate().getId()).isEqualTo(5L);
        assertThat(ascendingArgs.getRightCandidate().getId()).isEqualTo(9L);
        assertThat(descendingArgs.getLeftCandidate().getId()).isEqualTo(5L);
        assertThat(descendingArgs.getRightCandidate().getId()).isEqualTo(9L);
    }

    @Test
    void addEvidenceAttachesBackReferenceInPositionOrder() {
        NormalizedContentCandidate a = candidate(NormalizedCandidateType.GRAMMAR, 1L);
        NormalizedContentCandidate b = candidate(NormalizedCandidateType.GRAMMAR, 2L);
        NormalizedCandidateMatchPair pair = new NormalizedCandidateMatchPair(
                a, b, NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE, Instant.now());

        NormalizedCandidateMatchEvidence first = new NormalizedCandidateMatchEvidence(
                1, NormalizedCandidateMatchEvidenceCode.SAME_PATTERN, "pattern", null);
        NormalizedCandidateMatchEvidence second = new NormalizedCandidateMatchEvidence(
                2, NormalizedCandidateMatchEvidenceCode.DIFFERENT_MEANING_GLOSS, "meaningGloss", "a=x b=y");

        pair.addEvidence(first);
        pair.addEvidence(second);

        assertThat(pair.getEvidence()).containsExactly(first, second);
        assertThat(first.getPair()).isSameAs(pair);
        assertThat(second.getPair()).isSameAs(pair);
    }

    /** Builds a candidate with a given id without persisting it (reflection sets the generated id). */
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
