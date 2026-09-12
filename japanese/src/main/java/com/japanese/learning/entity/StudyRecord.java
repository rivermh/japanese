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
@Table(name = "study_records", uniqueConstraints = @UniqueConstraint(
        name = "uk_study_session_content", columnNames = {"learner_profile_id", "session_key", "content_item_id"}))
public class StudyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_profile_id", nullable = false)
    private LearnerProfile learnerProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_item_id", nullable = false)
    private ContentItem contentItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyResult result;

    @Column(name = "studied_at", nullable = false)
    private Instant studiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", length = 20)
    private StudyActivityType activityType;

    @Column(name = "session_key", length = 120)
    private String sessionKey;

    protected StudyRecord() {
    }

    public StudyRecord(LearnerProfile learnerProfile, ContentItem contentItem, StudyResult result) {
        this(learnerProfile, contentItem, result, StudyActivityType.REVIEW, null);
    }

    public StudyRecord(LearnerProfile learnerProfile, ContentItem contentItem, StudyResult result,
                       StudyActivityType activityType, String sessionKey) {
        this.learnerProfile = learnerProfile;
        this.contentItem = contentItem;
        this.result = result;
        this.studiedAt = Instant.now();
        this.activityType = activityType;
        this.sessionKey = sessionKey;
    }

    public StudyResult getResult() {
        return result;
    }

    public ContentItem getContentItem() {
        return contentItem;
    }

    public Instant getStudiedAt() {
        return studiedAt;
    }

    public StudyActivityType getActivityType() {
        return activityType;
    }
}
