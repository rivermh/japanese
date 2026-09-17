package com.japanese.content.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static com.japanese.content.entity.NormalizedCandidateQualityState.CLEAN;
import static com.japanese.content.entity.NormalizedCandidateQualityState.FATAL;
import static com.japanese.content.entity.NormalizedCandidateQualityState.INFORMATIONAL;
import static com.japanese.content.entity.NormalizedCandidateQualityState.REVIEW_REQUIRED;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the worst-wins precedence (FATAL > REVIEW_REQUIRED > INFORMATIONAL > CLEAN) independently
 * of enum declaration order, since {@link NormalizedCandidateQualityState#worstOf} is keyed off an
 * explicit per-constant rank rather than ordinal/natural ordering. */
class NormalizedCandidateQualityStateTest {

    @Test
    void noStatesIsClean() {
        assertThat(NormalizedCandidateQualityState.worstOf(List.of())).isEqualTo(CLEAN);
    }

    @Test
    void informationalOnlyIsInformational() {
        assertThat(NormalizedCandidateQualityState.worstOf(List.of(INFORMATIONAL))).isEqualTo(INFORMATIONAL);
    }

    @Test
    void reviewRequiredOnlyIsReviewRequired() {
        assertThat(NormalizedCandidateQualityState.worstOf(List.of(REVIEW_REQUIRED))).isEqualTo(REVIEW_REQUIRED);
    }

    @Test
    void fatalOnlyIsFatal() {
        assertThat(NormalizedCandidateQualityState.worstOf(List.of(FATAL))).isEqualTo(FATAL);
    }

    @Test
    void informationalPlusReviewRequiredIsReviewRequired() {
        assertThat(NormalizedCandidateQualityState.worstOf(List.of(INFORMATIONAL, REVIEW_REQUIRED)))
                .isEqualTo(REVIEW_REQUIRED);
    }

    @Test
    void reviewRequiredPlusFatalIsFatal() {
        assertThat(NormalizedCandidateQualityState.worstOf(List.of(REVIEW_REQUIRED, FATAL))).isEqualTo(FATAL);
    }

    @Test
    void informationalPlusFatalIsFatal() {
        assertThat(NormalizedCandidateQualityState.worstOf(List.of(INFORMATIONAL, FATAL))).isEqualTo(FATAL);
    }
}
