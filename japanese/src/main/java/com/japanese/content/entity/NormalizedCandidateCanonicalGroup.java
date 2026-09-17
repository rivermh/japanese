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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

/**
 * JLPT-MAX Ticket 4E-3A: one human-recorded canonical-selection decision over a complete, freshly
 * reviewed SAME_CONTENT group of {@link NormalizedContentCandidate} rows - the private-domain
 * decision that {@code candidateId} is the member whose normalized fields will eventually become one
 * production {@code ContentItem}'s content representation. This entity never writes to, references, or
 * is referenced by any production entity ({@code ContentItem}/{@code Word}/{@code Meaning}/
 * {@code Example}/{@code ImportedSourceRecord}) - production convergence is Ticket 4E-3B's separate,
 * later scope; recording this decision performs zero production writes and leaves the existing
 * {@code SAME_CONTENT_CANONICAL_SELECTION_REQUIRED} promotion-readiness blocker completely unchanged
 * for every participating candidate (see {@code NormalizedCandidateCanonicalGroupService}'s class
 * javadoc for the full boundary).
 *
 * <p><b>Immutability</b>: {@link #canonicalCandidate}/{@link #decidedBy}/{@link #decidedAt}/
 * {@link #note} are fixed at construction and never mutated afterward - there is no
 * "change canonical" or "add/remove member" operation anywhere on this entity or its service. A wrong
 * decision is corrected by {@link #dissolve dissolving} this group and creating an entirely new one,
 * never by editing this row in place (JLPT-MAX Ticket 4E-3A design review, schema v2/v3).
 *
 * <p><b>Lifecycle</b>: {@link NormalizedCandidateCanonicalGroupStatus#ACTIVE} means this decision
 * currently reserves every one of its {@link NormalizedCandidateCanonicalGroupMember} rows (via that
 * table's own {@code UNIQUE(member_candidate_id)} constraint) and may participate in a future Ticket
 * 4E-3B group-aware promotion; {@link NormalizedCandidateCanonicalGroupStatus#DISSOLVED} means the
 * decision is no longer authoritative, its former members have been released (their membership rows
 * deleted - see {@link com.japanese.content.service.NormalizedCandidateCanonicalGroupService}), but
 * this header row and every {@link NormalizedCandidateCanonicalGroupEdge} row it produced remain
 * permanently, unmodified, as the historical audit trail. ACTIVE is a lifecycle state, not a
 * live-freshness guarantee - the underlying Ticket 4B pairs/Ticket 4C reviews this decision was built
 * from can change or disappear afterward without this group auto-dissolving; a future Ticket 4E-3B
 * promotion path must revalidate all of that fresh before ever trusting an ACTIVE group.
 *
 * <p><b>No unique constraint on {@code canonicalCandidate}</b> (deliberate, not an oversight): a
 * DISSOLVED group's header row is kept forever for audit and still carries its old
 * {@code canonicalCandidate} value, so a global uniqueness on that column would wrongly prevent that
 * same candidate from ever being canonical again in a later group. The "at most one CURRENT
 * membership" invariant belongs entirely to {@link NormalizedCandidateCanonicalGroupMember}'s own
 * {@code UNIQUE(member_candidate_id)} constraint (rows deleted on dissolution), not here.
 */
@Entity
@Table(name = "normalized_candidate_canonical_groups")
public class NormalizedCandidateCanonicalGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "canonical_candidate_id", nullable = false)
    private NormalizedContentCandidate canonicalCandidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decided_by", nullable = false)
    private UserAccount decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(length = 2000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NormalizedCandidateCanonicalGroupStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dissolved_by")
    private UserAccount dissolvedBy;

    @Column(name = "dissolved_at")
    private Instant dissolvedAt;

    @Column(name = "dissolution_note", length = 2000)
    private String dissolutionNote;

    /**
     * Optimistic-concurrency guard (JLPT-MAX Ticket 4E-3A), mirroring
     * {@code NormalizedCandidatePairReview}'s own {@code @Version} precedent: this row is otherwise
     * write-once except for the single {@link #dissolve} transition, which two admins could otherwise
     * race to perform simultaneously.
     */
    @Version
    @Column(nullable = false)
    private long version;

    protected NormalizedCandidateCanonicalGroup() {
    }

    public NormalizedCandidateCanonicalGroup(NormalizedContentCandidate canonicalCandidate, UserAccount decidedBy,
            String note, Instant decidedAt) {
        this.canonicalCandidate = Objects.requireNonNull(canonicalCandidate, "canonicalCandidate is required");
        this.decidedBy = Objects.requireNonNull(decidedBy, "decidedBy is required");
        this.note = note;
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt is required");
        this.status = NormalizedCandidateCanonicalGroupStatus.ACTIVE;
    }

    /**
     * The single allowed lifecycle transition (JLPT-MAX Ticket 4E-3A) - rejects a group that is not
     * currently {@code ACTIVE} (a second dissolution attempt, or any other misuse), so this method can
     * never be called twice successfully against the same row.
     */
    public void dissolve(UserAccount dissolvedBy, Instant dissolvedAt, String dissolutionNote) {
        if (status != NormalizedCandidateCanonicalGroupStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE canonical group can be dissolved (current status: "
                    + status + ")");
        }
        this.dissolvedBy = Objects.requireNonNull(dissolvedBy, "dissolvedBy is required");
        this.dissolvedAt = Objects.requireNonNull(dissolvedAt, "dissolvedAt is required");
        this.dissolutionNote = dissolutionNote;
        this.status = NormalizedCandidateCanonicalGroupStatus.DISSOLVED;
    }

    public Long getId() {
        return id;
    }

    public NormalizedContentCandidate getCanonicalCandidate() {
        return canonicalCandidate;
    }

    public UserAccount getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getNote() {
        return note;
    }

    public NormalizedCandidateCanonicalGroupStatus getStatus() {
        return status;
    }

    public UserAccount getDissolvedBy() {
        return dissolvedBy;
    }

    public Instant getDissolvedAt() {
        return dissolvedAt;
    }

    public String getDissolutionNote() {
        return dissolutionNote;
    }

    public long getVersion() {
        return version;
    }
}
