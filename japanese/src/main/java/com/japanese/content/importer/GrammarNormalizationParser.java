package com.japanese.content.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/**
 * Turns one raw private-staging Grammar-model note (field name -&gt; raw field value, as stored in
 * {@code private_apkg_notes.field_names}/{@code field_values}, plus the {@code category} column
 * PrivateApkgExtractor already computed) into a {@link GrammarNormalizationResult}.
 *
 * <p>Scope (Ticket 3B-1): ONLY {@code category=GRAMMAR} with {@code IsBasic} active - the regular,
 * non-COMPREHENSIVE 1,078 notes. Ticket 3A.1's actual APKG profiling proved
 * IsBasic/IsGrammarForm/IsPassageBlank/IsSentenceArrangement are a mutually-exclusive 4-way subtype
 * selector, and that the other three subtypes (all COMPREHENSIVE) are a structurally different
 * Practice/Question shape - not a Grammar-knowledge shape at all. This parser never attempts to
 * normalize those subtypes as Grammar; see {@link GrammarNormalizationIssue#UNSUPPORTED_SUBTYPE}.
 *
 * <p><b>Corrected BackHTML semantic model (Ticket 3B-1, full 1,078-note re-review, zero
 * exceptions):</b> a section._j4a-as-"examples" / div._j4z-as-"explanation" interpretation
 * (inherited from {@link GrammarHtmlParser}, which every Ticket 1-3A.1 measurement was built on)
 * is disproven by actual data. Every real note's BackHTML is:
 * <pre>
 * section._j4r
 *   div._j4u   (a copy of the front sentence - not used; the parser reads FrontHTML's own div._j4u instead)
 *   div._j4z   the Korean TRANSLATION of the FrontHTML example sentence
 *   div._j4x   the pattern's short Korean MEANING GLOSS (e.g. "저~")
 *   section._j4a (label "뉘앙스"/Nuance)   div._j4v = nuance text
 *   section._j4a (label "접속"/Connection) div._j4v = the REAL grammatical connection form
 *   section._j4a (label "헷갈리는 문형"/Confusable patterns)  ul._j1d &gt; li._j1f x3
 * </pre>
 * Reference cards are identified by their {@code div._jcq} label text, never by position alone -
 * see {@link GrammarNormalizationIssue#SECTION_LABEL_MISMATCH}. See
 * {@link GrammarNormalizationResult} for the full field-by-field rationale.
 *
 * <p>Pure by design: no repository, no {@code EntityManager}, no JDBC, no transaction, no
 * production table access. {@link #parse} is a deterministic function of its arguments and never
 * mutates the input map or throws for note-shaped input, however incomplete or out of scope -
 * unsupported-subtype/malformed notes come back as a result with warnings and
 * {@code hasNoFatalIssues=false}, never as an exception that would abort a whole batch. This parser
 * also never truncates any field to a production column length the way {@link GrammarHtmlParser}
 * does for its legacy import path. Persisting the result into a private candidate store, and
 * deciding how these fields map onto the production {@code Grammar} entity, are separate, later
 * concerns - this parser does not touch {@code Grammar}, {@code GrammarEnrichment},
 * {@code GrammarRelation}, or {@code GrammarComparison}.
 */
@Component
public class GrammarNormalizationParser {

    private static final String SUPPORTED_CATEGORY = "GRAMMAR";
    private static final String LABEL_NUANCE = "뉘앙스";
    private static final String LABEL_CONNECTION = "접속";
    private static final String LABEL_CONFUSABLE = "헷갈리는 문형";
    private static final int EXPECTED_SECTION_COUNT = 3;
    private static final int EXPECTED_CONFUSABLE_PATTERN_COUNT = 3;
    private static final Pattern JLPT_LEVEL = Pattern.compile("(?i)(?:jlpt[-_ ]*)?(n[1-5])");
    private static final Pattern AUDIO_RESIDUE = Pattern.compile("(?i)\\[sound:|<audio\\b");

