package com.japanese.content.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Pins that a candidate's detail can only be attached to a candidate of the matching
 * {@link NormalizedCandidateType}, since {@link NormalizedContentCandidate#attachVocabularyDetail}
 * and {@link NormalizedContentCandidate#attachGrammarDetail} are public and otherwise take no
 * detail-type guard against a mismatched envelope. */
class NormalizedContentCandidateTest {

    @Test
    void attachingGrammarDetailToVocabularyCandidateFails() {
        NormalizedContentCandidate candidate = new NormalizedContentCandidate(NormalizedCandidateType.VOCABULARY,
                "ref", 1L, "entry-1", NormalizedCandidateQualityState.CLEAN, Instant.now());

        assertThatIllegalStateException().isThrownBy(() -> candidate.attachGrammarDetail(
                new NormalizedGrammarCandidateDetail("unit-1", "pattern", null, null, null, null, null, null, null,
                        null, null, null, null)));
    }

    @Test
    void attachingVocabularyDetailToGrammarCandidateFails() {
        NormalizedContentCandidate candidate = new NormalizedContentCandidate(NormalizedCandidateType.GRAMMAR,
                "ref", 1L, "unit-1", NormalizedCandidateQualityState.CLEAN, Instant.now());

        assertThatIllegalStateException().isThrownBy(() -> candidate.attachVocabularyDetail(
                new NormalizedVocabularyCandidateDetail("entry-1", "expr", "reading", "pos", null, null, null, null,
                        null, null, null)));
    }

    @Test
    void attachingMatchingDetailsSucceeds() {
        NormalizedContentCandidate vocabularyCandidate = new NormalizedContentCandidate(
                NormalizedCandidateType.VOCABULARY, "ref", 1L, "entry-1", NormalizedCandidateQualityState.CLEAN,
                Instant.now());
        NormalizedVocabularyCandidateDetail vocabularyDetail = new NormalizedVocabularyCandidateDetail("entry-1",
                "expr", "reading", "pos", null, null, null, null, null, null, null);
        vocabularyCandidate.attachVocabularyDetail(vocabularyDetail);
        assertThat(vocabularyCandidate.getVocabularyDetail()).isSameAs(vocabularyDetail);

        NormalizedContentCandidate grammarCandidate = new NormalizedContentCandidate(NormalizedCandidateType.GRAMMAR,
                "ref", 2L, "unit-1", NormalizedCandidateQualityState.CLEAN, Instant.now());
        NormalizedGrammarCandidateDetail grammarDetail = new NormalizedGrammarCandidateDetail("unit-1", "pattern",
                null, null, null, null, null, null, null, null, null, null, null);
        grammarCandidate.attachGrammarDetail(grammarDetail);
        assertThat(grammarCandidate.getGrammarDetail()).isSameAs(grammarDetail);
    }
}
