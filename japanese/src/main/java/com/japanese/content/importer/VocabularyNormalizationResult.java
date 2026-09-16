package com.japanese.content.importer;

import java.util.List;
import java.util.Map;

/**
 * The pure, immutable output of {@link VocabularyNormalizationParser} for one private-staging
 * VOCABULARY note. Deliberately independent of any production entity (ContentItem/Word/Meaning/
 * Example) so it can be reviewed, diffed, or discarded without ever touching production tables.
 *
 * <p>{@code sourceRef}/{@code sourceNoteId} are provenance (where this came from) - they identify
 * a staging row, not a piece of vocabulary content. {@code entryId} is the source-native semantic
 * identity candidate; {@code expression}/{@code reading}/{@code level} are supporting identity
 * signals. This ticket does not decide a global identity/dedup policy - it only keeps the
 * candidates distinguishable for a future ticket to use.
 *
 * @param preservedExtraFields cleaned (HTML-stripped) values of fields that carry real content but
 *                             have no dedicated slot above (e.g. KanjiDetails, ConjugationDetails,
 *                             RelatedWords, KoreanRecallPrompt), keyed by their raw APKG field name,
 *                             plus any field this parser did not recognize at all (see
 *                             {@link VocabularyNormalizationIssue#UNKNOWN_EXTRA_FIELD}). Known
 *                             sentinel/placeholder fields (MeaningV2, Example1..5*, etc.) are never
 *                             included here.
 * @param validForPromotion    true iff {@code warnings} contains no {@link VocabularyNormalizationSeverity#FATAL}
 *                             entry. This is a pure content-completeness signal only - it is not a
 *                             review/publication decision and must not be treated as one.
 */
public record VocabularyNormalizationResult(
        String sourceRef,
        long sourceNoteId,
        String entryId,
        String expression,
        String reading,
        String partOfSpeech,
        NormalizedPitchAccent pitchAccent,
        List<NormalizedMeaning> meanings,
        List<NormalizedExample> examples,
        NormalizedJlptLevel level,
        String normalizedSearchExpression,
        String normalizedSearchReading,
        Map<String, String> preservedExtraFields,
        List<VocabularyNormalizationWarning> warnings,
        boolean validForPromotion
) {
    public VocabularyNormalizationResult {
        if (sourceRef == null || sourceRef.isBlank()) throw new IllegalArgumentException("sourceRef is required");
        meanings = List.copyOf(meanings);
        examples = List.copyOf(examples);
        preservedExtraFields = Map.copyOf(preservedExtraFields);
        warnings = List.copyOf(warnings);
    }

    public boolean hasIssue(VocabularyNormalizationIssue issue) {
        return warnings.stream().anyMatch(warning -> warning.issue() == issue);
    }
}
