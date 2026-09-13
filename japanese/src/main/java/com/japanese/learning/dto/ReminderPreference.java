package com.japanese.learning.dto;

import java.time.LocalTime;

public record ReminderPreference(boolean enabled, LocalTime preferredTime, String timeZone) {}
