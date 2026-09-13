package com.japanese.learning.dto;

import java.time.Instant;
import java.time.LocalDate;

public record LearningReminder(String type, String message, boolean visible, boolean learningNeeded,
        boolean reviewScheduled, boolean dailyMissionCompleted, boolean sufficientLearning,
        int currentStreak, LocalDate lastStudyDate, Instant nextReviewAt, ReminderPreference preference) {}
