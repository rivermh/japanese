package com.japanese.learning.entity;

import com.japanese.content.entity.ContentItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "weakness_review_session_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_weakness_session_position", columnNames = {"session_id", "position"}),
        @UniqueConstraint(name = "uk_weakness_session_content", columnNames = {"session_id", "content_item_id"})})
public class WeaknessReviewSessionItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "session_id", nullable = false) private WeaknessReviewSession session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "content_item_id", nullable = false) private ContentItem contentItem;
    @Column(nullable = false) private int position;
    @Column(nullable = false) private boolean completed;
    @Column(name = "completed_at") private Instant completedAt;
    protected WeaknessReviewSessionItem() { }
    public WeaknessReviewSessionItem(WeaknessReviewSession session, ContentItem contentItem, int position) { this.session=session;this.contentItem=contentItem;this.position=position; }
    public void complete() { if (!completed) { completed=true; completedAt=Instant.now(); } }
    public Long getId(){return id;} public ContentItem getContentItem(){return contentItem;} public int getPosition(){return position;} public boolean isCompleted(){return completed;}
}
