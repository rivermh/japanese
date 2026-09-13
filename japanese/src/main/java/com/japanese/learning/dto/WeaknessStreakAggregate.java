package com.japanese.learning.dto;

/** Incorrect attempts since the latest correct attempt for a content item. */
public record WeaknessStreakAggregate(Long contentItemId, long count) { }
