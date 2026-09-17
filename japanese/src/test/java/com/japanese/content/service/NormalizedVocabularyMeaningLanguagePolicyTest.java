package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalizedVocabularyMeaningLanguagePolicyTest {

    private final NormalizedVocabularyMeaningLanguagePolicy policy = new NormalizedVocabularyMeaningLanguagePolicy();

    @Test
    void canonicalJlptMaxVocabularySourceResolvesToKorean() {
        assertThat(policy.resolveLanguageTag("JLPT-MAX-Deck-2.1.1.apkg")).contains("ko");
    }

    @Test
    void unknownSourceIsUnresolved() {
        assertThat(policy.resolveLanguageTag("some-other-source")).isEmpty();
    }

    @Test
    void nullSourceIsUnresolved() {
        assertThat(policy.resolveLanguageTag(null)).isEmpty();
    }

    @Test
    void neverGeneralizesToEveryUnrecognizedSourceBeingKorean() {
        // Guards against silently generalizing "every source is ko" - see this policy's javadoc.
        assertThat(policy.resolveLanguageTag("")).isEmpty();
        assertThat(policy.resolveLanguageTag("JLPT-MAX-Deck-2.1.1.apkg ")).isEmpty();
        assertThat(policy.resolveLanguageTag(" JLPT-MAX-Deck-2.1.1.apkg")).isEmpty();
        assertThat(policy.resolveLanguageTag("jlpt-max-deck-2.1.1.apkg")).isEmpty();
        assertThat(policy.resolveLanguageTag("JLPT-MAX-Deck-2.1.0.apkg")).isEmpty();
    }
}
