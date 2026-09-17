package com.japanese.content.dto;

import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.NormalizedCandidateMatchAssessment;
import com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode;
import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import java.time.Instant;
import java.util.List;

/**
 * Read-model DTOs for JLPT-MAX Ticket 4C's private-candidate human review admin UI. These never
 * appear anywhere in the production admin-review models ({@code AdminContentReviewModels}) and never
 * carry a production entity reference - only {@link NormalizedContentCandidateFields} shaped from the
 * private Ticket 4A/4B candidate/pair/evidence entities.
 */
public final class NormalizedCandidatePairReviewModels {

    private NormalizedCandidatePairReviewModels() {
    }

    /** Whether the current Ticket 4B pair row is still safe to record a new decision against. */
    public enum PairFreshness {
        FRESH, STALE
    }

    /**
     * Whether an existing {@code NormalizedCandidatePairReview} is still valid for the current
     * candidate/pair state. {@code NOT_REVIEWED} is a computed absence, not a stored row (JLPT-MAX
     * Ticket 4C).
     */
    public enum ReviewFreshness {
        NOT_REVIEWED, FRESH, STALE, ANALYSIS_NO_LONGER_PRESENT
    }

    public record ReviewListRow(
            Long leftCandidateId,
            Long rightCandidateId,
            NormalizedCandidateType candidateType,
            String sourceRef,
            NormalizedCandidateMatchAssessment assessment,
            Instant pairGeneratedAt,
            PairFreshness pairFreshness,
            NormalizedCandidateQualityState worstQualityState,
            String leftPreview,
            String rightPreview,
            HumanReviewDecision decision,
            ReviewFreshness reviewFreshness,
            String reviewerDisplayName,
            Instant reviewedAt) {
    }

    public record ReviewListResult(
            List<ReviewListRow> rows,
            int page,
            int totalPages,
            long totalElements) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }

        public boolean hasPrevious() {
            return page > 0;
        }

        public boolean hasNext() {
            return page + 1 < totalPages;
        }
    }

    public record VocabExampleView(String meaningLabel, String japaneseText, String reading, String translation) {
    }

    public record ConfusablePatternView(String pattern, String explanation) {
    }

    public record WarningView(int position, String issueCode, String severity, String message) {
    }

    /**
     * The full persisted fields of one side of a pair - deliberately independent of
     * {@code NormalizedCandidateMatchEvidence.detail} (a reviewer-convenience excerpt that may be
     * truncated, per {@code NormalizedCandidateConflictAnalyzer}'s {@code EVIDENCE_EXCERPT_MAX_LENGTH})
     * so a Ticket 4C reviewer always has the option to check the real, untruncated candidate data
     * rather than judging from evidence text alone (JLPT-MAX Ticket 4C).
     */
    public record CandidateFieldsView(
            Long candidateId,
            NormalizedCandidateType candidateType,
            String sourceRef,
            long sourceNoteId,
            String sourceIdentityKey,
            NormalizedCandidateQualityState qualityState,
            Instant normalizedAt,
            List<WarningView> warnings,
            // Vocabulary-only (null for Grammar candidates)
            String entryId,
            String expression,
            String reading,
            String partOfSpeech,
            String pitchAccentTerminalStates,
            String pitchAccentMora,
            String levelCode,
            List<String> meanings,
            List<VocabExampleView> examples,
            // Grammar-only (null for Vocabulary candidates)
            String unitId,
            String pattern,
            String meaningGloss,
            String nuance,
            String connectionForm,
            String frontExampleJapaneseText,
            String frontExampleReading,
            String frontExampleTranslation,
            String rawKind,
            List<ConfusablePatternView> confusablePatterns) {
    }

    public record EvidenceView(int position, NormalizedCandidateMatchEvidenceCode evidenceCode, String fieldName,
            String detail) {
    }

    public record CurrentReviewView(HumanReviewDecision decision, String reviewerDisplayName, String note,
            Instant reviewedAt, ReviewFreshness freshness, long version) {
    }

    public record HistoryEntryView(HumanReviewDecision previousDecision, HumanReviewDecision newDecision,
            String reviewerDisplayName, String note, Instant reviewedAt,
            NormalizedCandidateMatchAssessment assessmentSnapshot) {
    }

    public record ReviewDetailView(
            NormalizedCandidateType candidateType,
            String sourceRef,
            Long leftCandidateId,
            Long rightCandidateId,
            NormalizedCandidateMatchAssessment assessment,
            Instant pairGeneratedAt,
            PairFreshness pairFreshness,
            boolean analysisPresent,
            List<EvidenceView> evidence,
            CandidateFieldsView left,
            CandidateFieldsView right,
            CurrentReviewView currentReview,
            List<HistoryEntryView> history) {
        public boolean decisionAllowed() {
            return analysisPresent && pairFreshness == PairFreshness.FRESH;
        }
    }

    public record DecisionSubmission(
            Long leftCandidateId,
            Long rightCandidateId,
            HumanReviewDecision decision,
            String note,
            Instant expectedPairGeneratedAt,
            NormalizedCandidateMatchAssessment expectedAssessment,
            Long expectedReviewVersion) {
    }
}
