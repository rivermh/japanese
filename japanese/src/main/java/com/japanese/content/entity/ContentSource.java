package com.japanese.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "content_sources")
public class ContentSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_ref", nullable = false, unique = true, length = 160)
    private String sourceRef;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Column(length = 80)
    private String version;

    @Column(length = 500)
    private String licenseSummary;

    @Column(length = 500)
    private String licenseUrl;

    @Column(length = 2000)
    private String attribution;

    @Column(name = "attribution_required", nullable = false)
    private boolean attributionRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "rights_status", nullable = false, length = 32)
    private ContentSourceRightsStatus rightsStatus = ContentSourceRightsStatus.UNKNOWN;

    @Column(name = "rights_reviewed_at")
    private Instant rightsReviewedAt;

    @Column(name = "rights_review_note", length = 2000)
    private String rightsReviewNote;

    @Column(length = 1000)
    private String usageNote;

    protected ContentSource() {
    }

    public ContentSource(
            String sourceRef,
            String displayName,
            String version,
            String licenseSummary,
            String licenseUrl,
            String attribution,
            String usageNote
    ) {
        this.sourceRef = sourceRef;
        this.displayName = displayName;
        this.version = version;
        this.licenseSummary = licenseSummary;
        this.licenseUrl = licenseUrl;
        this.attribution = attribution;
        this.usageNote = usageNote;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getVersion() {
        return version;
    }

    public String getLicenseSummary() {
        return licenseSummary;
    }

    public String getLicenseUrl() {
        return licenseUrl;
    }

    public String getAttribution() {
        return attribution;
    }

    public boolean isAttributionRequired() {
        return attributionRequired;
    }

    public ContentSourceRightsStatus getRightsStatus() {
        return rightsStatus == null ? ContentSourceRightsStatus.UNKNOWN : rightsStatus;
    }

    public Instant getRightsReviewedAt() {
        return rightsReviewedAt;
    }

    public String getRightsReviewNote() {
        return rightsReviewNote;
    }

    public String getUsageNote() {
        return usageNote;
    }

    /**
     * JLPT-MAX Ticket 4E-6: read-only view of which target statuses are currently valid from this
     * source's own current status - reuses {@link #allowedTransitionsFrom} (the SAME authoritative set
     * {@link #canTransition} itself checks), so an admin UI can present only legal transition controls
     * without maintaining a second, independently-drifting copy of this policy. Never mutates anything;
     * safe to call from a read-only request.
     */
    public Set<ContentSourceRightsStatus> allowedNextStatuses() {
        return Set.copyOf(allowedTransitionsFrom(getRightsStatus()));
    }

    public void reviewRights(ContentSourceRightsStatus target, String note,
                             Boolean attributionRequired, String attributionText) {
        if (target == null) {
            throw new IllegalArgumentException("Source rights 상태가 필요합니다.");
        }
        String normalizedNote = clean(note);
        if (normalizedNote == null) {
            throw new IllegalArgumentException("Source rights 검토 메모가 필요합니다.");
        }
        ContentSourceRightsStatus current = getRightsStatus();
        if (!canTransition(current, target)) {
            throw new IllegalStateException("허용되지 않은 source rights 전환입니다: " + current + " -> " + target);
        }

        boolean nextAttributionRequired = attributionRequired == null
                ? this.attributionRequired
                : attributionRequired;
        String nextAttribution = attributionText == null ? this.attribution : clean(attributionText);
        if (target == ContentSourceRightsStatus.ALLOWED
                && nextAttributionRequired
                && nextAttribution == null) {
            throw new IllegalStateException("Attribution이 필요한 source는 attribution 문구 없이 ALLOWED로 전환할 수 없습니다.");
        }

        this.attributionRequired = nextAttributionRequired;
        this.attribution = nextAttribution;
        this.rightsStatus = target;
        this.rightsReviewNote = normalizedNote;
        this.rightsReviewedAt = Instant.now();
    }

    private static boolean canTransition(ContentSourceRightsStatus current, ContentSourceRightsStatus target) {
        return allowedTransitionsFrom(current).contains(target);
    }

    private static Set<ContentSourceRightsStatus> allowedTransitionsFrom(ContentSourceRightsStatus current) {
        return switch (current) {
            case UNKNOWN -> EnumSet.of(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED);
            case MANUAL_REVIEW_REQUIRED ->
                    EnumSet.of(ContentSourceRightsStatus.ALLOWED, ContentSourceRightsStatus.BLOCKED);
            case ALLOWED -> EnumSet.of(ContentSourceRightsStatus.BLOCKED);
            case BLOCKED -> EnumSet.of(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED);
        };
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
