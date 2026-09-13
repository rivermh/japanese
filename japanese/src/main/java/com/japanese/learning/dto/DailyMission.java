package com.japanese.learning.dto;

import java.time.LocalDate;

public record DailyMission(LocalDate date, boolean sessionSnapshot, boolean started, boolean completed,
        int total, int completedCount, MissionProgress review, MissionProgress newWords, MissionProgress newGrammar) {
    public int completionPercent() { return total == 0 ? 0 : Math.min(100, completedCount * 100 / total); }
}
