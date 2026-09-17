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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JLPT-MAX Ticket 4C: one append-only, immutable record of a human decision made about a
 * {@code (leftCandidate, rightCandidate)} pair - referencing the two stable
 * {@link NormalizedContentCandidate} ids directly, exactly like {@link NormalizedCandidatePairReview}
 * and for the same reason ({@link NormalizedCandidateMatchPair}'s own id is not stable across
 * {@code NormalizedCandidateConflictAnalyzer} reruns). A row is created every time a decision is
 * first recorded or changed for a pair identity; unlike {@link NormalizedCandidatePairReview} (one
 * current row per pair) this table only ever grows.
 *
 * <p>Deliberately no update/delete path is exposed anywhere in this ticket's service/controller -
 * once written, a history row is never modified, mirroring {@code ContentReviewHistory}/
 * {@code CurationReviewHistory}'s own immutable-audit-row convention (constructor + getters only, no
 * setters). Candidate refresh, reanalysis, or the pair itself later disappearing never deletes a
 * history row - see JLPT-MAX Ticket 4C step 16/40.
 */
@Entity
@Table(name = "normalized_candidate_pair_review_history", indexes = {
        @Index(name = "ix_normalized_candidate_pair_review_history_pair",
                columnList = "left_candidate_id, right_candidate_id")
})
public class NormalizedCandidatePairReviewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "left_candidate_id", nullable = false)
    private NormalizedContentCandidate leftCandidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "right_candidate_id", nullable = false)
    private NormalizedContentCandidate rightCandidate;

    /** Null for the very first decision recorded against this pair identity. */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_decision", length = 20)
    private HumanReviewDecision previousDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_decision", nullable = false, length = 20)
    private HumanReviewDecision newDecision;

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

    protected NormalizedCandidatePairReviewHistory() {
    }

    public NormalizedCandidatePairReviewHistory(NormalizedContentCandidate leftCandidate,
            NormalizedContentCandidate rightCandidate, HumanReviewDecision previousDecision,
            HumanReviewDecision newDecision, UserAccount reviewer, String note, Instant reviewedAt,
            Instant leftNormalizedAtSnapshot, Instant rightNormalizedAtSnapshot,
            NormalizedCandidateMatchAssessment assessmentSnapshot) {
        this.leftCandidate = Objects.requireNonNull(leftCandidate, "leftCandidate is required");
        this.rightCandidate = Objects.requireNonNull(rightCandidate, "rightCandidate is required");
        this.previousDecision = previousDecision;
        this.newDecision = Objects.requireNonNull(newDecision, "newDecision is required");
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

    public HumanReviewDecision getPreviousDecision() {
        return previousDecision;
    }

    public HumanReviewDecision getNewDecision() {
        return newDecision;
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
}
