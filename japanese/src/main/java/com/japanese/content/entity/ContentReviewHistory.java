package com.japanese.content.entity;

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

    @Column(length = 1000)
    private String note;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected ContentReviewHistory() {
    }

    public ContentReviewHistory(ContentItem contentItem, ReviewStatus status, String note) {
        this.contentItem = contentItem;
        this.status = status;
        this.note = note;
        this.reviewedAt = Instant.now();
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
