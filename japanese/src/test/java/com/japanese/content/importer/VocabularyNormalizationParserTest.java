package com.japanese.content.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VocabularyNormalizationParserTest {

    private final VocabularyNormalizationParser parser = new VocabularyNormalizationParser(new ExampleHtmlParser());

    private static final String EXAMPLE_HTML = """
            <section class="_j47" aria-label="뜻 묶음: 먹다">
              <div class="_j48" lang="ja"><ruby><rb>毎朝</rb><rt>まいあさ</rt></ruby>ご飯を食べます。</div>
              <div class="_jau" lang="ja"><ruby><rb>毎朝</rb><rt>まいあさ</rt></ruby>ご飯を食べます。</div>
              <div class="_j49">매일 아침밥을 먹습니다.</div>
            </section>
            """;

    private static Map<String, String> vocab(Map<String, String> overrides) {
        Map<String, String> fields = new HashMap<>();
        fields.put("EntryID", "jlpt-max-vocab-000123456789012");
        fields.put("Word", "食べる");
        fields.put("Reading", "たべる");
        fields.put("PartOfSpeech", "동사");
        fields.put("PitchAccent", "<span data-pitch-terminal-states=\"HLL\" j1=\"タ・ベ・ル\"></span>");
        fields.put("Meaning", "먹다");
        fields.put("ExamplesRendered", EXAMPLE_HTML);
        fields.put("JLPT", "N5");
        fields.put("WordJLPT", "N5");
        fields.putAll(overrides);
        return fields;
    }

    // ===== A. normal vocabulary =====

    @Test
    void normalVocabularyProducesFullyPopulatedValidResult() {
        VocabularyNormalizationResult result = parser.parse("private-jlpt-max", 1L, vocab(Map.of()));

        assertThat(result.sourceRef()).isEqualTo("private-jlpt-max");
        assertThat(result.sourceNoteId()).isEqualTo(1L);
        assertThat(result.entryId()).isEqualTo("jlpt-max-vocab-000123456789012");
        assertThat(result.expression()).isEqualTo("食べる");
        assertThat(result.reading()).isEqualTo("たべる");
        assertThat(result.partOfSpeech()).isEqualTo("동사");
        assertThat(result.pitchAccent()).isEqualTo(new NormalizedPitchAccent("HLL", "タ・ベ・ル"));
        assertThat(result.meanings()).containsExactly(new NormalizedMeaning(1, "먹다"));
        assertThat(result.examples()).hasSize(1);
        assertThat(result.examples().get(0).japaneseText()).isEqualTo("毎朝ご飯を食べます。");
        assertThat(result.examples().get(0).reading()).isEqualTo("まいあさご飯を食べます。");
        assertThat(result.examples().get(0).translation()).isEqualTo("매일 아침밥을 먹습니다.");
        assertThat(result.examples().get(0).meaningLabel()).isEqualTo("먹다");
        assertThat(result.level()).isEqualTo(new NormalizedJlptLevel("N5", "N5", "WordJLPT"));
        assertThat(result.normalizedSearchExpression()).isNotBlank();
        assertThat(result.normalizedSearchReading()).isNotBlank();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.validForPromotion()).isTrue();
    }

    @Test
    void multipleMeaningsAndExamplesArePreservedInOrder() {
        Map<String, String> fields = vocab(Map.of("Meaning", "먹다 / 마시다 / 삼키다"));
        VocabularyNormalizationResult result = parser.parse("s", 2L, fields);

        assertThat(result.meanings()).containsExactly(
                new NormalizedMeaning(1, "먹다"),
                new NormalizedMeaning(2, "마시다"),
                new NormalizedMeaning(3, "삼키다"));
    }

    // ===== B. meaning normalization =====

    @Test
    void singleMeaningIsNotSplit() {
        var result = parser.parse("s", 1L, vocab(Map.of("Meaning", "먹다")));
        assertThat(result.meanings()).containsExactly(new NormalizedMeaning(1, "먹다"));
    }

    @Test
    void multipleSlashSeparatedMeaningsAreSplitAndTrimmedWhenSpacingIsSymmetric() {
        var result = parser.parse("s", 1L, vocab(Map.of("Meaning", "  먹다  /  마시다  /  삼키다  ")));
        assertThat(result.meanings()).containsExactly(
                new NormalizedMeaning(1, "먹다"),
                new NormalizedMeaning(2, "마시다"),
                new NormalizedMeaning(3, "삼키다"));
    }

    @Test
    void slashWithoutSurroundingWhitespaceIsTreatedAsPartOfTheText() {
        // A slash with no space around it is real text (e.g. a ratio/compound term), not a separator.
        var result = parser.parse("s", 1L, vocab(Map.of("Meaning", "그리고/또는")));
        assertThat(result.meanings()).containsExactly(new NormalizedMeaning(1, "그리고/또는"));
    }

    @Test
    void asymmetricSpacingAroundASlashIsConservativelyNotSplit() {
        // Only whitespace on one side of the slash is ambiguous, so this parser leaves it as one
        // fragment rather than guessing - splitting here risks cutting a real compound term in half.
        var result = parser.parse("s", 1L, vocab(Map.of("Meaning", "먹다 / 마시다 /삼키다")));
        assertThat(result.meanings()).containsExactly(
                new NormalizedMeaning(1, "먹다"),
                new NormalizedMeaning(2, "마시다 /삼키다"));
    }

    @Test
    void repeatedSeparatorArtifactCollapsesInsteadOfLeavingAStrayFragment() {
        // "A / / B" (a stray double-slash, e.g. an empty sense slot) must not leave "/ B" as text.
        var result = parser.parse("s", 1L, vocab(Map.of("Meaning", "먹다 / / 마시다")));
        assertThat(result.meanings()).containsExactly(
                new NormalizedMeaning(1, "먹다"),
                new NormalizedMeaning(2, "마시다"));
    }

    @Test
    void trailingSeparatorWithNothingAfterIsDropped() {
        var result = parser.parse("s", 1L, vocab(Map.of("Meaning", "먹다 / ")));
        assertThat(result.meanings()).containsExactly(new NormalizedMeaning(1, "먹다"));
    }

    @Test
    void duplicateMeaningsAreNotSilentlyDeduplicated() {
        var result = parser.parse("s", 1L, vocab(Map.of("Meaning", "먹다 / 먹다")));
        assertThat(result.meanings()).containsExactly(
                new NormalizedMeaning(1, "먹다"),
                new NormalizedMeaning(2, "먹다"));
    }

    @Test
    void blankMeaningIsMissingMeaningWarningNotEmptyList() {
        var result = parser.parse("s", 1L, vocab(Map.of("Meaning", "   ")));
        assertThat(result.meanings()).isEmpty();
        assertThat(result.hasIssue(VocabularyNormalizationIssue.MISSING_MEANING)).isTrue();
        assertThat(result.validForPromotion()).isFalse();
    }

    // ===== C. example normalization =====

    @Test
    void multipleExamplesKeepDisplayOrderAndContent() {
        String twoExamples = EXAMPLE_HTML + """
                <section class="_j47" aria-label="뜻 묶음: 마시다">
                  <div class="_j48" lang="ja">水を飲みます。</div>
                  <div class="_jau" lang="ja">みずをのみます。</div>
                  <div class="_j49">물을 마십니다.</div>
                </section>
                """;
        var result = parser.parse("s", 1L, vocab(Map.of("ExamplesRendered", twoExamples)));

        assertThat(result.examples()).hasSize(2);
        assertThat(result.examples().get(0).displayOrder()).isEqualTo(1);
        assertThat(result.examples().get(1).displayOrder()).isEqualTo(2);
        assertThat(result.examples().get(1).japaneseText()).isEqualTo("水を飲みます。");
        assertThat(result.examples().get(1).translation()).isEqualTo("물을 마십니다.");
        assertThat(result.hasIssue(VocabularyNormalizationIssue.EXAMPLE_PARSE_FALLBACK)).isFalse();
    }

    @Test
    void exampleAudioReferenceIsFlaggedNotReintroduced() {
        String withAudio = """
                <section class="_j47" aria-label="뜻 묶음: 먹다">
                  <div class="_j48" lang="ja">食べます。</div>
                  <div class="_jau" lang="ja">たべます。</div>
                  <audio src="leftover.mp3"></audio>
                  <div class="_j49">먹습니다.</div>
                </section>
                """;
        var result = parser.parse("s", 1L, vocab(Map.of("ExamplesRendered", withAudio)));

        assertThat(result.examples()).singleElement()
                .extracting(NormalizedExample::japaneseText)
                .isEqualTo("食べます。");
        assertThat(result.hasIssue(VocabularyNormalizationIssue.AUDIO_REFERENCE_UNEXPECTED)).isTrue();
        // The normalized example type itself has no audio field/getter to reintroduce.
    }

    @Test
    void malformedExampleMarkupFallsBackWithWarningInsteadOfThrowing() {
        var result = parser.parse("s", 1L, vocab(Map.of("ExamplesRendered", "<div>not a recognized example section</div>")));

        assertThat(result.examples()).isEmpty();
        assertThat(result.hasIssue(VocabularyNormalizationIssue.EXAMPLE_PARSE_FALLBACK)).isTrue();
        // A parse fallback alone must not block promotion by itself if content fields are otherwise complete.
    }

    @Test
    void blankExamplesRenderedProducesEmptyListWithoutWarning() {
        var result = parser.parse("s", 1L, vocab(Map.of("ExamplesRendered", "")));
        assertThat(result.examples()).isEmpty();
        assertThat(result.hasIssue(VocabularyNormalizationIssue.EXAMPLE_PARSE_FALLBACK)).isFalse();
    }

    // ===== D. JLPT level normalization =====

    @Test
    void wordJlptTakesPriorityOverJlpt() {
        var result = parser.parse("s", 1L, vocab(Map.of("WordJLPT", "N3", "JLPT", "N5")));
        assertThat(result.level()).isEqualTo(new NormalizedJlptLevel("N3", "N3", "WordJLPT"));
    }

    @Test
    void jlptIsUsedWhenWordJlptIsBlank() {
        var result = parser.parse("s", 1L, vocab(Map.of("WordJLPT", "", "JLPT", "N4")));
        assertThat(result.level()).isEqualTo(new NormalizedJlptLevel("N4", "N4", "JLPT"));
    }

    @Test
    void invalidLevelValueIsFlaggedNotGuessed() {
        var result = parser.parse("s", 1L, vocab(Map.of("WordJLPT", "beginner", "JLPT", "")));
        assertThat(result.level().code()).isNull();
        assertThat(result.level().rawValue()).isEqualTo("beginner");
        assertThat(result.hasIssue(VocabularyNormalizationIssue.INVALID_JLPT_LEVEL)).isTrue();
    }

    @Test
    void missingLevelIsNullWithoutInventingOne() {
        var result = parser.parse("s", 1L, vocab(Map.of("WordJLPT", "", "JLPT", "")));
        assertThat(result.level()).isEqualTo(new NormalizedJlptLevel(null, null, null));
        assertThat(result.hasIssue(VocabularyNormalizationIssue.INVALID_JLPT_LEVEL)).isFalse();
    }

    // ===== E. required field missing =====

    @Test
    void missingEntryIdIsFatalButStillProducesAResult() {
        var result = parser.parse("s", 1L, vocab(Map.of("EntryID", "")));
        assertThat(result.entryId()).isNull();
        assertThat(result.hasIssue(VocabularyNormalizationIssue.MISSING_ENTRY_ID)).isTrue();
        assertThat(result.validForPromotion()).isFalse();
    }

    @Test
    void missingWordIsFatal() {
        var result = parser.parse("s", 1L, vocab(Map.of("Word", "")));
        assertThat(result.hasIssue(VocabularyNormalizationIssue.MISSING_EXPRESSION)).isTrue();
        assertThat(result.validForPromotion()).isFalse();
    }

    @Test
    void missingReadingIsFatal() {
        var result = parser.parse("s", 1L, vocab(Map.of("Reading", "")));
        assertThat(result.hasIssue(VocabularyNormalizationIssue.MISSING_READING)).isTrue();
        assertThat(result.validForPromotion()).isFalse();
    }

    @Test
    void missingPartOfSpeechIsReviewRequiredNotFatal() {
        var result = parser.parse("s", 1L, vocab(Map.of("PartOfSpeech", "")));
        assertThat(result.hasIssue(VocabularyNormalizationIssue.EMPTY_PART_OF_SPEECH)).isTrue();
        assertThat(result.validForPromotion()).isTrue();
    }

    @Test
    void retiredNoteWithAllContentFieldsMissingProducesInvalidResultNotAnException() {
        Map<String, String> fields = vocab(Map.of(
                "Word", "",
                "Reading", "",
                "Meaning", "",
                "PartOfSpeech", "",
                "ExamplesRendered", "",
                "RetiredKanaAuxiliaryCard", "jlpt-max-vocab-000999999999999"));

        VocabularyNormalizationResult result = parser.parse("private-jlpt-max", 999L, fields);

        assertThat(result.entryId()).isNotNull();
        assertThat(result.validForPromotion()).isFalse();
        assertThat(result.hasIssue(VocabularyNormalizationIssue.MISSING_EXPRESSION)).isTrue();
        assertThat(result.hasIssue(VocabularyNormalizationIssue.MISSING_READING)).isTrue();
        assertThat(result.hasIssue(VocabularyNormalizationIssue.MISSING_MEANING)).isTrue();
        assertThat(result.preservedExtraFields()).containsEntry("RetiredKanaAuxiliaryCard", "jlpt-max-vocab-000999999999999");
    }

    // ===== F. additional meaningful fields =====

    @Test
    void meaningfulExtraFieldsArePreservedNotDiscarded() {
        Map<String, String> fields = vocab(Map.of(
                "KanjiDetails", "<div>食: 먹을 식</div>",
                "ConjugationDetails", "<div>먹다 -> 먹어요</div>",
                "RelatedWords", "<div>飲む</div>",
                "KoreanRecallPrompt", "먹는 것을 뜻하는 동사는?"));

        var result = parser.parse("s", 1L, fields);

        assertThat(result.preservedExtraFields())
                .containsEntry("KanjiDetails", "食: 먹을 식")
                .containsEntry("ConjugationDetails", "먹다 -> 먹어요")
                .containsEntry("RelatedWords", "飲む")
                .containsEntry("KoreanRecallPrompt", "먹는 것을 뜻하는 동사는?");
        assertThat(result.hasIssue(VocabularyNormalizationIssue.UNKNOWN_EXTRA_FIELD)).isFalse();
    }

    @Test
    void unrecognizedFieldNameIsFlaggedButStillPreserved() {
        var result = parser.parse("s", 1L, vocab(Map.of("SomeFutureField", "new content")));
        assertThat(result.hasIssue(VocabularyNormalizationIssue.UNKNOWN_EXTRA_FIELD)).isTrue();
        assertThat(result.preservedExtraFields()).containsEntry("SomeFutureField", "new content");
    }

    /**
     * Every field name actually present on the real "JLPT MAX덱 어휘" note type (confirmed via
     * -Djapanese.actual-apkg field-inventory profiling) must be classified as primary, sentinel, or
     * known-extra. This test caught a real bug during development: UsageDetails was missing from
     * KNOWN_EXTRA_FIELDS, which raised a false UNKNOWN_EXTRA_FIELD warning on all 9,160 real notes.
     */
    @Test
    void everyRealVocabularyFieldNameIsClassifiedSoNoneAreFlaggedAsUnknown() {
        Map<String, String> allRealFields = vocab(Map.of());
        for (String field : List.of(
                "VocabularyContext", "MeaningV2", "ExamplesV2", "MediaIntentsV2", "CanonicalRecordHash",
                "WordAudio", "WordAudioFile", "KanjiDetails", "UsageRegister", "UsageDetails",
                "ConjugationDetails", "WordFormationDetails", "RelatedWords", "StudyPriority",
                "RetiredKanaAuxiliaryCard", "KoreanRecallPrompt",
                "KanaAuxiliaryWord", "KanaAuxiliaryReading", "KanaAuxiliaryContext", "KanaAuxiliaryJLPT")) {
            allRealFields.put(field, "x");
        }
        for (int example = 1; example <= 5; example++) {
            for (String suffix : List.of("JP", "Reading", "KO", "Sense", "Audio", "PitchAccent")) {
                allRealFields.put("Example" + example + suffix, "x");
            }
        }

        var result = parser.parse("s", 1L, allRealFields);

        assertThat(result.hasIssue(VocabularyNormalizationIssue.UNKNOWN_EXTRA_FIELD)).isFalse();
    }

    // ===== G. placeholder/sentinel fields =====

    @Test
    void sentinelFieldsAreNeverTreatedAsContentEvenWhenPopulated() {
        Map<String, String> fields = vocab(Map.of(
                "MeaningV2", "x",
                "ExamplesV2", "x",
                "MediaIntentsV2", "x",
                "CanonicalRecordHash", "x",
                "Example1JP", "x",
                "KanaAuxiliaryWord", "x"));

        var result = parser.parse("s", 1L, fields);

        assertThat(result.preservedExtraFields()).doesNotContainKeys(
                "MeaningV2", "ExamplesV2", "MediaIntentsV2", "CanonicalRecordHash", "Example1JP", "KanaAuxiliaryWord");
        assertThat(result.hasIssue(VocabularyNormalizationIssue.UNKNOWN_EXTRA_FIELD)).isFalse();
        // The real Meaning/example content still comes from Meaning/ExamplesRendered, not the V2 sentinels.
        assertThat(result.meanings()).containsExactly(new NormalizedMeaning(1, "먹다"));
    }

    // ===== pitch accent =====

    @Test
    void pitchAccentParseFailureIsFlaggedAndNull() {
        var result = parser.parse("s", 1L, vocab(Map.of("PitchAccent", "<span>no pitch data</span>")));
        assertThat(result.pitchAccent()).isNull();
        assertThat(result.hasIssue(VocabularyNormalizationIssue.PITCH_ACCENT_PARSE_FAILED)).isTrue();
    }

    @Test
    void blankPitchAccentIsNullWithoutWarning() {
        var result = parser.parse("s", 1L, vocab(Map.of("PitchAccent", "")));
        assertThat(result.pitchAccent()).isNull();
        assertThat(result.hasIssue(VocabularyNormalizationIssue.PITCH_ACCENT_PARSE_FAILED)).isFalse();
    }

    // ===== identity / provenance =====

    @Test
    void sourceNoteIdIsProvenanceOnlyEntryIdIsTheIdentityCandidate() {
        var first = parser.parse("private-jlpt-max", 111L, vocab(Map.of()));
        var second = parser.parse("private-jlpt-max", 222L, vocab(Map.of()));

        assertThat(first.sourceNoteId()).isNotEqualTo(second.sourceNoteId());
        assertThat(first.entryId()).isEqualTo(second.entryId());
    }

    // ===== H. determinism =====

    @Test
    void sameInputProducesEqualOutput() {
        Map<String, String> fields = vocab(Map.of("Meaning", "먹다 / 마시다"));
        var first = parser.parse("private-jlpt-max", 42L, fields);
        var second = parser.parse("private-jlpt-max", 42L, fields);

        assertThat(first).isEqualTo(second);
    }

    // ===== pure-parser guardrails =====

    @Test
    void doesNotMutateTheInputMap() {
        Map<String, String> fields = vocab(Map.of());
        Map<String, String> copy = new HashMap<>(fields);

        parser.parse("s", 1L, fields);

        assertThat(fields).isEqualTo(copy);
    }

    @Test
    void rejectsNullSourceRefAndNullFieldMapWithoutTouchingAnything() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parser.parse(null, 1L, vocab(Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parser.parse("s", 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
