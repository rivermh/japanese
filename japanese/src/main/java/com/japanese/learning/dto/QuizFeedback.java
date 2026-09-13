package com.japanese.learning.dto;

public record QuizFeedback(Long itemId, boolean correct, String submittedAnswer, String correctAnswer,
        String explanation, String contentSlug, String contentTitle, int earnedExperience) { }
