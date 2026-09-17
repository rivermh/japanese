package com.japanese.content.entity;

import com.japanese.account.entity.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

/**
 * JLPT-MAX Ticket 4C: the current human-review judgment for one {@code (leftCandidate,
 * rightCandidate)} pair - strictly a private-review-domain concept, entirely separate from
 * production review state ({@code ReviewStatus}/{@code ContentReviewHistory}/
 * {@code CurationReviewHistory}), which this class never reads, writes, or references.
 *
 * <p><b>Why this is keyed on the two candidates, not on {@link NormalizedCandidateMatchPair#getId()}</b>:
 * Ticket 4B's {@code NormalizedCandidateConflictAnalyzer.analyze(type, sourceRef)} deletes every
 * existing pair row in scope and re-inserts freshly computed ones on every rerun, so a
 * {@code NormalizedCandidateMatchPair}'s primary key is never stable across reanalysis even when the
 * underlying candidates and assessment are completely unchanged. A review keyed on the pair id would
 * either block Ticket 4B's rerun (FK constraint) or silently lose its connection to "the same pair"
 * every time an admin reanalyzes. Keying on {@code (leftCandidate, rightCandidate)} - the two stable
 * {@link NormalizedContentCandidate} identities - survives reanalysis; the current Ticket 4B pair
 * row for this identity (if any) is looked up at read time by
 * {@code NormalizedCandidateMatchPairRepository.findByLeftCandidateIdAndRightCandidateId}, never
 * joined to by a persisted foreign key.
 *
 * <p><b>Freshness</b>: {@link #leftNormalizedAtSnapshot}/{@link #rightNormalizedAtSnapshot}/
 * {@link #assessmentSnapshot} capture exactly what the reviewer saw at {@link #reviewedAt}. This
 * review is still valid ("FRESH") for the current candidate/pair state only while all three still
 * match the live values - see {@code NormalizedCandidatePairReviewService} for the comparison. A
 * reanalyze that leaves the candidates and assessment unchanged (only the pair row's id/generatedAt
 * change) therefore leaves an existing review FRESH; a candidate refresh or an assessment change
 * makes it STALE until a new decision is recorded.
 *
 * <p>There is exactly one current row per pair identity (backed by the
 * {@code (left_candidate_id, right_candidate_id)} unique constraint); re-reviewing updates this row
 * in place via {@link #recordDecision} and a corresponding {@link NormalizedCandidatePairReviewHistory}
 * row is appended by the service - this entity itself never writes history.
 */
@Entity
@Table(name = "normalized_candidate_pair_reviews", uniqueConstraints = @UniqueConstraint(
        name = "uk_normalized_candidate_pair_review", columnNames = {"left_candidate_id", "right_candidate_id"}))
public class NormalizedCandidatePairReview {

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
    private HumanReviewDecision decision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private UserAccount reviewer;

