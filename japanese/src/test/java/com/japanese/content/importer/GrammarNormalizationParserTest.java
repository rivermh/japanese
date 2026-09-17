package com.japanese.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Fixtures here mirror the corrected BackHTML semantic model confirmed by a full 1,078-note
 * real-APKG re-review during Ticket 3B-1 (zero exceptions): section._j4a always holds exactly
 * three label-identified reference cards - Nuance/Connection/Confusable-patterns - never bilingual
 * "examples", and div._j4z is the front-sentence translation, never a grammar "explanation".
 */
class GrammarNormalizationParserTest {

    private final GrammarNormalizationParser parser = new GrammarNormalizationParser();

    private static final String FRONT_HTML =
            "<section class=\"_j4t\"><div class=\"_j4u\">"
                    + "<mark class=\"_j4y\">あの</mark>かばんは田中さんのです。"
                    + "</div></section>";

    private static final String NUANCE_SECTION = """
              <section class="_j4a _j4w">
                <div class="_jcq">뉘앙스</div>
                <div class="_j4v">화자와 청자에게서 모두 멀리 있는 대상을 가리킨다.</div>
              </section>
            """;

    private static final String CONNECTION_SECTION = """
              <section class="_j4a _j4w">
                <div class="_jcq">접속</div>
                <div class="_j4v">あの + 명사</div>
              </section>
            """;

    private static final String THIRD_CONFUSABLE_ITEM =
            "<li class=\"_j1f\"><span class=\"_j1e\">～の</span><span>소유·소속을 나타낸다.</span></li>\n";

    private static final String CONFUSABLE_SECTION = """
              <section class="_j4a _j4w">
                <div class="_jcq">헷갈리는 문형</div>
                <ul class="_j1d">
                  <li class="_j1f"><span class="_j1e">どの～</span><span>여러 선택지 중 어느 것을 고르는지 묻는다.</span></li>
                  <li class="_j1f"><span class="_j1e">どんな～</span><span>대상의 종류나 성격을 묻는다.</span></li>
                  %s</ul>
              </section>
            """.formatted(THIRD_CONFUSABLE_ITEM);

    private static String backHtml(String nuanceSection, String connectionSection, String confusableSection) {
        return """
                <section class="_j4r">
                  <div class="_j4u">あのかばんは田中さんのです。<mark class="_j4y"><ruby>田中<rt>たなか</rt></ruby></mark></div>
                  <div class="_j4z">저 가방은 다나카 씨의 것입니다.</div>
                  <div class="_j51 _j4x">저~</div>
                  %s%s%s</section>
                """.formatted(nuanceSection, connectionSection, confusableSection);
    }

    private static final String BACK_HTML = backHtml(NUANCE_SECTION, CONNECTION_SECTION, CONFUSABLE_SECTION);

    private static Map<String, String> grammar(Map<String, String> overrides) {
        Map<String, String> fields = new HashMap<>();
        fields.put("Level", "N5");
        fields.put("Kind", "⁣");
        fields.put("UnitID", "basic-gr-n5-1931ddc74a20");
        fields.put("FrontHTML", FRONT_HTML);
        fields.put("BackHTML", BACK_HTML);
        fields.put("IsBasic", "1");
        fields.put("IsGrammarForm", "");
        fields.put("IsPassageBlank", "");
        fields.put("IsSentenceArrangement", "");
        fields.putAll(overrides);
        return fields;
    }

    // ===== A. full happy path =====

