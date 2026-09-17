package com.japanese.content.service;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * JLPT-MAX Ticket 4E-0: resolves the production {@code Meaning.languageTag} a Vocabulary candidate's
 * meanings would be promoted with, if any. {@code NormalizedVocabularyCandidateMeaning} carries no
 * language field of its own - {@code "ko"} is only ever the sole existing production {@code Meaning}
 * writer's ({@code ApkgVocabularyImporter.createMeanings}) convention, and only for that importer's
 * one canonical source. This policy therefore never generalizes "every source is ko": only the
 * canonical JLPT-MAX Vocabulary {@code sourceRef} - {@code ApkgVocabularyImporter.SOURCE_REF}'s literal
 * value, confirmed independently here since that field is {@code private} - resolves; every other or
 * unknown {@code sourceRef} is left unresolved rather than defaulted.
 */
@Service
public class NormalizedVocabularyMeaningLanguagePolicy {

    /** Matches {@code ApkgVocabularyImporter.SOURCE_REF} - see this class's javadoc. */
    private static final String CANONICAL_JLPT_MAX_VOCABULARY_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";
    private static final String CANONICAL_JLPT_MAX_LANGUAGE_TAG = "ko";

    /** Empty when {@code sourceRef} is not a recognized source profile - never defaults to {@code "ko"}. */
    public Optional<String> resolveLanguageTag(String sourceRef) {
        if (CANONICAL_JLPT_MAX_VOCABULARY_SOURCE_REF.equals(sourceRef)) {
            return Optional.of(CANONICAL_JLPT_MAX_LANGUAGE_TAG);
        }
        return Optional.empty();
    }
}
