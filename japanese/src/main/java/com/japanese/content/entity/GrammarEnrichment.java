package com.japanese.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Curated learning notes. Imported grammar fields are intentionally never changed here. */
@Entity
@Table(name = "grammar_enrichments")
public class GrammarEnrichment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grammar_id", nullable = false, unique = true)
    private Grammar grammar;

    @Column(length = 3000) private String nuance;
    @Column(length = 3000) private String usageNote;
    @Column(length = 3000) private String formationSupplement;
    @Column(length = 3000) private String commonMistake;
    @Column(length = 3000) private String learnerNote;
    @Column(name = "source_ref", nullable = false, length = 300) private String sourceRef;
    @Column(name = "review_status", nullable = false, length = 20) private ReviewStatus reviewStatus = ReviewStatus.PENDING;
    @Column(nullable = false) private boolean published;
    @Column(name = "reviewed_at") private Instant reviewedAt;

    protected GrammarEnrichment() { }

    public GrammarEnrichment(Grammar grammar, String sourceRef) {
        this.grammar = grammar;
        this.sourceRef = sourceRef;
    }

    public void revise(String nuance, String usageNote, String formationSupplement, String commonMistake, String learnerNote) {
        this.nuance = nuance;
        this.usageNote = usageNote;
        this.formationSupplement = formationSupplement;
        this.commonMistake = commonMistake;
        this.learnerNote = learnerNote;
    }

    public void approve(boolean releaseAllowed) { reviewStatus = ReviewStatus.APPROVED; published = releaseAllowed; reviewedAt = Instant.now(); }
    public void approveForPublication() { approve(true); }
    public void reject() { reviewStatus = ReviewStatus.REJECTED; published = false; reviewedAt = Instant.now(); }
    public void markPending() { reviewStatus = ReviewStatus.PENDING; published = false; reviewedAt = null; }
    public boolean isPubliclyVisible() { return published && reviewStatus == ReviewStatus.APPROVED; }
    public Long getId() { return id; }
    public Grammar getGrammar() { return grammar; }
    public String getNuance() { return nuance; }
    public String getUsageNote() { return usageNote; }
    public String getFormationSupplement() { return formationSupplement; }
    public String getCommonMistake() { return commonMistake; }
    public String getLearnerNote() { return learnerNote; }
    public String getSourceRef() { return sourceRef; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public boolean isPublished() { return published; }
    public Instant getReviewedAt() { return reviewedAt; }
}