    @Column(length = 2000)
    private String note;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "left_normalized_at_snapshot", nullable = false)
    private Instant leftNormalizedAtSnapshot;

    @Column(name = "right_normalized_at_snapshot", nullable = false)
    private Instant rightNormalizedAtSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_snapshot", nullable = false, length = 20)
    private NormalizedCandidateMatchAssessment assessmentSnapshot;

    /**
     * Optimistic-concurrency guard (JLPT-MAX Ticket 4C step 41): two admins opening the same detail
     * page and submitting a decision concurrently must not silently last-write-wins each other. There
     * is no existing {@code @Version} precedent elsewhere in this codebase - this is the first use of
     * one - chosen as the simplest standard JPA mechanism for this exact problem.
     */
    @Version
    @Column(nullable = false)
    private long version;

    protected NormalizedCandidatePairReview() {
    }

    /**
     * Creates the first review row for a pair identity. {@code candidateA}/{@code candidateB} may be
     * supplied in either order - canonically reordered to ascending id here, exactly like
     * {@link NormalizedCandidateMatchPair}, so a review is never stored twice in opposite
     * orientations; {@code normalizedAtSnapshotA}/{@code normalizedAtSnapshotB} are reordered
     * together with their respective candidate so the stored left/right snapshot always corresponds
     * to the stored left/right candidate.
     */
    public NormalizedCandidatePairReview(NormalizedContentCandidate candidateA, NormalizedContentCandidate candidateB,
            Instant normalizedAtSnapshotA, Instant normalizedAtSnapshotB,
            NormalizedCandidateMatchAssessment assessmentSnapshot, HumanReviewDecision decision,
            UserAccount reviewer, String note, Instant reviewedAt) {
        Objects.requireNonNull(candidateA, "candidateA is required");
        Objects.requireNonNull(candidateB, "candidateB is required");
        Objects.requireNonNull(candidateA.getId(), "candidateA must already be persisted");
        Objects.requireNonNull(candidateB.getId(), "candidateB must already be persisted");
        if (candidateA.getId().equals(candidateB.getId())) {
            throw new IllegalArgumentException("A candidate cannot be reviewed against itself");
        }
        if (candidateA.getCandidateType() != candidateB.getCandidateType()) {
            throw new IllegalArgumentException("Cannot review across candidate types: "
                    + candidateA.getCandidateType() + " vs " + candidateB.getCandidateType());
        }
        if (!Objects.equals(candidateA.getSourceRef(), candidateB.getSourceRef())) {
            throw new IllegalArgumentException(
                    "Cannot review across source refs: " + candidateA.getSourceRef() + " vs " + candidateB.getSourceRef());
        }
        boolean aIsLeft = candidateA.getId() < candidateB.getId();
        this.leftCandidate = aIsLeft ? candidateA : candidateB;
        this.rightCandidate = aIsLeft ? candidateB : candidateA;
        Instant leftSnapshot = aIsLeft ? normalizedAtSnapshotA : normalizedAtSnapshotB;
        Instant rightSnapshot = aIsLeft ? normalizedAtSnapshotB : normalizedAtSnapshotA;
        recordDecision(decision, reviewer, note, reviewedAt, leftSnapshot, rightSnapshot, assessmentSnapshot);
    }

    /**
     * Re-reviews this same pair identity in place: updates the current decision/reviewer/note/
     * snapshots. Never changes {@link #leftCandidate}/{@link #rightCandidate} (those are fixed at
     * creation) and never touches any {@link NormalizedCandidateMatchPair} row - the machine
     * assessment stays exactly what Ticket 4B computed, this only ever records what a human decided
     * about it (JLPT-MAX Ticket 4C step 19).
     */
    public void recordDecision(HumanReviewDecision decision, UserAccount reviewer, String note, Instant reviewedAt,
            Instant leftNormalizedAtSnapshot, Instant rightNormalizedAtSnapshot,
            NormalizedCandidateMatchAssessment assessmentSnapshot) {
        this.decision = Objects.requireNonNull(decision, "decision is required");
        this.reviewer = Objects.requireNonNull(reviewer, "reviewer is required");
        this.note = note;
        this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt is required");
        this.leftNormalizedAtSnapshot = Objects.requireNonNull(leftNormalizedAtSnapshot, "leftNormalizedAtSnapshot is required");
        this.rightNormalizedAtSnapshot = Objects.requireNonNull(rightNormalizedAtSnapshot, "rightNormalizedAtSnapshot is required");
        this.assessmentSnapshot = Objects.requireNonNull(assessmentSnapshot, "assessmentSnapshot is required");
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

    public HumanReviewDecision getDecision() {
        return decision;
    }

    public UserAccount getReviewer() {
        return reviewer;
    }

    public String getNote() {
        return note;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getLeftNormalizedAtSnapshot() {
        return leftNormalizedAtSnapshot;
    }

    public Instant getRightNormalizedAtSnapshot() {
        return rightNormalizedAtSnapshot;
    }

    public NormalizedCandidateMatchAssessment getAssessmentSnapshot() {
        return assessmentSnapshot;
    }

    public long getVersion() {
        return version;
    }
}
