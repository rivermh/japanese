package com.japanese.learning.dto;
import com.japanese.learning.entity.StudyResult;
import java.time.Instant;
public record QuizHistoryEntry(Long questionId, StudyResult result, Instant answeredAt) { }
