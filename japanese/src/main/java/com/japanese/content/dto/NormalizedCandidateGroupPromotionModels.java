package com.japanese.content.dto;

import java.util.List;

/**
 * JLPT-MAX Ticket 4E-3B: request/read-model DTOs for group-aware Vocabulary draft promotion.
 */
public final class NormalizedCandidateGroupPromotionModels {

    private NormalizedCandidateGroupPromotionModels() {
    }

    public record GroupPromotionResult(Long contentItemId, String slug) {
    }

    /**
     * A read-only, non-locking preview of whether a group currently looks promotable, for the admin
     * detail page only - the authoritative check is always the one performed fresh, under lock, inside
     * {@code NormalizedCandidateGroupPromotionService.promote} itself; this view can be stale the
     * moment after it is rendered and must never be trusted by the write path.
     */
    public record GroupPromotionEligibilityView(boolean eligible, List<String> blockingReasons) {
    }
}
