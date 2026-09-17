package com.japanese.content.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A private, independently-persisted snapshot of what one normalization parser
 * ({@code VocabularyNormalizationParser}/{@code GrammarNormalizationParser}) produced for one raw
 * private-staging note. This sits strictly between the pure in-memory normalization results and
 * the not-yet-built dedup/review/promotion stages - it never touches, references, or is referenced
 * by any production entity ({@code ContentItem}/{@code Word}/{@code Grammar}/
 * {@code GrammarEnrichment}/{@code GrammarRelation}/{@code GrammarComparison}), and it is not
 * {@code ImportedSourceRecord} (which is production-{@code ContentItem}-linkable).
 *
 * <p>{@code sourceRef}/{@code sourceNoteId} are provenance only - a logical pointer back to a
 * {@code private_apkg_notes} row, deliberately without a foreign key to that table (which has no
 * JPA entity of its own and is treated as raw/JDBC-only everywhere else in this codebase); a
 * candidate must remain independently trackable even if its staging row is later reprocessed or
 * removed. {@code sourceIdentityKey} (the source-native EntryID/UnitID) is a dedup candidate for a
 * future ticket only - it deliberately has no unique constraint here.
 *
 * <p>{@code (sourceRef, sourceNoteId, candidateType)} identifies exactly one current/active
 * snapshot: re-normalizing the same note replaces this row's scalar fields and every child
 * collection in place (via the persistence service), rather than inserting a new envelope row or
 * leaving stale children behind. There is no history/versioning here by design.
 */
@Entity
@Table(name = "normalized_content_candidates", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_candidate_snapshot", columnNames = {"source_ref", "source_note_id", "candidate_type"}))
public class NormalizedContentCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_type", nullable = false, length = 20)
    private NormalizedCandidateType candidateType;

    @Column(name = "source_ref", nullable = false, length = 160)
    private String sourceRef;

    @Column(name = "source_note_id", nullable = false)
    private long sourceNoteId;

    @Column(name = "source_identity_key", length = 160)
    private String sourceIdentityKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_state", nullable = false, length = 20)
    private NormalizedCandidateQualityState qualityState;

    @Column(name = "normalized_at", nullable = false)
    private Instant normalizedAt;

    @OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.LAZY)
    private NormalizedVocabularyCandidateDetail vocabularyDetail;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("senseOrder asc")
    private List<NormalizedVocabularyCandidateMeaning> vocabularyMeanings = new ArrayList<>();

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private List<NormalizedVocabularyCandidateExample> vocabularyExamples = new ArrayList<>();

    @OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.LAZY)
    private NormalizedGrammarCandidateDetail grammarDetail;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private List<NormalizedGrammarCandidateConfusablePattern> grammarConfusablePatterns = new ArrayList<>();

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<NormalizedCandidateWarning> warnings = new ArrayList<>();

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NormalizedCandidateExtraField> extraFields = new ArrayList<>();

    protected NormalizedContentCandidate() {
    }

    public NormalizedContentCandidate(NormalizedCandidateType candidateType, String sourceRef, long sourceNoteId,
            String sourceIdentityKey, NormalizedCandidateQualityState qualityState, Instant normalizedAt) {
        this.candidateType = candidateType;
        this.sourceRef = sourceRef;
        this.sourceNoteId = sourceNoteId;
        this.sourceIdentityKey = sourceIdentityKey;
        this.qualityState = qualityState;
        this.normalizedAt = normalizedAt;
    }

    public void refreshEnvelope(String sourceIdentityKey, NormalizedCandidateQualityState qualityState,
            Instant normalizedAt) {
        this.sourceIdentityKey = sourceIdentityKey;
        this.qualityState = qualityState;
        this.normalizedAt = normalizedAt;
    }

    public void attachVocabularyDetail(NormalizedVocabularyCandidateDetail detail) {
        if (candidateType != NormalizedCandidateType.VOCABULARY) {
            throw new IllegalStateException(
                    "Cannot attach a vocabulary detail to a " + candidateType + " candidate");
        }
        this.vocabularyDetail = detail;
        detail.attach(this);
    }

    public void addVocabularyMeaning(NormalizedVocabularyCandidateMeaning meaning) {
        vocabularyMeanings.add(meaning);
        meaning.attach(this);
    }

    public void addVocabularyExample(NormalizedVocabularyCandidateExample example) {
        vocabularyExamples.add(example);
        example.attach(this);
    }

    public void attachGrammarDetail(NormalizedGrammarCandidateDetail detail) {
        if (candidateType != NormalizedCandidateType.GRAMMAR) {
            throw new IllegalStateException(
                    "Cannot attach a grammar detail to a " + candidateType + " candidate");
        }
        this.grammarDetail = detail;
        detail.attach(this);
    }

    public void addGrammarConfusablePattern(NormalizedGrammarCandidateConfusablePattern pattern) {
        grammarConfusablePatterns.add(pattern);
        pattern.attach(this);
    }

    public void addWarning(NormalizedCandidateWarning warning) {
        warnings.add(warning);
        warning.attach(this);
    }

    public void addExtraField(NormalizedCandidateExtraField field) {
        extraFields.add(field);
        field.attach(this);
    }

    public Long getId() {
        return id;
    }

    public NormalizedCandidateType getCandidateType() {
        return candidateType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public long getSourceNoteId() {
        return sourceNoteId;
    }

    public String getSourceIdentityKey() {
        return sourceIdentityKey;
    }

    public NormalizedCandidateQualityState getQualityState() {
        return qualityState;
    }

    public Instant getNormalizedAt() {
        return normalizedAt;
    }

    public NormalizedVocabularyCandidateDetail getVocabularyDetail() {
        return vocabularyDetail;
    }

    public List<NormalizedVocabularyCandidateMeaning> getVocabularyMeanings() {
        return Collections.unmodifiableList(vocabularyMeanings);
    }

    public List<NormalizedVocabularyCandidateExample> getVocabularyExamples() {
        return Collections.unmodifiableList(vocabularyExamples);
    }

    public NormalizedGrammarCandidateDetail getGrammarDetail() {
        return grammarDetail;
    }

    public List<NormalizedGrammarCandidateConfusablePattern> getGrammarConfusablePatterns() {
        return Collections.unmodifiableList(grammarConfusablePatterns);
    }

    public List<NormalizedCandidateWarning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<NormalizedCandidateExtraField> getExtraFields() {
        return Collections.unmodifiableList(extraFields);
    }
}
