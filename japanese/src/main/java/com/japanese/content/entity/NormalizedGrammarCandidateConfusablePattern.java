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
 * One ordered "헷갈리는 문형" (confusable pattern) entry from
 * {@code GrammarNormalizationResult.confusablePatterns()} - source-derived comparison data only,
 * never mapped onto the curated {@code GrammarRelation}/{@code GrammarComparison} production
 * domain by this entity or its persistence.
 */
@Entity
@Table(name = "normalized_grammar_candidate_confusable_patterns", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_grammar_candidate_confusable_order", columnNames = {"candidate_id", "display_order"}))
public class NormalizedGrammarCandidateConfusablePattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private NormalizedContentCandidate candidate;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, length = 500)
    private String pattern;

    @Lob
    @Column(columnDefinition = "longtext")
    private String explanation;

    protected NormalizedGrammarCandidateConfusablePattern() {
    }

    public NormalizedGrammarCandidateConfusablePattern(int displayOrder, String pattern, String explanation) {
        this.displayOrder = displayOrder;
        this.pattern = pattern;
        this.explanation = explanation;
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

    public String getPattern() {
        return pattern;
    }

    public String getExplanation() {
        return explanation;
    }
}
