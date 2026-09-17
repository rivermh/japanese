package com.japanese.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One raw extra/unknown field preserved verbatim for a {@link NormalizedContentCandidate} - the
 * persisted form of {@code VocabularyNormalizationResult.preservedExtraFields()} or
 * {@code GrammarNormalizationResult.unknownFields()}, whichever applies to the owning candidate's
 * {@code candidateType}. A {@code Map<String,String>} keyed by raw source field name, so one row
 * per field rather than an opaque blob.
 */
@Entity
@Table(name = "normalized_candidate_extra_fields", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_candidate_extra_field", columnNames = {"candidate_id", "field_name"}))
public class NormalizedCandidateExtraField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private NormalizedContentCandidate candidate;

    @Column(name = "field_name", nullable = false, length = 160)
    private String fieldName;

    @Lob
    @Column(name = "field_value", nullable = false, columnDefinition = "longtext")
    private String fieldValue;

    protected NormalizedCandidateExtraField() {
    }

    public NormalizedCandidateExtraField(String fieldName, String fieldValue) {
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
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

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldValue() {
        return fieldValue;
    }
}
