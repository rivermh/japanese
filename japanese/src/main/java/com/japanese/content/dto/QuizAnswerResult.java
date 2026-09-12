package com.japanese.content.dto;

import com.japanese.learning.dto.StudyOverview;

public record QuizAnswerResult(
        boolean correct,
        String answerJapanese,
        String answerKorean,
        String explanation,
        StudyOverview overview
) {
}
