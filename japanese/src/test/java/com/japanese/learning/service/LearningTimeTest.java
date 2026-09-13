package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LearningTimeTest {
    @Test
    void usesSeoulMidnightInsteadOfTheServerDefaultTimeZone() {
        LearningTime beforeMidnight = new LearningTime("Asia/Seoul",
                Clock.fixed(Instant.parse("2026-09-12T14:59:59Z"), ZoneOffset.UTC));
        LearningTime atMidnight = new LearningTime("Asia/Seoul",
                Clock.fixed(Instant.parse("2026-09-12T15:00:00Z"), ZoneOffset.UTC));

        assertThat(beforeMidnight.today()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(beforeMidnight.localTime()).isEqualTo(LocalTime.of(23, 59, 59));
        assertThat(atMidnight.today()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(atMidnight.startOfToday()).isEqualTo(Instant.parse("2026-09-12T15:00:00Z"));
    }
}
