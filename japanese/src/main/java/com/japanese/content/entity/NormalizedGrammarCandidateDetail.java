package com.japanese.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * The GRAMMAR-specific scalar fields of a {@link NormalizedContentCandidate} snapshot - a lossless
 * persisted mirror of {@code GrammarNormalizationResult}, minus its ordered
 * {@code confusablePatterns} (own child table) and {@code warnings}/{@code unknownFields} (shared
 * candidate-level child tables). {@code frontExample} is flattened onto this single-row detail
 * (it is one value, never a list, in the source result). Every column is nullable so a FATAL
 * candidate is still persisted in full. There is deliberately no {@code explanation} column -
 * {@code meaningGloss} is the short Korean gloss and {@code frontExample.translation} is the
 * example sentence's Korean translation; neither is renamed or collapsed into a production-shaped
 * "explanation" field here, since which of these (if any) becomes {@code Grammar.explanation} is a
 * later promotion-time decision, not this ticket's.
 */
@Entity
@Table(name = "normalized_grammar_candidates", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_grammar_candidate", columnNames = "candidate_id"))
public class NormalizedGrammarCandidateDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private NormalizedContentCandidate candidate;

    @Column(name = "unit_id", length = 160)
    private String unitId;

    @Column(length = 500)
    private String pattern;

    @Column(name = "front_example_display_order")
    private Integer frontExampleDisplayOrder;

    @Lob
    @Column(name = "front_example_japanese_text", columnDefinition = "longtext")
    private String frontExampleJapaneseText;

    @Lob
    @Column(name = "front_example_reading", columnDefinition = "longtext")
    private String frontExampleReading;

    @Lob
    @Column(name = "front_example_translation", columnDefinition = "longtext")
    private String frontExampleTranslation;

    @Column(name = "meaning_gloss", length = 2000)
    private String meaningGloss;

    @Lob
    @Column(columnDefinition = "longtext")
    private String nuance;

    @Column(name = "connection_form", length = 2000)
    private String connectionForm;

    @Column(name = "level_code", length = 20)
    private String levelCode;

    @Column(name = "level_raw_value", length = 160)
    private String levelRawValue;

    @Column(name = "level_source_field", length = 40)
    private String levelSourceField;

    @Column(name = "raw_kind", length = 500)
    private String rawKind;

    protected NormalizedGrammarCandidateDetail() {
    }

    public NormalizedGrammarCandidateDetail(String unitId, String pattern, Integer frontExampleDisplayOrder,
            String frontExampleJapaneseText, String frontExampleReading, String frontExampleTranslation,
            String meaningGloss, String nuance, String connectionForm, String levelCode, String levelRawValue,
            String levelSourceField, String rawKind) {
        this.unitId = unitId;
        this.pattern = pattern;
        this.frontExampleDisplayOrder = frontExampleDisplayOrder;
        this.frontExampleJapaneseText = frontExampleJapaneseText;
        this.frontExampleReading = frontExampleReading;
        this.frontExampleTranslation = frontExampleTranslation;
        this.meaningGloss = meaningGloss;
        this.nuance = nuance;
        this.connectionForm = connectionForm;
        this.levelCode = levelCode;
        this.levelRawValue = levelRawValue;
        this.levelSourceField = levelSourceField;
        this.rawKind = rawKind;
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

    public String getUnitId() {
        return unitId;
    }

    public String getPattern() {
        return pattern;
    }

    public Integer getFrontExampleDisplayOrder() {
        return frontExampleDisplayOrder;
    }

    public String getFrontExampleJapaneseText() {
        return frontExampleJapaneseText;
    }

    public String getFrontExampleReading() {
        return frontExampleReading;
    }

    public String getFrontExampleTranslation() {
        return frontExampleTranslation;
    }

    public String getMeaningGloss() {
        return meaningGloss;
    }

    public String getNuance() {
        return nuance;
    }

    public String getConnectionForm() {
        return connectionForm;
    }

    public String getLevelCode() {
        return levelCode;
    }

    public String getLevelRawValue() {
        return levelRawValue;
    }

    public String getLevelSourceField() {
        return levelSourceField;
    }

    public String getRawKind() {
        return rawKind;
    }
}
