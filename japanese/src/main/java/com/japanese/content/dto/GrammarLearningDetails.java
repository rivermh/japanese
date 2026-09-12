package com.japanese.content.dto;

import com.japanese.content.entity.GrammarRelationType;
import java.util.List;

public record GrammarLearningDetails(
        Enrichment enrichment,
        List<RelatedGrammar> related,
        boolean confirmationAvailable
) {
    public record Enrichment(String nuance, String usageNote, String formationSupplement, String commonMistake, String learnerNote, String sourceRef) { }
    public record RelatedGrammar(String slug, String pattern, String explanation, GrammarRelationType relationType,
                                 boolean comparisonAvailable) { }
}
