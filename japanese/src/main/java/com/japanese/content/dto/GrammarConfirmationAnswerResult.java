package com.japanese.content.dto;

public record GrammarConfirmationAnswerResult(
        boolean correct, String correctChoice, String explanation, String grammarSlug, String grammarPattern
) { }
