package com.japanese.learning.dto;

import com.japanese.content.entity.ContentType;

/** Period progress is intentionally defined as distinct contents first started during that period. */
public record WeeklyJlptProgressChange(String levelCode, ContentType contentType, long newlyStartedCount) { }
