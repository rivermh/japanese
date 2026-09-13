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

/** Reviewed explanation for a particular grammar relation; it is not generated from source text. */
@Entity
@Table(name = "grammar_comparisons")
public class GrammarComparison {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "grammar_relation_id", nullable = false, unique = true) private GrammarRelation relation;
    @Column(length = 3000) private String summary;
    @Column(length = 3000) private String keyDifference;
    @Column(length = 3000) private String usageDifference;
    @Column(length = 3000) private String commonConfusion;
    @Column(name = "source_ref", nullable = false, length = 300) private String sourceRef;
    @Column(name = "review_status", nullable = false, length = 20) private ReviewStatus reviewStatus = ReviewStatus.PENDING;
    @Column(nullable = false) private boolean published;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    protected GrammarComparison() { }
    public GrammarComparison(GrammarRelation relation, String sourceRef) { this.relation = relation; this.sourceRef = sourceRef; }
    public void revise(String summary, String keyDifference, String usageDifference, String commonConfusion) { this.summary=summary; this.keyDifference=keyDifference; this.usageDifference=usageDifference; this.commonConfusion=commonConfusion; }
    public void approveForPublication() { reviewStatus=ReviewStatus.APPROVED; published=true; reviewedAt=Instant.now(); }
    public void reject(){reviewStatus=ReviewStatus.REJECTED;published=false;reviewedAt=Instant.now();}
    public void markPending() { reviewStatus=ReviewStatus.PENDING; published=false; reviewedAt=null; }
    public boolean isPubliclyVisible() { return published && reviewStatus==ReviewStatus.APPROVED && relation.isPubliclyVisible(); }
    public Long getId(){return id;} public GrammarRelation getRelation(){return relation;} public String getSummary(){return summary;} public String getKeyDifference(){return keyDifference;} public String getUsageDifference(){return usageDifference;} public String getCommonConfusion(){return commonConfusion;} public String getSourceRef(){return sourceRef;} public ReviewStatus getReviewStatus(){return reviewStatus;} public boolean isPublished(){return published;} public Instant getReviewedAt(){return reviewedAt;}
}
