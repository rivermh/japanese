package com.japanese.learning.entity;

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

    public QuizAttempt(LearnerProfile learnerProfile, Long questionSourceRecordId,
                       StudyResult result, int earnedExperience) {
        this(learnerProfile, questionSourceRecordId, result, earnedExperience, true);
    }

    public QuizAttempt(LearnerProfile learnerProfile, Long questionSourceRecordId,
                       StudyResult result, int earnedExperience, boolean streakEligible) {
        this.learnerProfile = learnerProfile;
        this.questionSourceRecordId = questionSourceRecordId;
        this.result = result;
        this.earnedExperience = earnedExperience;
        this.answeredAt = Instant.now();
        this.streakEligible = streakEligible;
    }
    public Long getQuestionSourceRecordId() { return questionSourceRecordId; }
    public StudyResult getResult() { return result; }
    public int getEarnedExperience() { return earnedExperience; }
    public Instant getAnsweredAt() { return answeredAt; }
    public boolean isStreakEligible() { return streakEligible == null || streakEligible; }
}
