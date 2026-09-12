package com.japanese.learning.dto;
public record LearningStatistics(long totalAnswers, long correctAnswers, long incorrectAnswers, int accuracyPercent,
                                 int experience, int level, long todayCompleted, long recentSevenDaysCompleted) { }
