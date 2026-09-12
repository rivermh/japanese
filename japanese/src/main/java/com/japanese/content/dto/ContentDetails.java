package com.japanese.content.dto;

import java.util.List;

public record ContentDetails(
        ContentSummary summary,
        SourceDetails source,
        String partOfSpeech,
        String pitchAccent,
        List<MeaningDetails> meanings,
        List<ExampleDetails> examples,
        List<MeaningGroup> meaningGroups,
        List<ExampleDetails> unlinkedExamples,
        GrammarDetails grammar,
        PitchAccentDisplay pitchAccentDisplay
) {

    public record MeaningDetails(String languageTag, String text, int order) {
    }

    public record ExampleDetails(
            String meaning,
            String japaneseText,
            String reading,
            String translation,
            String audioFileName,
            int order
    ) {
    }

    public record MeaningGroup(MeaningDetails meaning, List<ExampleDetails> examples) {
    }

    public record GrammarDetails(String pattern, String explanation, String connection, GrammarLearningDetails learning) {
    }

    public record PitchAccentDisplay(String label, Integer downstepAfterMora, boolean rawOnly) {
    }
}
