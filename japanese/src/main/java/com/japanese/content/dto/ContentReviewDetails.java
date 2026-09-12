package com.japanese.content.dto;

import java.util.List;

public record ContentReviewDetails(
        ContentReviewSummary summary,
        SourceDetails source,
        String partOfSpeech,
        String pitchAccent,
        List<ContentDetails.MeaningDetails> meanings,
        List<ContentDetails.ExampleDetails> examples,
        ContentDetails.GrammarDetails grammar,
        List<ReviewHistoryEntry> reviewHistory
) {
}
