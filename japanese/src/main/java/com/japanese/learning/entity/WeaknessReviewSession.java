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

/** A fixed, user-started RETRAIN plan for currently evidenced weak content. */
@Entity
@Table(name = "weakness_review_sessions")
public class WeaknessReviewSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "learner_profile_id", nullable = false) private LearnerProfile learnerProfile;
    @Column(name = "session_key", nullable = false, unique = true, length = 120) private String sessionKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private WeaknessReviewSessionState state = WeaknessReviewSessionState.ACTIVE;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "completed_at") private Instant completedAt;
    protected WeaknessReviewSession() { }
    public WeaknessReviewSession(LearnerProfile learnerProfile, String sessionKey) { this.learnerProfile=learnerProfile; this.sessionKey=sessionKey; }
    public void complete() { state=WeaknessReviewSessionState.COMPLETED; completedAt=Instant.now(); }
    public Long getId(){return id;} public String getSessionKey(){return sessionKey;} public WeaknessReviewSessionState getState(){return state;}
}
