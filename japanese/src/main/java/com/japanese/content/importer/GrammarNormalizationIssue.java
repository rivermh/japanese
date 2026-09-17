package com.japanese.content.importer;

import static com.japanese.content.importer.GrammarNormalizationSeverity.FATAL;
import static com.japanese.content.importer.GrammarNormalizationSeverity.INFORMATIONAL;
import static com.japanese.content.importer.GrammarNormalizationSeverity.REVIEW_REQUIRED;

/**
 * Every distinct problem {@link GrammarNormalizationParser} can detect while turning a raw
 * private-staging Grammar-model note into a {@link GrammarNormalizationResult}. Severity is fixed
 * per issue so callers never have to guess whether a given code is blocking.
 *
 * <p>This enum reflects the corrected BackHTML semantic model confirmed by a full-population
 * (1,078/1,078, zero exceptions) re-review during Ticket 3B-1: {@code div._j4z} is the Korean
 * translation of the FrontHTML example sentence (not a grammar explanation), and
 * {@code section._j4a} holds exactly three fixed reference cards - Nuance / Connection / Confusable
 * patterns - never bilingual "examples". The earlier WIP issue set (MISSING_EXPLANATION,
 * EXAMPLE_COUNT_UNEXPECTED, MALFORMED_EXAMPLE_ENTRY, EXAMPLE_PARSE_FALLBACK) was built on the old,
 * disproven model and does not appear here.
 */
public enum GrammarNormalizationIssue {
    /** category is not GRAMMAR, or IsBasic is not active - this parser only covers that subtype
     * (Ticket 3A.1: COMPREHENSIVE/IsGrammarForm, IsPassageBlank, IsSentenceArrangement are a
     * structurally different Practice/Question shape and belong to a separate future parser). */
    UNSUPPORTED_SUBTYPE(FATAL),
    MISSING_UNIT_ID(FATAL),
    /** Neither {@code <mark>} nor {@code div[lang=ja]} produced any text in FrontHTML. */
    MISSING_PATTERN(FATAL),
    /** {@code div._j4x} (the short Korean meaning gloss, e.g. "저~") was missing/blank. */
    MISSING_MEANING_GLOSS(FATAL),
    /** {@code div._j4z} (the Korean translation of the FrontHTML example sentence) was missing/blank. */
    MISSING_FRONT_TRANSLATION(FATAL),
    /** The "뉘앙스"(Nuance) reference card could not be identified by label, or its {@code div._j4v}
     * value was missing/blank. */
    MISSING_NUANCE(FATAL),
    /** The "접속"(Connection) reference card could not be identified by label, or its
     * {@code div._j4v} value was missing/blank. This - not the {@code Kind} field - is the real
     * grammatical connection information. */
    MISSING_CONNECTION(FATAL),
    MISSING_JLPT_LEVEL(FATAL),
    INVALID_JLPT_LEVEL(FATAL),
    /** FrontHTML's {@code div._j4u} (the full example sentence containing {@code <mark>}) produced
     * no usable text. {@code frontExample} is a first-class semantic field of this result (Ticket
     * 3B-1 review), not incidental metadata, so a candidate missing the Japanese example sentence
     * is not content-complete even if {@code pattern} alone succeeded. Ticket 3B-1 actual
     * measurement: 0/1,078 real notes hit this. */
    MISSING_FRONT_EXAMPLE(FATAL),
    /** Neither {@code <mark>} nor {@code div[lang=ja]} was found in FrontHTML; the pattern text
     * came from the whole front text instead. Ticket 3B-1 actual measurement: 0/1,078 real notes
     * hit this - it exists for defensive/future-drift coverage, not a known real-data case. */
    PATTERN_SELECTOR_FALLBACK(REVIEW_REQUIRED),
    /** BackHTML had a number of {@code section._j4a} reference cards other than the expected 3.
     * Ticket 3B-1 actual measurement: 0/1,078 - every real note has exactly 3. */
    SECTION_COUNT_UNEXPECTED(REVIEW_REQUIRED),
    /** A {@code section._j4a} card's {@code div._jcq} label text did not match any of the three
     * known labels ("뉘앙스"/"접속"/"헷갈리는 문형"). Cards are identified by label text, never by
     * position alone, precisely so a label drift like this is visible instead of silently
     * misinterpreted as whichever card happens to occupy that index. */
    SECTION_LABEL_MISMATCH(REVIEW_REQUIRED),
    /** The "헷갈리는 문형"(Confusable patterns) card had a number of {@code li._j1f} entries other
     * than the expected 3. Ticket 3B-1 actual measurement: 0/1,078. */
    CONFUSABLE_PATTERN_COUNT_UNEXPECTED(REVIEW_REQUIRED),
    /** A {@code li._j1f} entry in the Confusable-patterns card either (a) was missing its
     * pattern/explanation span text and was dropped rather than kept as a broken entry, or (b) had
     * a third (or later) direct {@code <span>} child beyond the expected pattern+explanation pair -
     * kept with its known pattern/explanation values, but flagged rather than silently dropping the
     * extra span's content, since that could be real source information this parser does not yet
     * have a place for. */
    MALFORMED_CONFUSABLE_PATTERN_ENTRY(REVIEW_REQUIRED),
    /** Kind normalized to real text instead of the expected U+2063 placeholder (Ticket 3A.1: 100%
     * of real notes are the placeholder). Never auto-promoted to {@code connection} - the
     * structured "접속"(Connection) reference card is the real connection source; a person must
     * decide what a non-placeholder Kind value means for this source. */
    UNEXPECTED_KIND_VALUE(REVIEW_REQUIRED),
    /** IsBasic is active (so the note would otherwise be in scope) but another subtype flag
     * (IsGrammarForm/IsPassageBlank/IsSentenceArrangement) is unexpectedly ALSO active. This
     * parser's scope is GRAMMAR/IsBasic-only, so a conflicting flag makes the note's subtype
     * identity itself ambiguous - it is not merely a review note on otherwise-valid content, the
     * classification the rest of the parse depends on cannot be trusted. Content is still parsed
     * (so a reviewer can see what regular-Grammar extraction would have produced), but the result
     * is not content-complete. */
    CONFLICTING_SUBTYPE_FLAGS(FATAL),
    /** FrontHTML/BackHTML still contains a sound/audio markup signature. Ticket 1.5 hardened
     * PrivateApkgExtractor to strip these before staging, so this should never fire on real data -
     * kept as a defensive check, never as a reason to open or decode the referenced media. */
    AUDIO_RESIDUE_UNEXPECTED(REVIEW_REQUIRED),
    UNKNOWN_EXTRA_FIELD(INFORMATIONAL);

    private final GrammarNormalizationSeverity severity;

    GrammarNormalizationIssue(GrammarNormalizationSeverity severity) {
        this.severity = severity;
    }

    public GrammarNormalizationSeverity severity() {
        return severity;
    }
}
