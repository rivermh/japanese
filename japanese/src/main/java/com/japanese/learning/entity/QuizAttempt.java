package com.japanese.learning.entity;

import com.japanese.content.entity.ImportedSourceRecord;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_profile_id", nullable = false)
    private LearnerProfile learnerProfile;

    @Column(name = "question_source_record_id", nullable = false)
    private Long questionSourceRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", nullable = false, length = 32)
    private QuizAttemptOriginType originType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_source_record_id")
    private ImportedSourceRecord importedSourceRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_session_item_id")
    private QuizSessionItem quizSessionItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyResult result;

    @Column(name = "earned_experience", nullable = false)
    private int earnedExperience;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    /** Null keeps attempts created before Quiz 2.0 eligible for the legacy streak policy. */
    @Column(name = "streak_eligible")
    private Boolean streakEligible = true;

    protected QuizAttempt() {
    }

    public static QuizAttempt forImportedQuestion(LearnerProfile learnerProfile,
            ImportedSourceRecord question, StudyResult result, int earnedExperience) {
        if (question == null || question.getId() == null) {
            throw new IllegalArgumentException("Imported quiz question must be persisted");
        }
        return new QuizAttempt(learnerProfile, question.getId(), QuizAttemptOriginType.IMPORTED_SOURCE,
                question, null, result, earnedExperience, true);
    }

    public static QuizAttempt forSessionItem(LearnerProfile learnerProfile,
            QuizSessionItem item, StudyResult result, int earnedExperience) {
        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException("Quiz session item must be persisted");
        }
        if (learnerProfile == null || learnerProfile.getId() == null
                || !learnerProfile.getId().equals(item.getSession().learnerProfileId())) {
            throw new IllegalArgumentException("Quiz session item belongs to another learner");
        }
        return new QuizAttempt(learnerProfile, item.getId(), QuizAttemptOriginType.QUIZ_SESSION_ITEM,
                null, item, result, earnedExperience, false);
    }

    /** Compatibility path for callers whose historic reference cannot be proven. */
    public static QuizAttempt forLegacyUnknown(LearnerProfile learnerProfile, Long referenceId,
            StudyResult result, int earnedExperience, boolean streakEligible) {
        return new QuizAttempt(learnerProfile, referenceId, QuizAttemptOriginType.LEGACY_UNKNOWN,
                null, null, result, earnedExperience, streakEligible);
    }

    private QuizAttempt(LearnerProfile learnerProfile, Long questionSourceRecordId,
            QuizAttemptOriginType originType, ImportedSourceRecord importedSourceRecord,
            QuizSessionItem quizSessionItem, StudyResult result, int earnedExperience,
            boolean streakEligible) {
        this.learnerProfile = Objects.requireNonNull(learnerProfile, "learnerProfile");
        this.questionSourceRecordId = Objects.requireNonNull(questionSourceRecordId, "questionSourceRecordId");
        this.originType = Objects.requireNonNull(originType, "originType");
        this.importedSourceRecord = importedSourceRecord;
        this.quizSessionItem = quizSessionItem;
        this.result = result;
        this.earnedExperience = earnedExperience;
        this.answeredAt = Instant.now();
        this.streakEligible = streakEligible;
    }
    @PrePersist
    @PreUpdate
    void validateProvenance() {
        boolean imported = importedSourceRecord != null;
        boolean session = quizSessionItem != null;
        if (originType == null || imported && session
                || originType == QuizAttemptOriginType.IMPORTED_SOURCE && (!imported || session)
                || originType == QuizAttemptOriginType.QUIZ_SESSION_ITEM && (imported || !session)
                || originType == QuizAttemptOriginType.LEGACY_UNKNOWN && (imported || session)) {
            throw new IllegalStateException("Quiz attempt provenance is inconsistent");
        }
    }
    public Long getQuestionSourceRecordId() { return questionSourceRecordId; }
    public QuizAttemptOriginType getOriginType() { return originType; }
    public ImportedSourceRecord getImportedSourceRecord() { return importedSourceRecord; }
    public QuizSessionItem getQuizSessionItem() { return quizSessionItem; }
    public StudyResult getResult() { return result; }
    public int getEarnedExperience() { return earnedExperience; }
    public Instant getAnsweredAt() { return answeredAt; }
    public boolean isStreakEligible() { return streakEligible == null || streakEligible; }
}
