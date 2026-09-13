package com.japanese.content.entity;

import com.japanese.account.entity.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "content_review_history")
public class ContentReviewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_item_id", nullable = false)
    private ContentItem contentItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private ReviewStatus previousStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private UserAccount reviewer;

    @Column(length = 1000)
    private String note;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected ContentReviewHistory() {
    }

    public ContentReviewHistory(ContentItem contentItem, ReviewStatus status, String note) {
        this(contentItem, null, status, null, note);
    }

    public ContentReviewHistory(ContentItem contentItem, ReviewStatus previousStatus, ReviewStatus status,
                                UserAccount reviewer, String note) {
        this.contentItem = contentItem;
        this.previousStatus = previousStatus;
        this.status = status;
        this.reviewer = reviewer;
        this.note = note;
        this.reviewedAt = Instant.now();
    }

    public ReviewStatus getStatus() {
        return status;
    }
    public ReviewStatus getPreviousStatus() { return previousStatus; }
    public UserAccount getReviewer() { return reviewer; }

    public String getNote() {
        return note;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
