package com.japanese.content.dto;

import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.service.ContentSourceRightsService;
import java.time.Instant;

public final class AdminContentSourceRightsModels {
    private AdminContentSourceRightsModels() {
    }

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
}
