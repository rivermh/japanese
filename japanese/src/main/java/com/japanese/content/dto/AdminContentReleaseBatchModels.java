package com.japanese.content.dto;

import com.japanese.content.entity.*;
import com.japanese.content.service.ContentReleaseDecision;
import com.japanese.content.service.ContentReleaseIssueCode;
import java.time.Instant;
import java.util.List;

public final class AdminContentReleaseBatchModels {
    private AdminContentReleaseBatchModels() {}
    public record ManualOverride(Long contentItemId, List<ContentReleaseIssueCode> acknowledgedIssueCodes, String reason) {}
    public record ExecuteRequest(List<Long> targetIds, String digest, String gateVersion,
                                 ContentReleaseMode mode, String note, List<ManualOverride> manualOverrides) {}
    public record RollbackRequest(String reason) {}
    public record BatchSummary(Long id, ContentReleaseBatchStatus status, ContentReleaseMode mode,
                               Instant executedAt, String reviewer, int targetCount, String digest, Instant rolledBackAt) {}
    public record ItemView(Long contentItemId, int position, ReviewStatus previousReviewStatus,
                           boolean previousPublished, ReviewStatus resultingReviewStatus,
                           boolean resultingPublished, ContentReleaseDecision releaseDecision,
                           List<String> issueCodes, List<String> manualOverrideIssueCodes,
                           String overrideReason, String sourceRef, String sourceRightsStatus, boolean success) {}
    public record BatchView(Long id, ContentReleaseBatchStatus status, ContentReleaseMode mode,
                            String gateVersion, String previewDigest, String executionNote,
                            int targetCount, int successCount, int releasableCount,
                            int manualOverrideCount, int blockedCount, String reviewer,
                            Instant createdAt, Instant executedAt, Instant rolledBackAt,
                            String rollbackReason, List<ItemView> items) {}
}
