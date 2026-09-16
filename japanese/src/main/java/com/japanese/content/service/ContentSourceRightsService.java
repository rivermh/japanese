package com.japanese.content.service;

import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.repository.ContentSourceRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentSourceRightsService {

    private final ContentSourceRepository sources;

    public ContentSourceRightsService(ContentSourceRepository sources) {
        this.sources = sources;
    }

    @Transactional(readOnly = true)
    public ReleaseEligibility releaseEligibility(String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank()) {
            return ReleaseEligibility.blocked(null, "SOURCE_REF_MISSING", "콘텐츠 sourceRef가 없습니다.");
        }
        if (!sourceRef.equals(sourceRef.trim())) {
            return ReleaseEligibility.blocked(null, "SOURCE_REF_NOT_CANONICAL", "콘텐츠 sourceRef에 앞뒤 공백이 있습니다.");
        }
        return sources.findBySourceRef(sourceRef)
                .map(this::releaseEligibilityForSource)
                .orElseGet(() -> ReleaseEligibility.blocked(
                        null, "SOURCE_NOT_REGISTERED", "등록된 ContentSource가 없습니다: " + sourceRef));
    }

    public boolean isAllowedForRelease(String sourceRef) {
        return releaseEligibility(sourceRef).allowed();
    }

    public String explainBlockingReason(String sourceRef) {
        return releaseEligibility(sourceRef).blockingReason();
    }

    @Transactional(readOnly = true)
    public List<ContentSource> sources() {
        return sources.findAllByOrderByDisplayNameAsc();
    }

    @Transactional(readOnly = true)
    public ContentSource source(Long id) {
        return sources.findById(id).orElseThrow(() -> new NoSuchElementException("ContentSource not found: " + id));
    }

    @Transactional
    public ContentSource reviewRights(Long id, ContentSourceRightsStatus target, String note,
                                      Boolean attributionRequired, String attributionText) {
        ContentSource source = sources.findByIdForRightsReview(id)
                .orElseThrow(() -> new NoSuchElementException("ContentSource not found: " + id));
        source.reviewRights(target, note, attributionRequired, attributionText);
        return source;
    }

    public ReleaseEligibility releaseEligibilityForSource(ContentSource source) {
        ContentSourceRightsStatus status = source.getRightsStatus();
        if (status != ContentSourceRightsStatus.ALLOWED) {
            return ReleaseEligibility.blocked(
                    status, "RIGHTS_NOT_ALLOWED", "Source rights 상태가 ALLOWED가 아닙니다: " + status);
        }
        if (source.isAttributionRequired()
                && (source.getAttribution() == null || source.getAttribution().isBlank())) {
            return ReleaseEligibility.blocked(
                    status, "ATTRIBUTION_MISSING", "필수 attribution 문구가 없습니다.");
        }
        return new ReleaseEligibility(true, status, null, null);
    }

    public record ReleaseEligibility(
            boolean allowed,
            ContentSourceRightsStatus rightsStatus,
            String blockingCode,
            String blockingReason
    ) {
        private static ReleaseEligibility blocked(
                ContentSourceRightsStatus status, String code, String reason) {
            return new ReleaseEligibility(false, status, code, reason);
        }
    }
}
