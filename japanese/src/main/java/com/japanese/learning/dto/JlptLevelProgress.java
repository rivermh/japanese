package com.japanese.learning.dto;

import java.util.List;

public record JlptLevelProgress(String code, String name, boolean inLearningScope,
                                List<ContentTypeProgress> contentTypes) {
}
