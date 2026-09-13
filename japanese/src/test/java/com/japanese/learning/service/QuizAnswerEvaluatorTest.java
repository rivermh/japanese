package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.learning.entity.QuizQuestionType;
import org.junit.jupiter.api.Test;

class QuizAnswerEvaluatorTest {
    private final QuizAnswerEvaluator evaluator = new QuizAnswerEvaluator();

    @Test
    void readingComparisonUsesNfkcWhitespaceAndKatakanaToHiraganaNormalization() {
        assertThat(evaluator.matches(QuizQuestionType.WORD_READING_INPUT, "  タベル　", "たべる")).isTrue();
        assertThat(evaluator.matches(QuizQuestionType.WORD_READING_INPUT, "taberu", "たべる")).isFalse();
        assertThat(evaluator.matches(QuizQuestionType.WORD_READING_INPUT, "먹다", "たべる")).isFalse();
    }
}