    @Test
    void regularGrammarProducesFullyPopulatedValidResult() {
        var result = parser.parse("private-jlpt-max", 1L, "GRAMMAR", grammar(Map.of()));

        assertThat(result.sourceRef()).isEqualTo("private-jlpt-max");
        assertThat(result.sourceNoteId()).isEqualTo(1L);
        assertThat(result.unitId()).isEqualTo("basic-gr-n5-1931ddc74a20");
        assertThat(result.pattern()).isEqualTo("あの");

        assertThat(result.frontExample()).isNotNull();
        assertThat(result.frontExample().japaneseText()).isEqualTo("あのかばんは田中さんのです。");
        assertThat(result.frontExample().reading()).isNull(); // Ticket 3B-1: FrontHTML div._j4u never has <ruby>
        assertThat(result.frontExample().translation()).isEqualTo("저 가방은 다나카 씨의 것입니다.");

        assertThat(result.meaningGloss()).isEqualTo("저~");
        assertThat(result.nuance()).isEqualTo("화자와 청자에게서 모두 멀리 있는 대상을 가리킨다.");
        assertThat(result.connection()).isEqualTo("あの + 명사");

        assertThat(result.confusablePatterns()).hasSize(3);
        assertThat(result.confusablePatterns().get(0).displayOrder()).isEqualTo(1);
        assertThat(result.confusablePatterns().get(0).pattern()).isEqualTo("どの～");
        assertThat(result.confusablePatterns().get(0).explanation()).isEqualTo("여러 선택지 중 어느 것을 고르는지 묻는다.");
        assertThat(result.confusablePatterns().get(2).pattern()).isEqualTo("～の");

        assertThat(result.level()).isEqualTo(new NormalizedJlptLevel("N5", "N5", "Level"));
        assertThat(result.rawKind()).isEqualTo("⁣");
        assertThat(result.unknownFields()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.hasNoFatalIssues()).isTrue();
    }

    // ===== B. Kind U+2063 placeholder =====

