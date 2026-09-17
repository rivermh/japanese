package com.japanese.content.dto;

import com.japanese.content.entity.NormalizedCandidateCanonicalGroupStatus;
import java.time.Instant;
import java.util.List;

/**
 * JLPT-MAX Ticket 4E-3A: request/read-model DTOs for the human canonical-selection persistence
 * workflow. These never carry a production entity reference and never appear in any production admin
 * model - mirrors {@code NormalizedCandidatePairReviewModels}'/{@code PromotionReadinessModels}' own
 * separation between this private-candidate-domain workflow and production review/promotion.
 */
public final class NormalizedCandidateCanonicalGroupModels {

    private NormalizedCandidateCanonicalGroupModels() {
    }

    /** One participating candidate's render-time freshness token (JLPT-MAX Ticket 4E-3A). */
    public record ParticipantExpectation(Long candidateId, Instant expectedNormalizedAt) {
    }

    /**
     * One required C(N,2) edge's render-time freshness token - {@code leftCandidateId}/
     * {@code rightCandidateId} may be supplied in either order (the service canonically reorders them,
     * exactly like every other pair-identity concept in this codebase).
     */
    public record EdgeExpectation(Long leftCandidateId, Long rightCandidateId, Long expectedReviewVersion) {
    }

    /**
     * A full canonical-group creation submission - deliberately a structured request rather than
     * fragile parallel arrays (JLPT-MAX Ticket 4E-3A design review round 3), usable directly by both
     * the admin controller (built from simple named form fields for the smallest, two-candidate case)
     * and by a service-level caller supporting 3+ participants.
     */
    public record CanonicalGroupCreationRequest(Long canonicalCandidateId,
            List<ParticipantExpectation> participants, List<EdgeExpectation> edges, String note) {
    }

    public record CanonicalGroupCreationResult(Long groupId) {
    }

    public record CanonicalGroupListRow(Long id, NormalizedCandidateCanonicalGroupStatus status,
            Long canonicalCandidateId, String canonicalPreview, String sourceRef, int memberCount,
            String decidedByDisplayName, Instant decidedAt) {
    }

    public record CanonicalGroupListResult(List<CanonicalGroupListRow> rows) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    public record CanonicalGroupMemberView(Long candidateId, String preview, boolean canonical,
            Instant normalizedAtSnapshot) {
    }

    public record CanonicalGroupEdgeView(Long leftCandidateId, Long rightCandidateId,
            long pairReviewVersionSnapshot, Instant pairReviewedAtSnapshot, String reviewerDisplayName,
            String reviewDecisionSnapshot, String assessmentSnapshot, Instant leftNormalizedAtSnapshot,
            Instant rightNormalizedAtSnapshot) {
    }

    public record CanonicalGroupDetailView(Long id, NormalizedCandidateCanonicalGroupStatus status,
            Long canonicalCandidateId, String decidedByDisplayName, Instant decidedAt, String note,
            String dissolvedByDisplayName, Instant dissolvedAt, String dissolutionNote, long version,
            List<CanonicalGroupMemberView> members, List<CanonicalGroupEdgeView> edges) {
        public boolean active() {
            return status == NormalizedCandidateCanonicalGroupStatus.ACTIVE;
        }
    }
}
