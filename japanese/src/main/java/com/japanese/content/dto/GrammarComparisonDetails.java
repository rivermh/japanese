package com.japanese.content.dto;

import com.japanese.content.entity.GrammarRelationType;

public record GrammarComparisonDetails(
        GrammarLearningDetails.RelatedGrammar left,
        GrammarLearningDetails.RelatedGrammar right,
        GrammarRelationType relationType,
        String summary,
        String keyDifference,
        String usageDifference,
        String commonConfusion,
        String sourceRef
) { }
