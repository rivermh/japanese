package com.japanese.learning.dto;

import com.japanese.learning.entity.QuizMode;
import com.japanese.learning.entity.QuizSessionState;

public record QuizSessionView(String sessionId, QuizMode mode, QuizSessionState state, int totalQuestions,
        int answeredCount, int correctCount, int earnedExperience, QuizQuestionView currentQuestion) {
    public boolean completed() { return state == QuizSessionState.COMPLETED; }
    public int remainingCount() { return Math.max(totalQuestions - answeredCount, 0); }
    public int progressPercent() { return totalQuestions == 0 ? 0 : answeredCount * 100 / totalQuestions; }
}
