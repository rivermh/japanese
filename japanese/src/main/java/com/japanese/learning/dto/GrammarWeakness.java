package com.japanese.learning.dto;

import com.japanese.learning.entity.LearningState;

public record GrammarWeakness(String slug, String pattern, long attempts, long incorrectAnswers,
                              LearningState learningState) { }
