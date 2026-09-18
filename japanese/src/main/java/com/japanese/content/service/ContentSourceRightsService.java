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
        return reviewRights(id, null, target, note, attributionRequired, attributionText);
    }

    /**
     * JLPT-MAX Ticket 4E-6 hardening: an expected-state-aware overload for callers (the HTML admin
     * form) that must guard against a stale browser render silently overwriting a NEWER rights decision
     * made by someone else since the page was loaded. {@code expectedCurrentStatus} - when non-null -
     * is compared against the persisted status only AFTER the {@code PESSIMISTIC_WRITE} lock below is
     * acquired, so the comparison always sees the true latest value, never a value some other
     * in-flight transaction might still change. A mismatch rejects the whole request with no mutation
     * at all (not even a partial one) - the entity's {@code reviewRights} is never invoked. Passing
     * {@code null} (as the existing 5-arg overload above does, preserving the JSON API's pre-existing,
     * unchanged behavior) skips this check entirely; both overloads share this single locked mutation
     * path, so there is exactly one place that performs the actual write.
     */
    @Transactional
    public ContentSource reviewRights(Long id, ContentSourceRightsStatus expectedCurrentStatus,
                                      ContentSourceRightsStatus target, String note,
                                      Boolean attributionRequired, String attributionText) {
        ContentSource source = sources.findByIdForRightsReview(id)
                .orElseThrow(() -> new NoSuchElementException("ContentSource not found: " + id));
        if (expectedCurrentStatus != null && source.getRightsStatus() != expectedCurrentStatus) {
            throw new IllegalStateException(
                    "출처 권리 상태가 화면을 연 뒤 변경되었습니다. 최신 상태를 확인한 후 다시 검토해 주세요.");
        }
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
