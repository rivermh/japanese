package com.japanese.learning.entity;

import com.japanese.content.entity.GrammarConfirmationQuestion;
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

/** Supplementary comprehension signal. It deliberately does not award EXP or alter SRS. */
@Entity
@Table(name = "grammar_confirmation_attempts")
public class GrammarConfirmationAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "learner_profile_id", nullable = false) private LearnerProfile learnerProfile;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id", nullable = false) private GrammarConfirmationQuestion question;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StudyResult result;
    @Column(name = "answered_at", nullable = false) private Instant answeredAt;
    protected GrammarConfirmationAttempt() { }
    public GrammarConfirmationAttempt(LearnerProfile learnerProfile, GrammarConfirmationQuestion question, StudyResult result) { this.learnerProfile=learnerProfile;this.question=question;this.result=result;this.answeredAt=Instant.now(); }
    public GrammarConfirmationQuestion getQuestion(){return question;} public StudyResult getResult(){return result;} public Instant getAnsweredAt(){return answeredAt;}
}
