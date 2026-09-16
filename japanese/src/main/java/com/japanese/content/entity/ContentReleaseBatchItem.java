package com.japanese.content.entity;

import com.japanese.content.service.ContentReleaseDecision;
import jakarta.persistence.*;

@Entity
@Table(name = "content_release_batch_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_release_batch_position", columnNames = {"batch_id", "position"}),
        @UniqueConstraint(name = "uk_release_batch_content", columnNames = {"batch_id", "content_item_id"})})
public class ContentReleaseBatchItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "batch_id", nullable = false) private ContentReleaseBatch batch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "content_item_id", nullable = false) private ContentItem contentItem;
    @Column(nullable = false) private int position;
    @Enumerated(EnumType.STRING) @Column(name = "previous_review_status", nullable = false, length = 20) private ReviewStatus previousReviewStatus;
    @Column(name = "previous_published", nullable = false) private boolean previousPublished;
    @Enumerated(EnumType.STRING) @Column(name = "resulting_review_status", nullable = false, length = 20) private ReviewStatus resultingReviewStatus;
    @Column(name = "resulting_published", nullable = false) private boolean resultingPublished;
    @Enumerated(EnumType.STRING) @Column(name = "release_decision", nullable = false, length = 32) private ContentReleaseDecision releaseDecision;
    @Column(name = "issue_codes", nullable = false, length = 4000) private String issueCodes;
    @Column(name = "manual_override_issue_codes", length = 4000) private String manualOverrideIssueCodes;
    @Column(name = "override_reason", length = 1000) private String overrideReason;
    @Column(name = "source_ref_snapshot", length = 160) private String sourceRefSnapshot;
    @Column(name = "source_rights_status_snapshot", length = 32) private String sourceRightsStatusSnapshot;
    @Column(nullable = false) private boolean success;

    protected ContentReleaseBatchItem() {}
    public ContentReleaseBatchItem(ContentItem contentItem, int position, ReviewStatus previousReviewStatus,
            boolean previousPublished, ReviewStatus resultingReviewStatus, boolean resultingPublished,
            ContentReleaseDecision releaseDecision, String issueCodes, String manualOverrideIssueCodes,
            String overrideReason, String sourceRefSnapshot, String sourceRightsStatusSnapshot) {
        this.contentItem=contentItem; this.position=position; this.previousReviewStatus=previousReviewStatus;
        this.previousPublished=previousPublished; this.resultingReviewStatus=resultingReviewStatus;
        this.resultingPublished=resultingPublished; this.releaseDecision=releaseDecision;
        this.issueCodes=issueCodes; this.manualOverrideIssueCodes=manualOverrideIssueCodes;
        this.overrideReason=overrideReason; this.sourceRefSnapshot=sourceRefSnapshot;
        this.sourceRightsStatusSnapshot=sourceRightsStatusSnapshot; this.success=true;
    }
    void attach(ContentReleaseBatch batch){if(this.batch!=null)throw new IllegalStateException("BATCH_ITEM_IMMUTABLE");this.batch=batch;}
    public Long getId(){return id;} public ContentReleaseBatch getBatch(){return batch;} public ContentItem getContentItem(){return contentItem;}
    public int getPosition(){return position;} public ReviewStatus getPreviousReviewStatus(){return previousReviewStatus;}
    public boolean isPreviousPublished(){return previousPublished;} public ReviewStatus getResultingReviewStatus(){return resultingReviewStatus;}
    public boolean isResultingPublished(){return resultingPublished;} public ContentReleaseDecision getReleaseDecision(){return releaseDecision;}
    public String getIssueCodes(){return issueCodes;} public String getManualOverrideIssueCodes(){return manualOverrideIssueCodes;}
    public String getOverrideReason(){return overrideReason;} public String getSourceRefSnapshot(){return sourceRefSnapshot;}
    public String getSourceRightsStatusSnapshot(){return sourceRightsStatusSnapshot;} public boolean isSuccess(){return success;}
}
