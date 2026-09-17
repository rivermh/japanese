package com.japanese.content.entity;

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
 * JLPT-MAX Ticket 4E-3A: one candidate's CURRENT reservation to an
 * {@link NormalizedCandidateCanonicalGroupStatus#ACTIVE} {@link NormalizedCandidateCanonicalGroup} -
 * contains EVERY participating candidate, including the group's own canonical candidate (there is no
 * separate canonical-vs-member uniqueness model; the canonical candidate always also has a row here).
 *
 * <p>{@code UNIQUE(member_candidate_id)} (global, not scoped to {@code group_id}) is the sole
 * database-level guarantee that a candidate belongs to at most one current canonical group at a time -
 * this table is the ENTIRE cross-table double-membership fix from this ticket's design-review rounds:
 * unifying canonical and member into one column with one constraint, rather than two independent
 * uniqueness sources that could not otherwise be reconciled.
 *
 * <p>Rows exist ONLY for current, ACTIVE membership - a group's dissolution
 * ({@code NormalizedCandidateCanonicalGroupService}) explicitly deletes every one of its membership
 * rows in the same transaction as the status transition, which is what "releases" those candidates for
 * a possible future group. This table is therefore never the permanent audit source (that is
 * {@link NormalizedCandidateCanonicalGroupEdge}, whose rows are never deleted) - it only ever reflects
 * live reservation state.
 */
@Entity
@Table(name = "normalized_candidate_canonical_group_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_canonical_group_member_candidate",
                columnNames = "member_candidate_id"),
        indexes = @Index(name = "ix_canonical_group_member_group", columnList = "group_id"))
public class NormalizedCandidateCanonicalGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private NormalizedCandidateCanonicalGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_candidate_id", nullable = false)
    private NormalizedContentCandidate memberCandidate;

    @Column(name = "member_normalized_at_snapshot", nullable = false)
    private Instant memberNormalizedAtSnapshot;

    protected NormalizedCandidateCanonicalGroupMember() {
    }

    public NormalizedCandidateCanonicalGroupMember(NormalizedCandidateCanonicalGroup group,
            NormalizedContentCandidate memberCandidate, Instant memberNormalizedAtSnapshot) {
        this.group = Objects.requireNonNull(group, "group is required");
        this.memberCandidate = Objects.requireNonNull(memberCandidate, "memberCandidate is required");
        this.memberNormalizedAtSnapshot =
                Objects.requireNonNull(memberNormalizedAtSnapshot, "memberNormalizedAtSnapshot is required");
    }

    public Long getId() {
        return id;
    }

    public NormalizedCandidateCanonicalGroup getGroup() {
        return group;
    }

    public NormalizedContentCandidate getMemberCandidate() {
        return memberCandidate;
    }

    public Instant getMemberNormalizedAtSnapshot() {
        return memberNormalizedAtSnapshot;
    }
}
