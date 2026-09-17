package com.japanese.content.dto;

import com.japanese.content.dto.NormalizedCandidatePairReviewModels.CandidateFieldsView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.PairFreshness;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.ReviewFreshness;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.NormalizedCandidateMatchAssessment;
import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.service.PromotionReadinessIssueCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JLPT-MAX Ticket 4D: read-model DTOs for {@code NormalizedCandidatePromotionReadinessService}'s
 * read-only promotion-readiness/dry-run planner and its admin UI. Every field here is either a
 * computed value object or a copy of already-persisted private-candidate/Ticket-4B/Ticket-4C state -
 * nothing here is itself persisted, and nothing here is (or references) a production entity.
 */
public final class PromotionReadinessModels {

    private PromotionReadinessModels() {
    }

    /** The single deterministic top-level verdict for one candidate. */
    public enum OverallStatus {
        READY_FOR_DRAFT_PROMOTION,
        BLOCKED,
        ALREADY_PROMOTED
    }

    public enum PairResolutionStatus {
        RESOLVED,
        BLOCKED
    }

    /** "Draft mapping readiness" (section 12) - whether the candidate's own fields fit the production schema. */
    public enum MappingStatus {
        READY,
        BLOCKED
    }

    /**
     * Whether this codebase has a ratified production {@code ContentItem.slug}/global-identity
     * policy for candidates. Only {@link #UNRESOLVED} is ever produced today - see
     * {@link PromotionReadinessIssueCode#PRODUCTION_IDENTITY_POLICY_UNRESOLVED}.
     */
    public enum ProductionIdentityStatus {
        UNRESOLVED,
        RESOLVED
    }

    public enum ExistingProductionLinkStatus {
        NOT_LINKED,
        LINKED
    }

    public record VocabExamplePreview(String meaningLabel, String japaneseText, String reading, String translation) {
    }

    public record ConfusablePatternPreview(String pattern, String explanation) {
    }

    /** Vocabulary-only preview of the fields a draft {@code Word}/{@code Meaning}/{@code Example} would carry. */
    public record VocabularyMappingPreview(
            String expression,
            String reading,
            String partOfSpeech,
            String pitchAccentPreview,
            List<String> meanings,
            List<VocabExamplePreview> examples,
            String levelCode) {
    }

    /**
     * Grammar-only preview. {@code explanationMappingUnresolved} is always {@code true} today (see
     * {@link PromotionReadinessIssueCode#GRAMMAR_MAPPING_POLICY_UNRESOLVED}); {@code meaningGloss}/
     * {@code nuance}/{@code frontExample*}/{@code confusablePatterns} are shown for human context
     * only and are never themselves mapped onto a production field/entity by this ticket.
     */
    public record GrammarMappingPreview(
            String pattern,
            String connectionPreview,
            String meaningGloss,
            String nuance,
            String frontExampleJapaneseText,
            String frontExampleReading,
            String frontExampleTranslation,
            List<ConfusablePatternPreview> confusablePatterns,
            String rawKind,
            String levelCode,
            boolean explanationMappingUnresolved) {
    }

    /** Exactly one of {@code vocabulary}/{@code grammar} is non-null, matching the candidate's type. */
    public record MappingPreview(VocabularyMappingPreview vocabulary, GrammarMappingPreview grammar) {
    }

    public record PromotionReadinessIssue(PromotionReadinessIssueCode code, String message) {
    }

    /** One current Ticket 4B pair this candidate participates in, plus its Ticket 4C review state. */
    public record ReadinessPairView(
            Long partnerCandidateId,
            String partnerPreview,
            NormalizedCandidateMatchAssessment assessment,
            Instant pairGeneratedAt,
            PairFreshness pairFreshness,
            HumanReviewDecision decision,
            ReviewFreshness reviewFreshness,
            List<PromotionReadinessIssueCode> issuesFromThisPair) {
    }

    public record PromotionReadinessResult(
            Long candidateId,
            NormalizedCandidateType candidateType,
            String sourceRef,
            long sourceNoteId,
            String shortPreview,
            NormalizedCandidateQualityState qualityState,
            PairResolutionStatus pairResolutionStatus,
            MappingStatus mappingStatus,
            ContentSourceRightsStatus sourceRightsStatus,
            ProductionIdentityStatus productionIdentityStatus,
            ExistingProductionLinkStatus existingProductionLinkStatus,
            OverallStatus overallStatus,
            List<PromotionReadinessIssue> issues) {
        public PromotionReadinessIssueCode primaryIssueCode() {
            return issues.isEmpty() ? null : issues.get(0).code();
        }
    }

    /** {@link PromotionReadinessResult} plus everything only a single-candidate detail page needs. */
    public record PromotionReadinessDetailView(
            PromotionReadinessResult result,
            CandidateFieldsView candidateFields,
            List<ReadinessPairView> pairs,
            MappingPreview mappingPreview) {
    }

    public record ReadinessListRow(
            Long candidateId,
            NormalizedCandidateType candidateType,
            String sourceRef,
            long sourceNoteId,
            String shortPreview,
            NormalizedCandidateQualityState qualityState,
            PairResolutionStatus pairResolutionStatus,
            MappingStatus mappingStatus,
            ContentSourceRightsStatus sourceRightsStatus,
            ProductionIdentityStatus productionIdentityStatus,
            ExistingProductionLinkStatus existingProductionLinkStatus,
            OverallStatus overallStatus,
            PromotionReadinessIssueCode primaryIssueCode,
            int issueCount) {
    }

    public record ReadinessListResult(List<ReadinessListRow> rows, int page, int totalPages, long totalElements) {
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

    /**
     * {@code blockedByIssueCode} counts can sum to more than {@code blocked} - one candidate can
     * carry several issue codes at once (JLPT-MAX Ticket 4D step 27).
     */
    public record ReadinessSummary(
            NormalizedCandidateType candidateType,
            String sourceRef,
            long totalCandidates,
            long readyForDraftPromotion,
            long blocked,
            long alreadyPromoted,
            Map<String, Long> blockedByIssueCode,
            Map<String, Long> qualityBreakdown,
            Map<String, Long> pairResolutionBreakdown,
            Map<String, Long> mappingBreakdown,
            Map<String, Long> sourceRightsBreakdown,
            Map<String, Long> candidatesByBlockerCountBreakdown) {
    }
}
