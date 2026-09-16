package com.japanese.content.dto;

import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.ReviewStatus;
import com.japanese.content.service.ContentReleaseDecision;
import com.japanese.content.service.ContentReleaseIssueClassification;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** API models for the read-only content release preview. */
public final class AdminContentReleaseDryRunModels {
    private AdminContentReleaseDryRunModels() {}

    public record Filter(ContentType type, String level, ReviewStatus reviewStatus, Boolean published,
                         String source, ContentSourceRightsStatus sourceRightsStatus) {}

    public record Request(ContentType type, String level, ReviewStatus reviewStatus, Boolean published,
                          String source, ContentSourceRightsStatus sourceRightsStatus) {
        public Filter filter() {
            return new Filter(type, level, reviewStatus, published, source, sourceRightsStatus);
        }
    }

    public record DecisionCounts(long releasable, long manualReviewRequired, long blocked) {}

    public record ManualTarget(Sample content, List<com.japanese.content.service.ContentReleaseGateService.Issue> issues) {}

    public record Sample(Long contentItemId, String slug, ContentType type, String expressionOrPattern,
                         String jlpt, ContentReleaseDecision decision, List<String> issueCodes,
                         String sourceRef, ContentSourceRightsStatus sourceRightsStatus) {}

    public record Result(Filter filter, String gateVersion, Instant generatedAt, long totalTargetCount,
                          List<Long> targetIds,
                          DecisionCounts decisionCounts, Map<String, Long> issueCounts,
                          Map<String, Long> classificationCounts, Map<String, Long> sourceRightsCounts,
                          long duplicateCandidateCount, List<Sample> samples,
                          List<Sample> blockedSamples, List<Sample> manualReviewSamples, String digest,
                          List<ManualTarget> manualTargets,
                          Map<String, com.japanese.content.service.ContentReleaseGateService.Issue> issueDetails) {}
}
