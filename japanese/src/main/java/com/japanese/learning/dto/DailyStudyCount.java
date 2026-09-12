package com.japanese.learning.dto;

import java.time.LocalDate;

public record DailyStudyCount(LocalDate date, long count) {
}
