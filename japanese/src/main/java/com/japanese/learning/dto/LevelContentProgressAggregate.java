package com.japanese.learning.dto;

import com.japanese.content.entity.ContentType;

/** One database aggregate row: a JLPT level and a content type. */
public record LevelContentProgressAggregate(
        String levelCode, String levelName, ContentType contentType,
        long total, long unstarted, long learning, long review, long mastered, long suspended) {
}
