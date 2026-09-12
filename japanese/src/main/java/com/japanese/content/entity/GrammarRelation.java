package com.japanese.content.entity;

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

/** A canonical pair: left grammar id is always lower than right grammar id. */
@Entity
@Table(name = "grammar_relations", uniqueConstraints = @UniqueConstraint(
        name = "uk_grammar_relation_pair_type", columnNames = {"left_grammar_id", "right_grammar_id", "relation_type"}))
public class GrammarRelation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "left_grammar_id", nullable = false) private Grammar leftGrammar;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "right_grammar_id", nullable = false) private Grammar rightGrammar;
    @Enumerated(EnumType.STRING) @Column(name = "relation_type", nullable = false, length = 20) private GrammarRelationType relationType;
    @Column(name = "source_ref", nullable = false, length = 300) private String sourceRef;
    @Column(name = "review_status", nullable = false, length = 20) private ReviewStatus reviewStatus = ReviewStatus.PENDING;
    @Column(nullable = false) private boolean published;
    @Column(name = "reviewed_at") private Instant reviewedAt;

    protected GrammarRelation() { }
    public GrammarRelation(Grammar leftGrammar, Grammar rightGrammar, GrammarRelationType relationType, String sourceRef) {
        this.leftGrammar = leftGrammar; this.rightGrammar = rightGrammar; this.relationType = relationType; this.sourceRef = sourceRef;
    }
    public void approveForPublication() { reviewStatus = ReviewStatus.APPROVED; published = true; reviewedAt = Instant.now(); }
    public void markPending() { reviewStatus = ReviewStatus.PENDING; published = false; reviewedAt = null; }
    public boolean isPubliclyVisible() { return published && reviewStatus == ReviewStatus.APPROVED; }
    public Long getId() { return id; }
    public Grammar getLeftGrammar() { return leftGrammar; }
    public Grammar getRightGrammar() { return rightGrammar; }
    public GrammarRelationType getRelationType() { return relationType; }
    public String getSourceRef() { return sourceRef; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
}
