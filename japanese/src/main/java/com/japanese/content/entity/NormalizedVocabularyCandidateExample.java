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

/** One ordered example sentence from {@code VocabularyNormalizationResult.examples()}. */
@Entity
@Table(name = "normalized_vocabulary_candidate_examples", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_vocab_candidate_example_order", columnNames = {"candidate_id", "display_order"}))
public class NormalizedVocabularyCandidateExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private NormalizedContentCandidate candidate;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "meaning_label", length = 500)
    private String meaningLabel;

    @Lob
    @Column(name = "japanese_text", nullable = false, columnDefinition = "longtext")
    private String japaneseText;

    @Lob
    @Column(columnDefinition = "longtext")
    private String reading;

    @Lob
    @Column(columnDefinition = "longtext")
    private String translation;

    protected NormalizedVocabularyCandidateExample() {
    }

    public NormalizedVocabularyCandidateExample(int displayOrder, String meaningLabel, String japaneseText,
            String reading, String translation) {
        this.displayOrder = displayOrder;
        this.meaningLabel = meaningLabel;
        this.japaneseText = japaneseText;
        this.reading = reading;
        this.translation = translation;
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

    public int getDisplayOrder() {
        return displayOrder;
    }

    public String getMeaningLabel() {
        return meaningLabel;
    }

    public String getJapaneseText() {
        return japaneseText;
    }

    public String getReading() {
        return reading;
    }

    public String getTranslation() {
        return translation;
    }
}
