package com.japanese.content.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JLPT-MAX Ticket 4B: one private, independently-persisted dedup/conflict relationship between two
 * same-{@link NormalizedCandidateType} {@link NormalizedContentCandidate} snapshots, plus the
 * structured {@link NormalizedCandidateMatchEvidence} that justifies its
 * {@link NormalizedCandidateMatchAssessment}. This is strictly a private-candidate-domain concept -
 * it never references or is referenced by {@code GrammarRelation}/{@code GrammarComparison} (the
 * curated production domain those two entities belong to look similar in name only) or any other
 * production entity.
 *
 * <p>{@code leftCandidate}/{@code rightCandidate} are always canonically ordered by ascending id
 * (enforced in the constructor, not left to callers) so a pair is never stored twice in opposite
 * orientations; a DB unique constraint on {@code (left_candidate_id, right_candidate_id)} backs this
 * up. The constructor also rejects a self-pair, a cross-{@code candidateType} pair, and a
 * cross-{@code sourceRef} pair outright - all three are analysis bugs, never legitimate data (the
 * analyzer only ever pools candidates that already share both {@code candidateType} and
 * {@code sourceRef} before pairing them - see {@code NormalizedCandidateConflictAnalyzer}), so they
 * fail fast as {@link IllegalArgumentException} rather than being silently stored.
 *
 * <p>Comparison logic itself lives in {@code NormalizedCandidateConflictAnalyzer}, not here - this
 * class (and {@link NormalizedCandidateMatchEvidence}) only hold state, per this ticket's
 * entity-vs-service split.
 *
 * <p>Rerunning analysis for a {@code (candidateType, sourceRef)} scope deletes every existing pair
 * in that scope and re-inserts freshly computed ones (see the analyzer) rather than versioning or
 * diffing - {@code generatedAt} marks when the currently-stored row was produced, nothing more.
 */
@Entity
@Table(name = "normalized_candidate_match_pairs",
        uniqueConstraints = @UniqueConstraint(name = "uk_normalized_candidate_match_pair",
                columnNames = {"left_candidate_id", "right_candidate_id"}),
        indexes = {
                @Index(name = "ix_normalized_candidate_match_pair_right", columnList = "right_candidate_id"),
                @Index(name = "ix_normalized_candidate_match_pair_assessment", columnList = "assessment")
        })
public class NormalizedCandidateMatchPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "left_candidate_id", nullable = false)
    private NormalizedContentCandidate leftCandidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "right_candidate_id", nullable = false)
    private NormalizedContentCandidate rightCandidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NormalizedCandidateMatchAssessment assessment;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @OneToMany(mappedBy = "pair", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<NormalizedCandidateMatchEvidence> evidence = new ArrayList<>();

    protected NormalizedCandidateMatchPair() {
    }

    public NormalizedCandidateMatchPair(NormalizedContentCandidate candidateA, NormalizedContentCandidate candidateB,
            NormalizedCandidateMatchAssessment assessment, Instant generatedAt) {
        Objects.requireNonNull(candidateA, "candidateA is required");
        Objects.requireNonNull(candidateB, "candidateB is required");
        Objects.requireNonNull(candidateA.getId(), "candidateA must already be persisted");
        Objects.requireNonNull(candidateB.getId(), "candidateB must already be persisted");
        if (candidateA.getId().equals(candidateB.getId())) {
            throw new IllegalArgumentException("A candidate cannot be matched against itself");
        }
        if (candidateA.getCandidateType() != candidateB.getCandidateType()) {
            throw new IllegalArgumentException(
                    "Cannot match across candidate types: " + candidateA.getCandidateType()
                            + " vs " + candidateB.getCandidateType());
        }
        if (!Objects.equals(candidateA.getSourceRef(), candidateB.getSourceRef())) {
            throw new IllegalArgumentException(
                    "Cannot match across source refs: " + candidateA.getSourceRef()
                            + " vs " + candidateB.getSourceRef());
        }
        if (assessment == NormalizedCandidateMatchAssessment.UNIQUE) {
            throw new IllegalArgumentException("UNIQUE is never persisted as a match pair");
        }
        boolean aIsLeft = candidateA.getId() < candidateB.getId();
        this.leftCandidate = aIsLeft ? candidateA : candidateB;
        this.rightCandidate = aIsLeft ? candidateB : candidateA;
        this.assessment = assessment;
        this.generatedAt = generatedAt;
    }

    public void addEvidence(NormalizedCandidateMatchEvidence item) {
        evidence.add(item);
        item.attach(this);
    }

    public Long getId() {
        return id;
    }

    public NormalizedContentCandidate getLeftCandidate() {
        return leftCandidate;
    }

    public NormalizedContentCandidate getRightCandidate() {
        return rightCandidate;
    }

    public NormalizedCandidateMatchAssessment getAssessment() {
        return assessment;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public List<NormalizedCandidateMatchEvidence> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }
}
