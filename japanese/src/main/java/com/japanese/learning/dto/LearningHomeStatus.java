package com.japanese.learning.dto;

import java.time.LocalDate;

public record LearningHomeStatus(LocalDate date, DailyMission mission, NextLearningAction nextAction, LearningReminder reminder) {}
