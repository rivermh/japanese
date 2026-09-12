package com.japanese.content.dto;

import com.japanese.content.entity.GrammarConfirmationType;
import java.util.List;

public record GrammarConfirmationQuestionDetails(
        Long id, String grammarSlug, String grammarPattern, GrammarConfirmationType type,
        String prompt, String context, List<Choice> choices
) {
    public record Choice(Long id, String text) { }
}
