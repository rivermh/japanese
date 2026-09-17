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

/** One ordered sense from {@code VocabularyNormalizationResult.meanings()}, preserved verbatim. */
@Entity
@Table(name = "normalized_vocabulary_candidate_meanings", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_vocab_candidate_meaning_order", columnNames = {"candidate_id", "sense_order"}))
public class NormalizedVocabularyCandidateMeaning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private NormalizedContentCandidate candidate;

    @Column(name = "sense_order", nullable = false)
    private int senseOrder;

    @Lob
    @Column(name = "meaning_text", nullable = false, columnDefinition = "longtext")
    private String meaningText;

    protected NormalizedVocabularyCandidateMeaning() {
    }

    public NormalizedVocabularyCandidateMeaning(int senseOrder, String meaningText) {
        this.senseOrder = senseOrder;
        this.meaningText = meaningText;
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

    public int getSenseOrder() {
        return senseOrder;
    }

    public String getMeaningText() {
        return meaningText;
    }
}
