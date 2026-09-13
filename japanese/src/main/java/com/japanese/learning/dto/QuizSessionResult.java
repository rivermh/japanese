package com.japanese.learning.dto;

import java.util.List;

public record QuizSessionResult(String sessionId, int totalQuestions, int correctCount, int incorrectCount,
        int accuracyPercent, int earnedExperience, List<QuizIncorrectItem> incorrectItems) { }
