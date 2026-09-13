package com.japanese.learning.dto;

import java.time.Instant;

/** Bounded database aggregate for one content item's learning attempts. */
public record WeaknessStudyAggregate(
        Long contentItemId,
        long incorrectCount,
        long correctCount,
        Instant lastIncorrectAt,
        Instant lastCorrectAt
) { }
