package com.japanese.content.service;

/**
 * JLPT-MAX Ticket 4E-8: the outcome of one successful
 * {@link NormalizedGrammarCandidatePromotionService#promote} call - the identity of the freshly
 * created (always {@code published = false}) production draft {@code ContentItem}.
 */
public record NormalizedGrammarCandidatePromotionResult(Long contentItemId, String slug) {
}
