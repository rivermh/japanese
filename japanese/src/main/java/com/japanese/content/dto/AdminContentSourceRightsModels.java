package com.japanese.content.dto;

import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.service.ContentSourceRightsService;
import java.time.Instant;
import java.util.Set;

public final class AdminContentSourceRightsModels {
    private AdminContentSourceRightsModels() {
    }

    /**
     * The JSON API view - {@link com.japanese.content.controller.AdminContentSourceRightsApiController}
     * serializes exactly this record. Its field set is the established API contract and must not gain
     * or lose fields for the sake of the HTML admin UI; see {@link SourceRightsAdminView} for that.
     */
    public record SourceRightsView(
            Long id,
            String sourceRef,
            String displayName,
            String version,
            String licenseSummary,
            String licenseUrl,
            ContentSourceRightsStatus rightsStatus,
            Instant rightsReviewedAt,
            String rightsReviewNote,
            boolean attributionRequired,
            String attributionText,
            boolean allowedForRelease,
            String blockingReason
    ) {
        public static SourceRightsView from(
                ContentSource source,
                ContentSourceRightsService.ReleaseEligibility eligibility) {
            return new SourceRightsView(
                    source.getId(),
                    source.getSourceRef(),
                    source.getDisplayName(),
                    source.getVersion(),
                    source.getLicenseSummary(),
                    source.getLicenseUrl(),
                    source.getRightsStatus(),
                    source.getRightsReviewedAt(),
                    source.getRightsReviewNote(),
                    source.isAttributionRequired(),
                    source.getAttribution(),
                    eligibility.allowed(),
                    eligibility.blockingReason());
        }
    }

    /**
     * JLPT-MAX Ticket 4E-6: an HTML-only view model for the server-rendered admin rights screens
     * ({@code content-source-rights-list.html} / {@code content-source-rights-detail.html}). Adds
     * {@code usageNote} and {@code allowedNextStatuses} on top of {@link SourceRightsView}'s field set -
     * fields the HTML templates need but that must never appear in the JSON API response, so they live
     * on this separate record instead of being added to {@link SourceRightsView}.
     */
    public record SourceRightsAdminView(
            Long id,
            String sourceRef,
            String displayName,
            String version,
            String licenseSummary,
            String licenseUrl,
            String usageNote,
            ContentSourceRightsStatus rightsStatus,
            Instant rightsReviewedAt,
            String rightsReviewNote,
            boolean attributionRequired,
            String attributionText,
            boolean allowedForRelease,
            String blockingReason,
            Set<ContentSourceRightsStatus> allowedNextStatuses
    ) {
        public static SourceRightsAdminView from(
                ContentSource source,
                ContentSourceRightsService.ReleaseEligibility eligibility) {
            return new SourceRightsAdminView(
                    source.getId(),
                    source.getSourceRef(),
                    source.getDisplayName(),
                    source.getVersion(),
                    source.getLicenseSummary(),
                    source.getLicenseUrl(),
                    source.getUsageNote(),
                    source.getRightsStatus(),
                    source.getRightsReviewedAt(),
                    source.getRightsReviewNote(),
                    source.isAttributionRequired(),
                    source.getAttribution(),
                    eligibility.allowed(),
                    eligibility.blockingReason(),
                    source.allowedNextStatuses());
        }
    }
}
