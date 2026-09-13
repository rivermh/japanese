package com.japanese.learning.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record ReminderPreferenceRequest(boolean enabled, @NotNull LocalTime preferredTime) {}
