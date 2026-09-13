package com.japanese.learning.dto;

public record QuizIncorrectItem(String slug, String title, String submittedAnswer, String correctAnswer,
        String explanation) { }
