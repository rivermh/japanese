package com.japanese.learning.entity;

public enum QuizQuestionType {
    WORD_JAPANESE_TO_MEANING(false),
    WORD_MEANING_TO_JAPANESE(false),
    WORD_READING_CHOICE(false),
    WORD_READING_INPUT(true),
    GRAMMAR_PATTERN_TO_MEANING(false),
    GRAMMAR_MEANING_TO_PATTERN(false),
    GRAMMAR_CONTEXT_CHOICE(false);

    private final boolean input;

    QuizQuestionType(boolean input) {
        this.input = input;
    }

    public boolean isInput() {
        return input;
    }
}
