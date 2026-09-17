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
import jakarta.persistence.UniqueConstraint;

/**
 * The VOCABULARY-specific scalar fields of a {@link NormalizedContentCandidate} snapshot - a
 * lossless persisted mirror of {@code VocabularyNormalizationResult}, minus its ordered
 * {@code meanings}/{@code examples} (their own child tables) and {@code warnings}/
 * {@code preservedExtraFields} (shared candidate-level child tables). Every column is nullable:
 * a FATAL candidate (e.g. missing expression/reading/meaning) is still persisted in full, never
 * dropped or coerced into a non-null placeholder. {@code validForPromotion} from the source result
 * is intentionally not stored here - it is not a persisted approval state, and the candidate's
 * {@code qualityState} on the owning envelope already carries the equivalent completeness signal.
 */
@Entity
@Table(name = "normalized_vocabulary_candidates", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_vocabulary_candidate", columnNames = "candidate_id"))
public class NormalizedVocabularyCandidateDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private NormalizedContentCandidate candidate;

    @Column(name = "entry_id", length = 160)
    private String entryId;

    @Column(length = 500)
    private String expression;

    @Column(length = 500)
    private String reading;

    @Column(name = "part_of_speech", length = 500)
    private String partOfSpeech;

    @Column(name = "pitch_accent_terminal_states", length = 500)
    private String pitchAccentTerminalStates;

    @Column(name = "pitch_accent_mora", length = 500)
    private String pitchAccentMora;

    @Column(name = "level_code", length = 20)
    private String levelCode;

    @Column(name = "level_raw_value", length = 160)
    private String levelRawValue;

    @Column(name = "level_source_field", length = 40)
    private String levelSourceField;

    @Column(name = "normalized_search_expression", length = 500)
    private String normalizedSearchExpression;

    @Column(name = "normalized_search_reading", length = 500)
    private String normalizedSearchReading;

    protected NormalizedVocabularyCandidateDetail() {
    }

    public NormalizedVocabularyCandidateDetail(String entryId, String expression, String reading,
            String partOfSpeech, String pitchAccentTerminalStates, String pitchAccentMora,
            String levelCode, String levelRawValue, String levelSourceField,
            String normalizedSearchExpression, String normalizedSearchReading) {
        this.entryId = entryId;
        this.expression = expression;
        this.reading = reading;
        this.partOfSpeech = partOfSpeech;
        this.pitchAccentTerminalStates = pitchAccentTerminalStates;
        this.pitchAccentMora = pitchAccentMora;
        this.levelCode = levelCode;
        this.levelRawValue = levelRawValue;
        this.levelSourceField = levelSourceField;
        this.normalizedSearchExpression = normalizedSearchExpression;
        this.normalizedSearchReading = normalizedSearchReading;
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

    public String getEntryId() {
        return entryId;
    }

    public String getExpression() {
        return expression;
    }

    public String getReading() {
        return reading;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public String getPitchAccentTerminalStates() {
        return pitchAccentTerminalStates;
    }

    public String getPitchAccentMora() {
        return pitchAccentMora;
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

    public String getNormalizedSearchExpression() {
        return normalizedSearchExpression;
    }

    public String getNormalizedSearchReading() {
        return normalizedSearchReading;
    }
}
