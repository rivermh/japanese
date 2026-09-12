package com.japanese.learning.dto;

import java.time.YearMonth;
import java.util.List;

public record MonthlyLearningActivity(YearMonth month, List<LearningActivityDay> days) {
}
