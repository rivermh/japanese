package com.japanese.learning.dto;

import com.japanese.content.entity.ContentType;
import com.japanese.learning.entity.QuizQuestionType;
import java.util.List;

public record QuizQuestionView(Long itemId, int number, QuizQuestionType type, ContentType contentType,
        String instruction, String prompt, List<String> choices, boolean input) { }
