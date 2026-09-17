package com.japanese.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One warning raised while normalizing a {@link NormalizedContentCandidate}, preserved losslessly
 * in original order. {@code issueCode} is the raw {@code VocabularyNormalizationIssue}/
 * {@code GrammarNormalizationIssue} enum name (whichever applies to the owning candidate's
 * {@code candidateType}) stored as plain text - there is deliberately no shared cross-domain issue
 * enum, since the two normalization parsers keep their own separate severity-tagged issue sets.
 */
@Entity
@Table(name = "normalized_candidate_warnings", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_candidate_warning_position", columnNames = {"candidate_id", "position"}))
public class NormalizedCandidateWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private NormalizedContentCandidate candidate;

    @Column(nullable = false)
    private int position;

    @Column(name = "issue_code", nullable = false, length = 80)
    private String issueCode;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 2000)
    private String message;

    protected NormalizedCandidateWarning() {
    }

    public NormalizedCandidateWarning(int position, String issueCode, String severity, String message) {
        this.position = position;
        this.issueCode = issueCode;
        this.severity = severity;
        this.message = message;
    }

    void attach(NormalizedContentCandidate candidate) {
        this.candidate = candidate;
    }

    public Long getId() {
        return id;
    }

    public NormalizedContentCandidate getCandidate() {
        return candidate;
    }

    public int getPosition() {
        return position;
    }

    public String getIssueCode() {
        return issueCode;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }
}
