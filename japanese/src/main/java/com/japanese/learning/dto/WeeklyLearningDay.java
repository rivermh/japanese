package com.japanese.learning.dto;

import java.time.LocalDate;

public record WeeklyLearningDay(LocalDate date, long regularCount, long retrainCount, long experience) { }
