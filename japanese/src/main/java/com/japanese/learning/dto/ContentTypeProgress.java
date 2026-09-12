package com.japanese.learning.dto;

import com.japanese.content.entity.ContentType;

public record ContentTypeProgress(ContentType type, long total, long unstarted, long learning,
                                  long review, long mastered, long suspended, int completionPercent) {
}
