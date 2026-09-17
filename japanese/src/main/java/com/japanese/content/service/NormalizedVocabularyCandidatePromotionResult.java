package com.japanese.content.service;

/**
 * JLPT-MAX Ticket 4E-1: the outcome of one successful
 * {@link NormalizedVocabularyCandidatePromotionService#promote} call - the identity of the freshly
 * created (always {@code published = false}) production draft {@code ContentItem}.
 */
public record NormalizedVocabularyCandidatePromotionResult(Long contentItemId, String slug) {
}