    @Test
    void kindPlaceholderDoesNotAffectConnectionWhichComesFromBackHtml() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of()));
        assertThat(result.connection()).isEqualTo("あの + 명사");
        assertThat(result.hasIssue(GrammarNormalizationIssue.UNEXPECTED_KIND_VALUE)).isFalse();
        assertThat(result.rawKind()).isEqualTo("⁣");
    }

    // ===== C. unexpected meaningful Kind =====

    @Test
    void unexpectedMeaningfulKindIsFlaggedButNeverOverridesConnection() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("Kind", "동사 て형 + もいい")));
        assertThat(result.connection()).isEqualTo("あの + 명사");
        assertThat(result.hasIssue(GrammarNormalizationIssue.UNEXPECTED_KIND_VALUE)).isTrue();
        assertThat(result.rawKind()).isEqualTo("동사 て형 + もいい");
    }

    // ===== D. missing div._j4x (meaning gloss) =====

    @Test
    void missingMeaningGlossIsFatal() {
        String backWithoutGloss = BACK_HTML.replace("<div class=\"_j51 _j4x\">저~</div>", "");
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("BackHTML", backWithoutGloss)));
        assertThat(result.meaningGloss()).isNull();
        assertThat(result.hasIssue(GrammarNormalizationIssue.MISSING_MEANING_GLOSS)).isTrue();
        assertThat(result.hasNoFatalIssues()).isFalse();
    }

    // ===== E. missing div._j4z (front translation) =====

    @Test
    void missingFrontTranslationIsFatalButFrontExampleJapaneseStillPresent() {
        String backWithoutTranslation = BACK_HTML.replace(
                "<div class=\"_j4z\">저 가방은 다나카 씨의 것입니다.</div>", "");
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("BackHTML", backWithoutTranslation)));
        assertThat(result.hasIssue(GrammarNormalizationIssue.MISSING_FRONT_TRANSLATION)).isTrue();
        assertThat(result.hasNoFatalIssues()).isFalse();
        assertThat(result.frontExample()).isNotNull();
        assertThat(result.frontExample().japaneseText()).isEqualTo("あのかばんは田中さんのです。");
        assertThat(result.frontExample().translation()).isNull();
    }

    // ===== F. section._j4a count != 3 =====

    @Test
    void sectionCountOtherThanThreeIsFlagged() {
        String backWithTwoSections = backHtml(NUANCE_SECTION, CONNECTION_SECTION, "");
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("BackHTML", backWithTwoSections)));
        assertThat(result.hasIssue(GrammarNormalizationIssue.SECTION_COUNT_UNEXPECTED)).isTrue();
        // The confusable-patterns card is entirely gone, so its own count check also fires.
        assertThat(result.hasIssue(GrammarNormalizationIssue.CONFUSABLE_PATTERN_COUNT_UNEXPECTED)).isTrue();
        assertThat(result.confusablePatterns()).isEmpty();
        // Nuance/Connection cards are untouched and still resolve correctly.
        assertThat(result.nuance()).isNotNull();
        assertThat(result.connection()).isNotNull();
    }

    // ===== G. wrong section label =====

    @Test
    void unexpectedSectionLabelIsFlaggedAndTheCardIsTreatedAsMissingNotMisidentified() {
        String backWithRenamedConnectionLabel = BACK_HTML.replace(
                "<div class=\"_jcq\">접속</div>", "<div class=\"_jcq\">연결</div>");
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("BackHTML", backWithRenamedConnectionLabel)));
        assertThat(result.hasIssue(GrammarNormalizationIssue.SECTION_LABEL_MISMATCH)).isTrue();
        assertThat(result.hasIssue(GrammarNormalizationIssue.MISSING_CONNECTION)).isTrue();
        assertThat(result.connection()).isNull();
        // The other two cards, correctly labeled, are unaffected.
        assertThat(result.nuance()).isNotNull();
        assertThat(result.confusablePatterns()).hasSize(3);
    }

    // ===== H. missing Nuance content =====

    @Test
    void missingNuanceCardValueIsFatal() {
        String backWithBlankNuance = BACK_HTML.replace(
                "<div class=\"_j4v\">화자와 청자에게서 모두 멀리 있는 대상을 가리킨다.</div>", "<div class=\"_j4v\"></div>");
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("BackHTML", backWithBlankNuance)));
        assertThat(result.nuance()).isNull();
        assertThat(result.hasIssue(GrammarNormalizationIssue.MISSING_NUANCE)).isTrue();
        assertThat(result.hasNoFatalIssues()).isFalse();
    }

    // ===== I. missing Connection content =====

    @Test
    void missingConnectionCardValueIsFatal() {
        String backWithBlankConnection = BACK_HTML.replace(
                "<div class=\"_j4v\">あの + 명사</div>", "<div class=\"_j4v\"></div>");
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("BackHTML", backWithBlankConnection)));
        assertThat(result.connection()).isNull();
        assertThat(result.hasIssue(GrammarNormalizationIssue.MISSING_CONNECTION)).isTrue();
        assertThat(result.hasNoFatalIssues()).isFalse();
    }

    // ===== J. malformed confusable item =====

    @Test
    void malformedConfusablePatternEntryIsDroppedWithWarning() {
        String confusableWithMalformedThirdItem = CONFUSABLE_SECTION.replace(
                THIRD_CONFUSABLE_ITEM, "<li class=\"_j1f\"><span>소유·소속을 나타낸다.</span></li>\n");
        String backWithMalformedItem = backHtml(NUANCE_SECTION, CONNECTION_SECTION, confusableWithMalformedThirdItem);
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("BackHTML", backWithMalformedItem)));
        assertThat(result.confusablePatterns()).hasSize(2);
        assertThat(result.hasIssue(GrammarNormalizationIssue.MALFORMED_CONFUSABLE_PATTERN_ENTRY)).isTrue();
        assertThat(result.hasIssue(GrammarNormalizationIssue.CONFUSABLE_PATTERN_COUNT_UNEXPECTED)).isTrue();
    }

    // ===== K. confusable count != 3 =====

    @Test
    void confusablePatternCountOtherThanThreeIsFlagged() {
        String confusableWithTwoItems = CONFUSABLE_SECTION.replace(THIRD_CONFUSABLE_ITEM, "");
        String backWithTwoItems = backHtml(NUANCE_SECTION, CONNECTION_SECTION, confusableWithTwoItems);
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("BackHTML", backWithTwoItems)));
        assertThat(result.confusablePatterns()).hasSize(2);
        assertThat(result.hasIssue(GrammarNormalizationIssue.CONFUSABLE_PATTERN_COUNT_UNEXPECTED)).isTrue();
        assertThat(result.hasIssue(GrammarNormalizationIssue.MALFORMED_CONFUSABLE_PATTERN_ENTRY)).isFalse();
    }

    // ===== confusable-pattern extraction sanity (pattern/explanation must not bleed into each other) =====

    @Test
    void confusablePatternTextDoesNotLeakIntoExplanationOrViceVersa() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of()));
        for (var entry : result.confusablePatterns()) {
            assertThat(entry.explanation()).doesNotContain(entry.pattern());
            assertThat(entry.pattern()).doesNotContain(entry.explanation());
        }
        assertThat(result.confusablePatterns()).extracting(NormalizedConfusablePattern::pattern)
                .containsExactly("どの～", "どんな～", "～の");
        assertThat(result.confusablePatterns()).extracting(NormalizedConfusablePattern::explanation)
                .containsExactly(
                        "여러 선택지 중 어느 것을 고르는지 묻는다.",
                        "대상의 종류나 성격을 묻는다.",
                        "소유·소속을 나타낸다.");
    }

    @Test
    void patternSpanWithNestedChildSpanIsNotMisattributedAsExplanation() {
        // The pattern span itself has a nested <span> - if extraction walked ALL descendant spans
        // instead of only li's direct children, that inner span could be wrongly picked up as the
        // "explanation" span (the first non-_j1e span encountered in document order).
        String itemWithNestedSpan =
                "<li class=\"_j1f\"><span class=\"_j1e\"><span>どの</span>～</span><span>설명 텍스트</span></li>\n";
        String confusableWithNestedSpan = CONFUSABLE_SECTION.replace(THIRD_CONFUSABLE_ITEM, itemWithNestedSpan);
        var result = parser.parse("s", 1L, "GRAMMAR",
                grammar(Map.of("BackHTML", backHtml(NUANCE_SECTION, CONNECTION_SECTION, confusableWithNestedSpan))));
        var thirdEntry = result.confusablePatterns().get(2);
        assertThat(thirdEntry.pattern()).isEqualTo("どの～");
        assertThat(thirdEntry.explanation()).isEqualTo("설명 텍스트");
    }

    @Test
    void extraDirectSpanBeyondPatternAndExplanationPreservesKnownValuesButRaisesReviewWarning() {
        // A third direct <span> is unexplained source content - silently ignoring it would risk
        // dropping real semantic information, so this must be flagged, not a silent no-op.
        String itemWithThreeSpans =
                "<li class=\"_j1f\"><span class=\"_j1e\">～の</span><span>소유·소속을 나타낸다.</span><span>extra</span></li>\n";
        String confusableWithExtraSpan = CONFUSABLE_SECTION.replace(THIRD_CONFUSABLE_ITEM, itemWithThreeSpans);
        var result = parser.parse("s", 1L, "GRAMMAR",
                grammar(Map.of("BackHTML", backHtml(NUANCE_SECTION, CONNECTION_SECTION, confusableWithExtraSpan))));
        var thirdEntry = result.confusablePatterns().get(2);
        assertThat(thirdEntry.pattern()).isEqualTo("～の");
        assertThat(thirdEntry.explanation()).isEqualTo("소유·소속을 나타낸다.");
        assertThat(thirdEntry.explanation()).doesNotContain("extra");
        assertThat(result.hasIssue(GrammarNormalizationIssue.MALFORMED_CONFUSABLE_PATTERN_ENTRY)).isTrue();
    }

    @Test
    void confusablePatternWithEmptyExplanationIsKeptWithNullExplanationNotDropped() {
        String itemWithBlankExplanation =
                "<li class=\"_j1f\"><span class=\"_j1e\">～の</span><span></span></li>\n";
        String confusableWithBlankExplanation = CONFUSABLE_SECTION.replace(THIRD_CONFUSABLE_ITEM, itemWithBlankExplanation);
        var result = parser.parse("s", 1L, "GRAMMAR",
                grammar(Map.of("BackHTML", backHtml(NUANCE_SECTION, CONNECTION_SECTION, confusableWithBlankExplanation))));
        var thirdEntry = result.confusablePatterns().get(2);
        assertThat(thirdEntry.pattern()).isEqualTo("～の");
        assertThat(thirdEntry.explanation()).isNull();
        assertThat(result.hasIssue(GrammarNormalizationIssue.MALFORMED_CONFUSABLE_PATTERN_ENTRY)).isFalse();
    }

    // ===== L. unsupported subtype =====

    @Test
    void comprehensiveCategoryIsUnsupportedAndProducesNoGrammarContent() {
        var result = parser.parse("s", 1L, "COMPREHENSIVE", grammar(Map.of("IsBasic", "", "IsGrammarForm", "1")));
        assertThat(result.hasIssue(GrammarNormalizationIssue.UNSUPPORTED_SUBTYPE)).isTrue();
        assertThat(result.hasNoFatalIssues()).isFalse();
        assertThat(result.unitId()).isNull();
        assertThat(result.pattern()).isNull();
        assertThat(result.frontExample()).isNull();
        assertThat(result.meaningGloss()).isNull();
        assertThat(result.nuance()).isNull();
        assertThat(result.connection()).isNull();
        assertThat(result.confusablePatterns()).isEmpty();
        assertThat(result.rawKind()).isNull();
    }

    @Test
    void grammarCategoryWithoutIsBasicActiveIsUnsupported() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("IsBasic", "")));
        assertThat(result.hasIssue(GrammarNormalizationIssue.UNSUPPORTED_SUBTYPE)).isTrue();
    }

    @Test
    void isGrammarFormSubtypeAloneIsUnsupported() {
        var result = parser.parse("s", 1L, "COMPREHENSIVE", grammar(Map.of("IsBasic", "", "IsGrammarForm", "1")));
        assertThat(result.hasIssue(GrammarNormalizationIssue.UNSUPPORTED_SUBTYPE)).isTrue();
    }

    @Test
    void isPassageBlankSubtypeAloneIsUnsupported() {
        var result = parser.parse("s", 1L, "COMPREHENSIVE", grammar(Map.of("IsBasic", "", "IsPassageBlank", "1")));
        assertThat(result.hasIssue(GrammarNormalizationIssue.UNSUPPORTED_SUBTYPE)).isTrue();
    }

    @Test
    void isSentenceArrangementSubtypeAloneIsUnsupported() {
        var result = parser.parse("s", 1L, "COMPREHENSIVE", grammar(Map.of("IsBasic", "", "IsSentenceArrangement", "1")));
        assertThat(result.hasIssue(GrammarNormalizationIssue.UNSUPPORTED_SUBTYPE)).isTrue();
    }

    // ===== M. conflicting subtype flags =====

    @Test
    void conflictingSubtypeFlagsAreFatalButContentIsStillParsedForReview() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("IsGrammarForm", "1")));
        assertThat(result.hasIssue(GrammarNormalizationIssue.CONFLICTING_SUBTYPE_FLAGS)).isTrue();
        assertThat(result.hasIssue(GrammarNormalizationIssue.UNSUPPORTED_SUBTYPE)).isFalse();
        // Subtype identity itself is ambiguous when flags conflict, so the candidate is not
        // content-complete - but extraction still runs so a reviewer can see what it produced.
        assertThat(result.hasNoFatalIssues()).isFalse();
        assertThat(result.pattern()).isEqualTo("あの");
    }

    // ===== N. missing UnitID =====

    @Test
    void missingUnitIdIsFatal() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("UnitID", "")));
        assertThat(result.unitId()).isNull();
        assertThat(result.hasIssue(GrammarNormalizationIssue.MISSING_UNIT_ID)).isTrue();
        assertThat(result.hasNoFatalIssues()).isFalse();
    }

    // ===== O. invalid/missing JLPT level =====

    @Test
    void missingLevelIsFatal() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("Level", "")));
        assertThat(result.level()).isEqualTo(new NormalizedJlptLevel(null, null, null));
        assertThat(result.hasIssue(GrammarNormalizationIssue.MISSING_JLPT_LEVEL)).isTrue();
        assertThat(result.hasNoFatalIssues()).isFalse();
    }

    @Test
    void invalidLevelValueIsFatalNotGuessed() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("Level", "beginner")));
        assertThat(result.level().code()).isNull();
        assertThat(result.level().rawValue()).isEqualTo("beginner");
        assertThat(result.hasIssue(GrammarNormalizationIssue.INVALID_JLPT_LEVEL)).isTrue();
        assertThat(result.hasNoFatalIssues()).isFalse();
    }

    // ===== P. unknown field =====

    @Test
    void unrecognizedFieldNameIsFlaggedInformationalButDoesNotBlockPromotion() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("SomeFutureField", "new content")));
        assertThat(result.hasIssue(GrammarNormalizationIssue.UNKNOWN_EXTRA_FIELD)).isTrue();
        assertThat(result.unknownFields()).containsEntry("SomeFutureField", "new content");
        assertThat(result.hasNoFatalIssues()).isTrue();
    }

    // ===== Q. immutability =====

    @Test
    void resultCollectionsAreImmutable() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of("SomeFutureField", "x")));
        assertThatThrownBy(() -> result.confusablePatterns().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.warnings().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.unknownFields().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void doesNotMutateTheInputMap() {
        Map<String, String> fields = grammar(Map.of());
        Map<String, String> copy = new HashMap<>(fields);

        parser.parse("s", 1L, "GRAMMAR", fields);

        assertThat(fields).isEqualTo(copy);
    }

    // ===== R. unexpected audio residue =====

    @Test
    void audioResidueInBackHtmlIsFlagged() {
        var result = parser.parse("s", 1L, "GRAMMAR",
                grammar(Map.of("BackHTML", BACK_HTML + "<audio src=\"leftover.mp3\"></audio>")));
        assertThat(result.hasIssue(GrammarNormalizationIssue.AUDIO_RESIDUE_UNEXPECTED)).isTrue();
    }

    @Test
    void bracketSoundResidueInFrontHtmlIsFlagged() {
        var result = parser.parse("s", 1L, "GRAMMAR",
                grammar(Map.of("FrontHTML", FRONT_HTML + "[sound:leftover.mp3]")));
        assertThat(result.hasIssue(GrammarNormalizationIssue.AUDIO_RESIDUE_UNEXPECTED)).isTrue();
    }

    @Test
    void cleanHtmlWithoutAudioMarkupIsNotFlagged() {
        var result = parser.parse("s", 1L, "GRAMMAR", grammar(Map.of()));
        assertThat(result.hasIssue(GrammarNormalizationIssue.AUDIO_RESIDUE_UNEXPECTED)).isFalse();
    }

    // ===== S. front example extraction edge case =====

    @Test
    void missingFrontExampleContainerIsFatalEvenThoughPatternAloneStillSucceeds() {
        var result = parser.parse("s", 1L, "GRAMMAR",
                grammar(Map.of("FrontHTML", "<section><mark class=\"_j4y\">あの</mark></section>")));
        assertThat(result.pattern()).isEqualTo("あの");
        assertThat(result.frontExample()).isNull();
        assertThat(result.hasIssue(GrammarNormalizationIssue.MISSING_FRONT_EXAMPLE)).isTrue();
        // frontExample is a first-class semantic field, not incidental metadata - a candidate
        // missing it is not content-complete even though the separately-extracted pattern succeeded.
        assertThat(result.hasNoFatalIssues()).isFalse();
    }

    // ===== identity / provenance =====

    @Test
    void unitIdIsIdentityCandidateSourceNoteIdIsProvenanceOnly() {
        var first = parser.parse("private-jlpt-max", 111L, "GRAMMAR", grammar(Map.of()));
        var second = parser.parse("private-jlpt-max", 222L, "GRAMMAR", grammar(Map.of()));

        assertThat(first.sourceNoteId()).isNotEqualTo(second.sourceNoteId());
        assertThat(first.unitId()).isEqualTo(second.unitId());
    }

    // ===== determinism =====

    @Test
    void sameInputProducesEqualOutput() {
        Map<String, String> fields = grammar(Map.of());
        var first = parser.parse("private-jlpt-max", 42L, "GRAMMAR", fields);
        var second = parser.parse("private-jlpt-max", 42L, "GRAMMAR", fields);

        assertThat(first).isEqualTo(second);
    }

    // ===== pure-parser guardrails =====

    @Test
    void rejectsNullOrBlankSourceRefAndNullFieldMapWithoutTouchingAnything() {
        assertThatThrownBy(() -> parser.parse(null, 1L, "GRAMMAR", grammar(Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("   ", 1L, "GRAMMAR", grammar(Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("s", 1L, "GRAMMAR", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveSourceNoteId() {
        assertThatThrownBy(() -> parser.parse("s", 0L, "GRAMMAR", grammar(Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("s", -1L, "GRAMMAR", grammar(Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
