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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

/** A fixed, per-learner daily plan. Its item order never changes after creation. */
@Entity
@Table(name = "today_study_sessions", uniqueConstraints = @UniqueConstraint(
        name = "uk_today_study_session_learner_date", columnNames = {"learner_profile_id", "session_date"}))
public class TodayStudySession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "learner_profile_id", nullable = false) private LearnerProfile learnerProfile;
    @Column(name = "session_date", nullable = false) private LocalDate sessionDate;
    @Column(name = "session_key", nullable = false, unique = true, length = 120) private String sessionKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TodayStudySessionState state = TodayStudySessionState.ACTIVE;
    @Column(name = "planned_review_count", nullable = false) private int plannedReviewCount;
    @Column(name = "planned_new_word_count", nullable = false) private int plannedNewWordCount;
    @Column(name = "planned_new_grammar_count", nullable = false) private int plannedNewGrammarCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "completed_at") private Instant completedAt;
    protected TodayStudySession() { }
    public TodayStudySession(LearnerProfile learnerProfile, LocalDate sessionDate, String sessionKey,
                             int plannedReviewCount, int plannedNewWordCount, int plannedNewGrammarCount) {
        this.learnerProfile=learnerProfile; this.sessionDate=sessionDate; this.sessionKey=sessionKey;
        this.plannedReviewCount=plannedReviewCount; this.plannedNewWordCount=plannedNewWordCount; this.plannedNewGrammarCount=plannedNewGrammarCount;
    }
    public void complete() { state=TodayStudySessionState.COMPLETED; completedAt=Instant.now(); }
    public Long getId(){return id;} public String getSessionKey(){return sessionKey;} public TodayStudySessionState getState(){return state;}
    public int getPlannedReviewCount(){return plannedReviewCount;} public int getPlannedNewWordCount(){return plannedNewWordCount;} public int getPlannedNewGrammarCount(){return plannedNewGrammarCount;}
}
