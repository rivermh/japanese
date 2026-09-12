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
@Table(name = "learning_progress", uniqueConstraints = @UniqueConstraint(
        name = "uk_learning_progress_learner_content",
        columnNames = {"learner_profile_id", "content_item_id"}))
public class LearningProgress {

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
    @Column(name = "last_result", nullable = false, length = 20)
    private StudyResult lastResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_state", length = 20, columnDefinition = "varchar(20) default 'LEARNING'")
    private LearningState learningState;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "consecutive_correct", nullable = false, columnDefinition = "int default 0")
    private int consecutiveCorrect;

    @Column(name = "lapse_count", nullable = false, columnDefinition = "int default 0")
    private int lapseCount;

    @Column(name = "last_studied_at", nullable = false)
    private Instant lastStudiedAt;

    @Column(name = "next_review_at", nullable = false)
    private Instant nextReviewAt;

    protected LearningProgress() {
    }

    public LearningProgress(LearnerProfile learnerProfile, ContentItem contentItem, StudyResult result) {
        this.learnerProfile = learnerProfile;
        this.contentItem = contentItem;
        record(result);
    }

    public void record(StudyResult result) {
        reviewCount++;
        lastResult = result;
        lastStudiedAt = Instant.now();
        if (result == StudyResult.CORRECT) {
            consecutiveCorrect++;
            learningState = consecutiveCorrect >= 5 ? LearningState.MASTERED : LearningState.REVIEW;
            long intervalDays = Math.min(16, 1L << Math.min(consecutiveCorrect - 1, 4));
            nextReviewAt = lastStudiedAt.plusSeconds(intervalDays * 86_400L);
        } else {
            consecutiveCorrect = 0;
            lapseCount++;
            learningState = LearningState.LEARNING;
            nextReviewAt = lastStudiedAt.plusSeconds(600);
        }
    }

    public ContentItem getContentItem() {
        return contentItem;
    }

    public Instant getNextReviewAt() {
        return nextReviewAt;
    }

    public LearningState getLearningState() {
        if (learningState != null) {
            return learningState;
        }
        return lastResult == StudyResult.INCORRECT ? LearningState.LEARNING : LearningState.REVIEW;
    }

    public int getConsecutiveCorrect() {
        return consecutiveCorrect;
    }

    public int getLapseCount() {
        return lapseCount;
    }
}
