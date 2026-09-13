package com.japanese.learning.dto;

import com.japanese.learning.entity.QuizMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record QuizStartRequest(QuizMode mode, @Min(1) @Max(20) Integer count) {
    public QuizMode effectiveMode() { return mode == null ? QuizMode.QUICK : mode; }
    public int effectiveCount() { return count == null ? 5 : count; }
}
