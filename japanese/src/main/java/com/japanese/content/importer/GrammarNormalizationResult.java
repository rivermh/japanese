package com.japanese.content.importer;

import java.util.List;
import java.util.Map;

/**
 * The pure, immutable output of {@link GrammarNormalizationParser} for one private-staging
 * Grammar-model note. Deliberately independent of any production entity (ContentItem/Grammar/
 * Example/GrammarEnrichment/GrammarRelation/GrammarComparison) so it can be reviewed, diffed, or
 * discarded without ever touching production tables. This is a raw-private-staging-to-semantic-
 * candidate representation, NOT an adapter shaped to fit the current production {@code Grammar}
 * entity's (pattern/explanation/connection) constructor - see the field-by-field notes below for
 * why several fields here have no production counterpart yet. Mapping this onto production schema
 * is a separate, later promotion/integration decision.
 *
 * <p>Scope (Ticket 3B-1): this result only ever represents a {@code category=GRAMMAR},
 * {@code IsBasic}-active note. A note outside that subtype (COMPREHENSIVE / IsGrammarForm /
 * IsPassageBlank / IsSentenceArrangement) comes back with {@code
 * hasIssue(GrammarNormalizationIssue.UNSUPPORTED_SUBTYPE)} true and every content field left
 * null/empty - this parser never guesses at Practice/Question content shaped for a different
 * subtype.
 *
 * <p>{@code sourceRef}/{@code sourceNoteId} are provenance (where this came from) - they identify
 * a staging row, not a piece of grammar content. {@code unitId} is the source-native semantic
 * identity candidate. This ticket does not decide a global identity/dedup policy - it only keeps
 * the candidate distinguishable for a future ticket to use.
 *
 * <p><b>Corrected semantic model (Ticket 3B-1, full 1,078-note re-review, zero exceptions):</b> an
 * earlier WIP version of this parser reused {@link GrammarHtmlParser}'s field interpretation
 * (BackHTML {@code div._j4z} = "explanation", {@code section._j4a} = "examples"). That
 * interpretation is disproven by actual data - {@code div._j4z} is the Korean translation of the
 * FrontHTML example sentence, and every note's {@code section._j4a} holds exactly three fixed,
 * label-identified reference cards ("뉘앙스"/Nuance, "접속"/Connection, "헷갈리는 문형"/Confusable
 * patterns), never bilingual examples. This result reflects that correction; it has no
 * {@code explanation} or {@code examples} field.
 *
 * @param pattern           the grammar pattern fragment, from FrontHTML's {@code <mark>} (or
 *                          {@code div[lang=ja]} fallback) - unchanged from the disproven model,
 *                          this part was always correct.
 * @param frontExample      the FrontHTML example sentence containing {@code pattern} in context
 *                          (from {@code div._j4u}, which has {@code <mark>} nested inside it) plus
 *                          its Korean translation ({@code div._j4z}). {@code reading()} is always
 *                          null for JLPT-MAX-Deck v2.1.1 - Ticket 3B-1 confirmed 0/1,078 FrontHTML
 *                          example sentences contain any {@code <ruby>} at all, so there is no
 *                          furigana to preserve here (unlike Vocabulary examples, which do have
 *                          ruby). Null if FrontHTML's {@code div._j4u} produced no usable text (see
 *                          {@link GrammarNormalizationIssue#MISSING_FRONT_EXAMPLE}); its
 *                          {@code translation()} alone can still be null if only the translation
 *                          half was missing (see {@link GrammarNormalizationIssue#MISSING_FRONT_TRANSLATION}).
 * @param meaningGloss      the short Korean meaning gloss of the pattern (e.g. "저~", "아직
 *                          ~하지 않았다"), from BackHTML's {@code div._j4x}. Never called
 *                          "explanation" - Ticket 3B-1 found no field in this source that
 *                          corresponds to a prose grammar explanation; what production's
 *                          {@code Grammar.explanation} should be populated from (this gloss, the
 *                          translation, both, or something else) is a promotion-time decision, not
 *                          decided here.
 * @param nuance            the "뉘앙스"(Nuance) reference card's content, identified by its
 *                          {@code div._jcq} label text (never by position alone - see
 *                          {@link GrammarNormalizationIssue#SECTION_LABEL_MISMATCH}).
 * @param connection        the REAL grammatical connection form (e.g. "どんな + 명사"), from the
 *                          "접속"(Connection) reference card's {@code div._j4v} - identified by
 *                          label, never by position alone. This is a structurally different source
 *                          than {@code rawKind}: {@code Kind} is always the U+2063 placeholder in
 *                          this dataset and is never promoted into this field (see
 *                          {@link GrammarNormalizationIssue#UNEXPECTED_KIND_VALUE}).
 * @param confusablePatterns the "헷갈리는 문형"(Confusable patterns) reference card's entries,
 *                          identified by label - source-derived comparison data only. This parser
 *                          never maps these into the curated/reviewed {@code GrammarRelation}/
 *                          {@code GrammarComparison} production domains; that is a separate,
 *                          deliberate promotion-time decision, not an automatic consequence of a
 *                          shared field name.
 * @param rawKind           Kind's raw field value, preserved verbatim regardless of whether it was
 *                          the expected U+2063 placeholder or something else. Never influences
 *                          {@code connection}.
 * @param unknownFields raw values of fields this parser does not recognize as part of the known
 *                      Grammar-model schema (Level/Kind/UnitID/FrontHTML/BackHTML/IsBasic/
 *                      IsGrammarForm/IsPassageBlank/IsSentenceArrangement), keyed by their raw APKG
 *                      field name. Empty on every real Ticket 3A.1/3B-1 note; exists so schema
 *                      drift is visible instead of silently dropped.
 * @param hasNoFatalIssues true iff {@code warnings} contains no {@link GrammarNormalizationSeverity#FATAL}
 *                         entry. This is a pure content-completeness signal only - it is not a
 *                         review/publication decision and must not be treated as one.
 */
public record GrammarNormalizationResult(
        String sourceRef,
        long sourceNoteId,
        String unitId,
        String pattern,
        NormalizedGrammarExample frontExample,
        String meaningGloss,
        String nuance,
        String connection,
        List<NormalizedConfusablePattern> confusablePatterns,
        NormalizedJlptLevel level,
        String rawKind,
        Map<String, String> unknownFields,
        List<GrammarNormalizationWarning> warnings,
        boolean hasNoFatalIssues
) {
    public GrammarNormalizationResult {
        if (sourceRef == null || sourceRef.isBlank()) throw new IllegalArgumentException("sourceRef is required");
        confusablePatterns = List.copyOf(confusablePatterns);
        unknownFields = Map.copyOf(unknownFields);
        warnings = List.copyOf(warnings);
    }

    public boolean hasIssue(GrammarNormalizationIssue issue) {
        return warnings.stream().anyMatch(warning -> warning.issue() == issue);
    }
}
