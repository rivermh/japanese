package com.japanese.learning.dto;

import java.time.LocalDate;
import java.util.List;

public record WeeklyLearningReport(
        LocalDate startDate,
        LocalDate endDate,
        WeeklyStudyAggregate currentStudy,
        WeeklyStudyAggregate previousStudy,
        WeeklyQuizAggregate currentQuiz,
        WeeklyQuizAggregate previousQuiz,
        WeeklyConfirmationAggregate currentConfirmation,
        WeeklyConfirmationAggregate previousConfirmation,
        int learningDays,
        int previousLearningDays,
        int currentStreak,
        List<WeeklyLearningDay> dailyActivity,
        List<WeeklyJlptProgressChange> jlptProgressChanges,
        List<WeaknessNoteItem> weakWords,
        List<WeaknessNoteItem> weakGrammar,
        List<WeaknessNoteItem> improvedWeaknesses,
        List<WeeklyRecommendation> recommendations
) {
    public long totalExperience() { return currentStudy.regularExperience() + currentQuiz.experience(); }
    public long previousTotalExperience() { return previousStudy.regularExperience() + previousQuiz.experience(); }
}
