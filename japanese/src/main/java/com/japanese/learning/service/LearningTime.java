package com.japanese.learning.service;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LearningTime {
    private final ZoneId zone;
    private final Clock clock;
    public LearningTime(@Value("${japanese.learning.time-zone:Asia/Seoul}") String zone, Clock clock) {
        this.zone = ZoneId.of(zone); this.clock = clock;
    }
    public Instant now() { return clock.instant(); }
    public LocalDate today() { return LocalDate.now(clock.withZone(zone)); }
    public LocalTime localTime() { return LocalTime.now(clock.withZone(zone)); }
    public Instant startOfToday() { return today().atStartOfDay(zone).toInstant(); }
    public ZoneId zone() { return zone; }
    public LocalDate dateAt(Instant instant) { return instant.atZone(zone).toLocalDate(); }
    public LocalTime timeAt(Instant instant) { return instant.atZone(zone).toLocalTime(); }
}
