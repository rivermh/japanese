package com.japanese.learning.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LearningStreakTest {

    @Test
    void continuesOnlyOnConsecutiveDaysAndKeepsBestRecord() {
        LearningStreak streak = new LearningStreak(new LearnerProfile("test", "테스트", "haru"));
        LocalDate start = LocalDate.of(2026, 9, 8);

        streak.recordActivity(start);
        streak.recordActivity(start.plusDays(1));
        streak.recordActivity(start.plusDays(1));
        streak.recordActivity(start.plusDays(3));

        assertThat(streak.getCurrentStreak()).isEqualTo(1);
        assertThat(streak.getLongestStreak()).isEqualTo(2);
        assertThat(streak.getLastActivityDate()).isEqualTo(start.plusDays(3));
    }
}
