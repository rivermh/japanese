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

/**
 * One structured reason contributing to a {@link NormalizedCandidateMatchPair}'s
 * {@link NormalizedCandidateMatchAssessment}, in original comparison order. {@code fieldName} is
 * the machine-readable field the code is about (e.g. {@code "expression"}, {@code "meaningGloss"});
 * {@code detail} is an optional human-readable rendering of the two compared values (e.g.
 * {@code "a=N2 b=N1"}) for a future human reviewer (Ticket 4C) - deliberately two structured columns
 * rather than one free-form blob, per this ticket's evidence-persistence requirement.
 */
@Entity
@Table(name = "normalized_candidate_match_evidence", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_candidate_match_evidence_position", columnNames = {"pair_id", "position"}))
public class NormalizedCandidateMatchEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pair_id", nullable = false)
    private NormalizedCandidateMatchPair pair;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_code", nullable = false, length = 40)
    private NormalizedCandidateMatchEvidenceCode evidenceCode;

    @Column(name = "field_name", length = 40)
    private String fieldName;

    @Column(length = 2000)
    private String detail;

    protected NormalizedCandidateMatchEvidence() {
    }

    public NormalizedCandidateMatchEvidence(int position, NormalizedCandidateMatchEvidenceCode evidenceCode,
            String fieldName, String detail) {
        this.position = position;
        this.evidenceCode = evidenceCode;
        this.fieldName = fieldName;
        this.detail = detail;
    }

    void attach(NormalizedCandidateMatchPair pair) {
        this.pair = pair;
    }

    public Long getId() {
        return id;
    }

    public NormalizedCandidateMatchPair getPair() {
        return pair;
    }

    public int getPosition() {
        return position;
    }

    public NormalizedCandidateMatchEvidenceCode getEvidenceCode() {
        return evidenceCode;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getDetail() {
        return detail;
    }
}
