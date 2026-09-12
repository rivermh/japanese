package com.japanese.learning.entity;

import com.japanese.content.entity.ContentItem;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "today_study_session_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_today_session_position", columnNames = {"session_id", "position"}),
        @UniqueConstraint(name = "uk_today_session_content", columnNames = {"session_id", "content_item_id"})})
public class TodayStudySessionItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "session_id", nullable = false) private TodayStudySession session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "content_item_id", nullable = false) private ContentItem contentItem;
    @Column(nullable = false) private int position;
    @Enumerated(EnumType.STRING) @Column(name = "planned_activity_type", nullable = false, length = 20) private StudyActivityType plannedActivityType;
    @Column(nullable = false) private boolean completed;
    @Enumerated(EnumType.STRING) @Column(length = 20) private StudyResult result;
    @Column(name = "completed_at") private Instant completedAt;
    protected TodayStudySessionItem() { }
    public TodayStudySessionItem(TodayStudySession session, ContentItem contentItem, int position, StudyActivityType type) { this.session=session;this.contentItem=contentItem;this.position=position;this.plannedActivityType=type; }
    public void complete(StudyResult result) { if (!completed) { completed=true; this.result=result; completedAt=Instant.now(); } }
    public Long getId(){return id;} public ContentItem getContentItem(){return contentItem;} public int getPosition(){return position;} public StudyActivityType getPlannedActivityType(){return plannedActivityType;} public boolean isCompleted(){return completed;}
}