    /** The full, actual Grammar-model field schema confirmed by Ticket 3A.1 real-APKG profiling. */
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "Level", "Kind", "UnitID", "FrontHTML", "BackHTML",
            "IsBasic", "IsGrammarForm", "IsPassageBlank", "IsSentenceArrangement");

    public GrammarNormalizationResult parse(String sourceRef, long sourceNoteId, String category,
            Map<String, String> fieldValuesByName) {
        if (sourceRef == null || sourceRef.isBlank()) throw new IllegalArgumentException("sourceRef is required");
        if (sourceNoteId <= 0) throw new IllegalArgumentException("sourceNoteId must be positive");
        if (fieldValuesByName == null) throw new IllegalArgumentException("fieldValuesByName is required");

        List<GrammarNormalizationWarning> warnings = new ArrayList<>();

        boolean isBasicActive = !blank(fieldValuesByName.get("IsBasic"));
        boolean isGrammarFormActive = !blank(fieldValuesByName.get("IsGrammarForm"));
        boolean isPassageBlankActive = !blank(fieldValuesByName.get("IsPassageBlank"));
        boolean isSentenceArrangementActive = !blank(fieldValuesByName.get("IsSentenceArrangement"));
        boolean supported = SUPPORTED_CATEGORY.equals(category) && isBasicActive;

        if (!supported) {
            warnings.add(warning(GrammarNormalizationIssue.UNSUPPORTED_SUBTYPE,
                    "category=" + category + " IsBasic=" + isBasicActive + " IsGrammarForm=" + isGrammarFormActive
                            + " IsPassageBlank=" + isPassageBlankActive + " IsSentenceArrangement=" + isSentenceArrangementActive
                            + " is not the regular GRAMMAR/IsBasic subtype this parser covers"));
            return new GrammarNormalizationResult(
                    sourceRef, sourceNoteId, null, null, null, null, null, null, List.of(),
                    new NormalizedJlptLevel(null, null, null), null, Map.of(), warnings, false);
        }

        if (isGrammarFormActive || isPassageBlankActive || isSentenceArrangementActive) {
            warnings.add(warning(GrammarNormalizationIssue.CONFLICTING_SUBTYPE_FLAGS,
                    "IsBasic is active but another subtype flag is also active (IsGrammarForm=" + isGrammarFormActive
                            + " IsPassageBlank=" + isPassageBlankActive
                            + " IsSentenceArrangement=" + isSentenceArrangementActive + ")"));
        }

        String unitId = blankToNull(fieldValuesByName.get("UnitID"));
        if (unitId == null) {
            warnings.add(warning(GrammarNormalizationIssue.MISSING_UNIT_ID, "UnitID is missing or blank"));
        }

        String frontHtml = fieldValuesByName.get("FrontHTML");
        String backHtml = fieldValuesByName.get("BackHTML");
        if (containsAudioResidue(frontHtml) || containsAudioResidue(backHtml)) {
            warnings.add(warning(GrammarNormalizationIssue.AUDIO_RESIDUE_UNEXPECTED,
                    "FrontHTML/BackHTML still contains a [sound:...]/<audio> signature"));
        }

        String pattern = parsePattern(frontHtml, warnings);

        Element back = Jsoup.parseBodyFragment(valueOrEmpty(backHtml)).body();
        String frontTranslation = parseFrontTranslation(back, warnings);
        NormalizedGrammarExample frontExample = parseFrontExample(frontHtml, frontTranslation, warnings);
        String meaningGloss = parseMeaningGloss(back, warnings);

        Elements sections = back.select("section._j4a");
        Map<String, Element> sectionsByLabel = classifySections(sections, warnings);
        String nuance = parseCardValue(sectionsByLabel.get(LABEL_NUANCE), GrammarNormalizationIssue.MISSING_NUANCE,
                "Nuance(\"" + LABEL_NUANCE + "\")", warnings);
        String connection = parseCardValue(sectionsByLabel.get(LABEL_CONNECTION), GrammarNormalizationIssue.MISSING_CONNECTION,
                "Connection(\"" + LABEL_CONNECTION + "\")", warnings);
        List<NormalizedConfusablePattern> confusablePatterns =
                parseConfusablePatterns(sectionsByLabel.get(LABEL_CONFUSABLE), warnings);

        NormalizedJlptLevel level = parseLevel(fieldValuesByName.get("Level"), warnings);
        String rawKind = parseKind(fieldValuesByName.get("Kind"), warnings);
        Map<String, String> unknownFields = collectUnknownFields(fieldValuesByName, warnings);

        boolean hasNoFatalIssues = warnings.stream()
                .noneMatch(warning -> warning.severity() == GrammarNormalizationSeverity.FATAL);

        return new GrammarNormalizationResult(
                sourceRef, sourceNoteId, unitId, pattern, frontExample, meaningGloss, nuance, connection,
                confusablePatterns, level, rawKind, unknownFields, warnings, hasNoFatalIssues);
    }

    private String parsePattern(String frontHtml, List<GrammarNormalizationWarning> warnings) {
        Element front = Jsoup.parseBodyFragment(valueOrEmpty(frontHtml)).body();
        Element mark = front.selectFirst("mark");
        Element divLangJa = front.selectFirst("div[lang=ja]");
        Element selected = mark != null ? mark : divLangJa;
        boolean usedFallback = selected == null;
        String pattern = blankToNull(usedFallback ? front.text() : selected.text());
        if (pattern == null) {
            warnings.add(warning(GrammarNormalizationIssue.MISSING_PATTERN,
                    "Neither <mark> nor div[lang=ja] was found in FrontHTML, and the fallback front text was also blank"));
        } else if (usedFallback) {
            warnings.add(warning(GrammarNormalizationIssue.PATTERN_SELECTOR_FALLBACK,
                    "Neither <mark> nor div[lang=ja] was found in FrontHTML; used the whole front text instead"));
        }
        return pattern;
    }

    /**
     * FrontHTML's {@code div._j4u} contains {@code <mark>} as a descendant, so its full text is the
     * complete example sentence (context plus the pattern filled in) - not just the short
     * {@code pattern} fragment. Ticket 3B-1 confirmed 0/1,078 real notes have any {@code <ruby>}
     * inside this container, so {@code reading} is always null here.
     */
    private NormalizedGrammarExample parseFrontExample(String frontHtml, String frontTranslation,
            List<GrammarNormalizationWarning> warnings) {
        Element front = Jsoup.parseBodyFragment(valueOrEmpty(frontHtml)).body();
        Element sentenceContainer = front.selectFirst("div._j4u");
        String japaneseText = sentenceContainer == null ? null : textWithoutRuby(sentenceContainer);
        if (japaneseText == null || japaneseText.isBlank()) {
            warnings.add(warning(GrammarNormalizationIssue.MISSING_FRONT_EXAMPLE,
                    "FrontHTML div._j4u produced no usable example-sentence text"));
            return null;
        }
        String reading = rubyReading(sentenceContainer);
        return new NormalizedGrammarExample(1, japaneseText, reading, frontTranslation);
    }

    private String parseFrontTranslation(Element back, List<GrammarNormalizationWarning> warnings) {
        Element translationDiv = back.selectFirst("div._j4z");
        String translation = translationDiv == null ? null : blankToNull(translationDiv.text().trim());
        if (translation == null) {
            warnings.add(warning(GrammarNormalizationIssue.MISSING_FRONT_TRANSLATION,
                    "BackHTML div._j4z (translation of the FrontHTML example sentence) is missing or blank"));
        }
        return translation;
    }

    private String parseMeaningGloss(Element back, List<GrammarNormalizationWarning> warnings) {
        Element glossDiv = back.selectFirst("div._j4x");
        String gloss = glossDiv == null ? null : blankToNull(glossDiv.text().trim());
        if (gloss == null) {
            warnings.add(warning(GrammarNormalizationIssue.MISSING_MEANING_GLOSS,
                    "BackHTML div._j4x (meaning gloss) is missing or blank"));
        }
        return gloss;
    }

    /**
     * Identifies each {@code section._j4a} reference card by its {@code div._jcq} label text
     * (never by position alone), so a future label/order drift is visible as a warning instead of
     * silently misinterpreted as whichever card happens to occupy that index.
     */
    private Map<String, Element> classifySections(Elements sections, List<GrammarNormalizationWarning> warnings) {
        if (sections.size() != EXPECTED_SECTION_COUNT) {
            warnings.add(warning(GrammarNormalizationIssue.SECTION_COUNT_UNEXPECTED,
                    "Expected exactly " + EXPECTED_SECTION_COUNT + " section._j4a reference cards, found " + sections.size()));
        }
        Map<String, Element> byLabel = new LinkedHashMap<>();
        for (Element section : sections) {
            String label = labelOf(section);
            byLabel.putIfAbsent(label, section);
        }
        List<String> missingLabels = new ArrayList<>();
        for (String expected : List.of(LABEL_NUANCE, LABEL_CONNECTION, LABEL_CONFUSABLE)) {
            if (!byLabel.containsKey(expected)) missingLabels.add(expected);
        }
        if (!missingLabels.isEmpty()) {
            warnings.add(warning(GrammarNormalizationIssue.SECTION_LABEL_MISMATCH,
                    "Expected reference-card labels " + missingLabels + " not found among actual labels " + byLabel.keySet()));
        }
        return byLabel;
    }

    private String labelOf(Element section) {
        Element label = section.selectFirst("div._jcq");
        return label == null ? "" : label.text().trim();
    }

    private String parseCardValue(Element section, GrammarNormalizationIssue missingIssue, String cardName,
            List<GrammarNormalizationWarning> warnings) {
        Element valueDiv = section == null ? null : section.selectFirst("div._j4v");
        String value = valueDiv == null ? null : blankToNull(valueDiv.text().trim());
        if (value == null) {
            warnings.add(warning(missingIssue, cardName + " reference card's div._j4v value is missing, blank, or the card itself was not found"));
        }
        return value;
    }

    private List<NormalizedConfusablePattern> parseConfusablePatterns(Element section, List<GrammarNormalizationWarning> warnings) {
        List<NormalizedConfusablePattern> result = new ArrayList<>();
        if (section != null) {
            int order = 1;
            for (Element li : section.select("ul._j1d > li._j1f")) {
                // Direct children only (never li.select("span"), which would also match any span
                // nested INSIDE the pattern or explanation span and could misattribute a fragment
                // of one into the other) - the real structure is always two sibling <span> children.
                Element patternSpan = null;
                Element explanationSpan = null;
                int patternSpanCount = 0;
                int directSpanCount = 0;
                for (Element child : li.children()) {
                    if (!"span".equals(child.tagName())) continue;
                    directSpanCount++;
                    // span._j1e is EXCLUSIVELY a pattern candidate - a second (or later) one must
                    // never fall through to explanationSpan, even though the "> 2 direct spans"
                    // check below would not otherwise catch a li with exactly two span._j1e
                    // children and nothing else (directSpanCount stays 2 in that case).
                    if (child.hasClass("_j1e")) {
                        patternSpanCount++;
                        if (patternSpan == null) patternSpan = child;
                    } else if (explanationSpan == null) {
                        explanationSpan = child;
                    }
                }
                String comparisonPattern = patternSpan == null ? null : blankToNull(patternSpan.text().trim());
                String comparisonExplanation = explanationSpan == null ? null : blankToNull(explanationSpan.text().trim());
                if (comparisonPattern == null) {
                    warnings.add(warning(GrammarNormalizationIssue.MALFORMED_CONFUSABLE_PATTERN_ENTRY,
                            "A confusable-pattern li._j1f entry was missing its span._j1e pattern text and was dropped"));
                    continue;
                }
                if (patternSpanCount > 1) {
                    warnings.add(warning(GrammarNormalizationIssue.MALFORMED_CONFUSABLE_PATTERN_ENTRY,
                            "A confusable-pattern li._j1f entry had " + patternSpanCount
                                    + " direct span._j1e children (expected 1); only the first was used as"
                                    + " pattern, the rest were never reinterpreted as explanation"));
                }
                // Actual v2.1.1 data always has a non-blank explanation alongside the pattern, so a
                // missing one is source drift/malformed structure - the entry is still kept (pattern
                // alone is still useful), but this must not pass through silently.
                if (comparisonExplanation == null) {
                    warnings.add(warning(GrammarNormalizationIssue.MALFORMED_CONFUSABLE_PATTERN_ENTRY,
                            "A confusable-pattern li._j1f entry was missing its explanation span text;"
                                    + " entry kept with explanation=null"));
                }
                // A third (or later) direct <span> child is unexplained source content - silently
                // ignoring it would drop real semantic information. Keep the known pattern/
                // explanation values (do not fail the whole entry) but flag it for review; never
                // merge the unexpected span's text into pattern/explanation/connection.
                if (directSpanCount > 2) {
                    warnings.add(warning(GrammarNormalizationIssue.MALFORMED_CONFUSABLE_PATTERN_ENTRY,
                            "A confusable-pattern li._j1f entry had " + directSpanCount
                                    + " direct <span> children (expected 2); the extra span(s) were ignored,"
                                    + " not merged into pattern/explanation"));
                }
                result.add(new NormalizedConfusablePattern(order++, comparisonPattern, comparisonExplanation));
            }
        }
        if (result.size() != EXPECTED_CONFUSABLE_PATTERN_COUNT) {
            warnings.add(warning(GrammarNormalizationIssue.CONFUSABLE_PATTERN_COUNT_UNEXPECTED,
                    "Expected exactly " + EXPECTED_CONFUSABLE_PATTERN_COUNT + " confusable-pattern entries, found " + result.size()));
        }
        return List.copyOf(result);
    }

    private String textWithoutRuby(Element element) {
        Element copy = element.clone();
        copy.select("rt").remove();
        return AnkiFieldTextNormalizer.text(copy.html());
    }

    private String rubyReading(Element element) {
        String reading = element.select("ruby rt").text().trim();
        return reading.isBlank() ? null : reading;
    }

    private NormalizedJlptLevel parseLevel(String rawLevel, List<GrammarNormalizationWarning> warnings) {
        String raw = blankToNull(rawLevel);
        if (raw == null) {
            warnings.add(warning(GrammarNormalizationIssue.MISSING_JLPT_LEVEL, "Level is missing or blank"));
            return new NormalizedJlptLevel(null, null, null);
        }
        Matcher matcher = JLPT_LEVEL.matcher(raw.trim());
        if (!matcher.matches()) {
            warnings.add(warning(GrammarNormalizationIssue.INVALID_JLPT_LEVEL,
                    "Could not parse a JLPT N1-N5 level out of Level=" + raw));
            return new NormalizedJlptLevel(null, raw, "Level");
        }
        return new NormalizedJlptLevel(matcher.group(1).toUpperCase(Locale.ROOT), raw, "Level");
    }

    private String parseKind(String rawKind, List<GrammarNormalizationWarning> warnings) {
        String normalized = AnkiFieldTextNormalizer.text(rawKind);
        if (normalized != null) {
            warnings.add(warning(GrammarNormalizationIssue.UNEXPECTED_KIND_VALUE,
                    "Kind normalized to \"" + normalized + "\" instead of the expected U+2063 placeholder; "
                            + "not auto-promoted to connection (the structured Connection reference card is the real source)"));
        }
        return blankToNull(rawKind);
    }

    private Map<String, String> collectUnknownFields(Map<String, String> values, List<GrammarNormalizationWarning> warnings) {
        Map<String, String> unknown = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String field = entry.getKey();
            if (KNOWN_FIELDS.contains(field)) continue;
            warnings.add(warning(GrammarNormalizationIssue.UNKNOWN_EXTRA_FIELD,
                    "Field \"" + field + "\" is not part of the known Grammar-model schema"));
            String cleaned = AnkiFieldTextNormalizer.text(entry.getValue());
            if (cleaned != null) unknown.put(field, cleaned);
        }
        return Map.copyOf(unknown);
    }

    private boolean containsAudioResidue(String html) {
        return html != null && AUDIO_RESIDUE.matcher(html).find();
    }

    private static String valueOrEmpty(String value) {
        return value == null || "⁣".equals(value) ? "" : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static GrammarNormalizationWarning warning(GrammarNormalizationIssue issue, String message) {
        return new GrammarNormalizationWarning(issue, message);
    }
}
