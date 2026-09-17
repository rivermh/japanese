package com.japanese.content.entity;

import com.japanese.account.entity.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

/**
 * JLPT-MAX Ticket 4E-3A: one permanent, IMMUTABLE audit record of a single pairwise SAME_CONTENT
 * {@code NormalizedCandidatePairReview} that a {@link NormalizedCandidateCanonicalGroup} decision
 * relied upon. For a group of N candidates, exactly C(N,2) edge rows are created at group-creation
 * time - one per unordered candidate pair - never inferred, never a spanning-tree/transitive shortcut
 * (JLPT-MAX Ticket 4E-3A: no SAME_CONTENT transitivity is ever assumed).
 *
 * <p><b>Every field except {@code id}/{@code group}/{@code leftCandidate}/{@code rightCandidate} is an
 * immutable snapshot</b>, copied once from the LOCKED, freshly-revalidated live review row at
 * group-creation time and never updated afterward - this row is never the target of any update
 * anywhere in this codebase. This is deliberate, self-describing duplication (JLPT-MAX Ticket 4E-3A
 * design review round 3): {@code NormalizedCandidatePairReview} is mutable in place (a later
 * re-review changes its decision/version/snapshots), and
 * {@code NormalizedCandidatePairReviewHistory} has no {@code version} column, so neither can durably
 * answer "exactly what did this canonical-group decision rely on" once time has passed - this row can,
 * entirely on its own, permanently.
 *
 * <p><b>Survives group dissolution</b>: unlike {@link NormalizedCandidateCanonicalGroupMember} rows
 * (deleted on dissolution to release candidates), edge rows are NEVER deleted - together with the
 * group header, they are this design's entire permanent historical record, sufficient on their own to
 * reconstruct a dissolved group's full former membership (the set of distinct candidate ids appearing
 * as either side of its edge rows) even after the membership rows are gone.
 *
 * <p>{@code leftCandidate}/{@code rightCandidate} are canonically ordered by ascending id in the
 * constructor, exactly like {@code NormalizedCandidateMatchPair}/{@code NormalizedCandidatePairReview}.
 * {@code NormalizedCandidateMatchPair.id} is deliberately never stored anywhere in this entity (or
 * anywhere else in this schema) - that id is intentionally unstable across Ticket 4B reanalysis;
 * {@code pairReview}'s own id is stable and safe to reference instead.
 */
@Entity
@Table(name = "normalized_candidate_canonical_group_edges",
        uniqueConstraints = @UniqueConstraint(name = "uk_canonical_group_edge",
                columnNames = {"group_id", "left_candidate_id", "right_candidate_id"}),
        indexes = @Index(name = "ix_canonical_group_edge_review", columnList = "pair_review_id"))
public class NormalizedCandidateCanonicalGroupEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private NormalizedCandidateCanonicalGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "left_candidate_id", nullable = false)
    private NormalizedContentCandidate leftCandidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "right_candidate_id", nullable = false)
    private NormalizedContentCandidate rightCandidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pair_review_id", nullable = false)
    private NormalizedCandidatePairReview pairReview;

    @Column(name = "pair_review_version_snapshot", nullable = false)
    private long pairReviewVersionSnapshot;

    @Column(name = "pair_reviewed_at_snapshot", nullable = false)
    private Instant pairReviewedAtSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id_snapshot", nullable = false)
    private UserAccount reviewerSnapshot;

    @Column(name = "review_decision_snapshot", nullable = false, length = 20)
    private String reviewDecisionSnapshot;

    @Column(name = "assessment_snapshot", nullable = false, length = 20)
    private String assessmentSnapshot;

    @Column(name = "left_normalized_at_snapshot", nullable = false)
    private Instant leftNormalizedAtSnapshot;

    @Column(name = "right_normalized_at_snapshot", nullable = false)
    private Instant rightNormalizedAtSnapshot;

    protected NormalizedCandidateCanonicalGroupEdge() {
    }

    public NormalizedCandidateCanonicalGroupEdge(NormalizedCandidateCanonicalGroup group,
            NormalizedContentCandidate candidateA, NormalizedContentCandidate candidateB,
            NormalizedCandidatePairReview pairReview, long pairReviewVersionSnapshot,
            Instant pairReviewedAtSnapshot, UserAccount reviewerSnapshot, HumanReviewDecision reviewDecisionSnapshot,
            NormalizedCandidateMatchAssessment assessmentSnapshot, Instant normalizedAtSnapshotA,
            Instant normalizedAtSnapshotB) {
        this.group = Objects.requireNonNull(group, "group is required");
        Objects.requireNonNull(candidateA, "candidateA is required");
        Objects.requireNonNull(candidateB, "candidateB is required");
        Objects.requireNonNull(candidateA.getId(), "candidateA must already be persisted");
        Objects.requireNonNull(candidateB.getId(), "candidateB must already be persisted");
        if (candidateA.getId().equals(candidateB.getId())) {
            throw new IllegalArgumentException("An edge cannot connect a candidate to itself");
        }
        boolean aIsLeft = candidateA.getId() < candidateB.getId();
        this.leftCandidate = aIsLeft ? candidateA : candidateB;
        this.rightCandidate = aIsLeft ? candidateB : candidateA;
        Instant leftSnapshot = aIsLeft ? normalizedAtSnapshotA : normalizedAtSnapshotB;
        Instant rightSnapshot = aIsLeft ? normalizedAtSnapshotB : normalizedAtSnapshotA;
        this.pairReview = Objects.requireNonNull(pairReview, "pairReview is required");
        this.pairReviewVersionSnapshot = pairReviewVersionSnapshot;
        this.pairReviewedAtSnapshot = Objects.requireNonNull(pairReviewedAtSnapshot, "pairReviewedAtSnapshot is required");
        this.reviewerSnapshot = Objects.requireNonNull(reviewerSnapshot, "reviewerSnapshot is required");
        this.reviewDecisionSnapshot =
                Objects.requireNonNull(reviewDecisionSnapshot, "reviewDecisionSnapshot is required").name();
        this.assessmentSnapshot = Objects.requireNonNull(assessmentSnapshot, "assessmentSnapshot is required").name();
        this.leftNormalizedAtSnapshot = Objects.requireNonNull(leftSnapshot, "leftNormalizedAtSnapshot is required");
        this.rightNormalizedAtSnapshot = Objects.requireNonNull(rightSnapshot, "rightNormalizedAtSnapshot is required");
    }

    public Long getId() {
        return id;
    }

    public NormalizedCandidateCanonicalGroup getGroup() {
        return group;
    }

    public NormalizedContentCandidate getLeftCandidate() {
        return leftCandidate;
    }

    public NormalizedContentCandidate getRightCandidate() {
        return rightCandidate;
    }

    public NormalizedCandidatePairReview getPairReview() {
        return pairReview;
    }

    public long getPairReviewVersionSnapshot() {
        return pairReviewVersionSnapshot;
    }

    public Instant getPairReviewedAtSnapshot() {
        return pairReviewedAtSnapshot;
    }

    public UserAccount getReviewerSnapshot() {
        return reviewerSnapshot;
    }

    public HumanReviewDecision getReviewDecisionSnapshot() {
        return HumanReviewDecision.valueOf(reviewDecisionSnapshot);
    }

    public NormalizedCandidateMatchAssessment getAssessmentSnapshot() {
        return NormalizedCandidateMatchAssessment.valueOf(assessmentSnapshot);
    }

    public Instant getLeftNormalizedAtSnapshot() {
        return leftNormalizedAtSnapshot;
    }

    public Instant getRightNormalizedAtSnapshot() {
        return rightNormalizedAtSnapshot;
    }
}
