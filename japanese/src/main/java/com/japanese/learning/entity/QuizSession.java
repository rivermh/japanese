package com.japanese.learning.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "quiz_sessions", indexes = {
        @Index(name = "idx_quiz_session_owner_state", columnList = "learner_profile_id,state")})
public class QuizSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "learner_profile_id", nullable = false) private LearnerProfile learnerProfile;
    @Column(name = "public_id", nullable = false, unique = true, length = 36) private String publicId;
    @Column(name = "session_key", nullable = false, unique = true, length = 120) private String sessionKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private QuizMode mode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private QuizSessionState state = QuizSessionState.ACTIVE;
    @Column(name = "total_questions", nullable = false) private int totalQuestions;
    @Column(name = "answered_count", nullable = false) private int answeredCount;
    @Column(name = "correct_count", nullable = false) private int correctCount;
    @Column(name = "earned_experience", nullable = false) private int earnedExperience;
    @Column(name = "started_at", nullable = false) private Instant startedAt = Instant.now();
    @Column(name = "completed_at") private Instant completedAt;

    protected QuizSession() { }

    public QuizSession(LearnerProfile learnerProfile, String publicId, String sessionKey, QuizMode mode, int totalQuestions) {
        this.learnerProfile = learnerProfile;
        this.publicId = publicId;
        this.sessionKey = sessionKey;
        this.mode = mode;
        this.totalQuestions = totalQuestions;
    }

    public void recordAnswer(boolean correct, int experience) {
        if (state != QuizSessionState.ACTIVE) throw new IllegalStateException("완료된 퀴즈입니다.");
        answeredCount++;
        if (correct) correctCount++;
        earnedExperience += experience;
        if (answeredCount >= totalQuestions) {
            state = QuizSessionState.COMPLETED;
            completedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long learnerProfileId() { return learnerProfile.getId(); }
    public String getPublicId() { return publicId; }
    public String getSessionKey() { return sessionKey; }
    public QuizMode getMode() { return mode; }
    public QuizSessionState getState() { return state; }
    public int getTotalQuestions() { return totalQuestions; }
    public int getAnsweredCount() { return answeredCount; }
    public int getCorrectCount() { return correctCount; }
    public int getEarnedExperience() { return earnedExperience; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
