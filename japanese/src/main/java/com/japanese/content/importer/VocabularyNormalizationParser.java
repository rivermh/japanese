package com.japanese.content.importer;

import com.japanese.content.search.ContentSearchNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns one raw private-staging VOCABULARY note (field name -&gt; raw field value, as stored in
 * {@code private_apkg_notes.field_names}/{@code field_values}) into a
 * {@link VocabularyNormalizationResult}.
 *
 * <p>Pure by design: no repository, no {@code EntityManager}, no JDBC, no transaction, no
 * production table access. {@link #parse} is a deterministic function of its arguments and never
 * mutates the input map or throws for note-shaped input, however incomplete - malformed/retired
 * notes come back as a result with warnings and {@code validForPromotion=false}, never as an
 * exception that would abort a whole batch. Persisting the result into a private candidate store
 * is a separate, later concern.
 */
@Component
public class VocabularyNormalizationParser {

    private static final Pattern JLPT_LEVEL = Pattern.compile("(?i)(?:jlpt[-_ ]*)?(n[1-5])");
    /** Same rule the existing importer/profiling code uses: split only on a slash with surrounding
     * whitespace, so a slash that is part of the actual text (no surrounding space) is left alone.
     * Deliberately conservative: "A / B" splits, but "A /B" (no trailing space) does not, since a
     * slash directly touching text on one side reads as part of that text, not a sense separator. */
    private static final Pattern MEANING_SPLIT = Pattern.compile("\\s+/(?:\\s+|$)");
    /** Collapses a repeated/empty-looking separator (e.g. "A / / B", a stray double-slash artifact)
     * to one canonical " / " before splitting, so it yields a clean empty-fragment drop instead of
     * leaving a stray leading "/" stuck onto the next sense. */
    private static final Pattern REPEATED_MEANING_SEPARATOR = Pattern.compile("(?:\\s*/\\s*){2,}");
    private static final Pattern PITCH_TERMINAL = Pattern.compile("data-pitch-terminal-states=\"([^\"]+)\"");
    private static final Pattern PITCH_MORA = Pattern.compile("j1=\"([^\"]+)\"");

    /** Fields this parser maps into a dedicated result field. */
    private static final Set<String> PRIMARY_FIELDS = Set.of(
            "EntryID", "Word", "Reading", "PartOfSpeech", "PitchAccent", "Meaning", "ExamplesRendered",
            "JLPT", "WordJLPT");

    /**
     * Fields confirmed (real-APKG profiling, Ticket 1) to hold only template placeholder/sentinel
     * values on every note - never real content. Intentionally excluded from
     * {@code preservedExtraFields} so they cannot be mistaken for real data later.
     */
    private static final Set<String> SENTINEL_FIELDS = Set.of(
            "MeaningV2", "ExamplesV2", "MediaIntentsV2", "CanonicalRecordHash", "WordAudio", "WordAudioFile",
            "KanaAuxiliaryWord", "KanaAuxiliaryReading", "KanaAuxiliaryContext", "KanaAuxiliaryJLPT",
            "Example1JP", "Example1Reading", "Example1KO", "Example1Sense", "Example1Audio", "Example1PitchAccent",
            "Example2JP", "Example2Reading", "Example2KO", "Example2Sense", "Example2Audio", "Example2PitchAccent",
            "Example3JP", "Example3Reading", "Example3KO", "Example3Sense", "Example3Audio", "Example3PitchAccent",
            "Example4JP", "Example4Reading", "Example4KO", "Example4Sense", "Example4Audio", "Example4PitchAccent",
            "Example5JP", "Example5Reading", "Example5KO", "Example5Sense", "Example5Audio", "Example5PitchAccent");

    /**
     * Fields with no dedicated result slot but confirmed (real-APKG profiling) to carry real
     * content on at least some notes - preserved verbatim (HTML-stripped) rather than discarded.
     */
    private static final Set<String> KNOWN_EXTRA_FIELDS = Set.of(
            "VocabularyContext", "KanjiDetails", "UsageRegister", "UsageDetails", "ConjugationDetails",
            "WordFormationDetails", "RelatedWords", "StudyPriority", "RetiredKanaAuxiliaryCard", "KoreanRecallPrompt");

    private final ExampleHtmlParser exampleHtmlParser;

    public VocabularyNormalizationParser(ExampleHtmlParser exampleHtmlParser) {
        this.exampleHtmlParser = exampleHtmlParser;
    }

    public VocabularyNormalizationResult parse(String sourceRef, long sourceNoteId, Map<String, String> fieldValuesByName) {
        if (sourceRef == null || sourceRef.isBlank()) throw new IllegalArgumentException("sourceRef is required");
        if (fieldValuesByName == null) throw new IllegalArgumentException("fieldValuesByName is required");

        List<VocabularyNormalizationWarning> warnings = new ArrayList<>();

        String entryId = blankToNull(fieldValuesByName.get("EntryID"));
        if (entryId == null) {
            warnings.add(warning(VocabularyNormalizationIssue.MISSING_ENTRY_ID, "EntryID is missing or blank"));
        }

        String expression = AnkiFieldTextNormalizer.text(fieldValuesByName.get("Word"));
        if (expression == null) {
            warnings.add(warning(VocabularyNormalizationIssue.MISSING_EXPRESSION, "Word is missing or blank"));
        }

        String reading = AnkiFieldTextNormalizer.text(fieldValuesByName.get("Reading"));
        if (reading == null) {
            warnings.add(warning(VocabularyNormalizationIssue.MISSING_READING, "Reading is missing or blank"));
        }

        String partOfSpeech = AnkiFieldTextNormalizer.text(fieldValuesByName.get("PartOfSpeech"));
        if (partOfSpeech == null) {
            warnings.add(warning(VocabularyNormalizationIssue.EMPTY_PART_OF_SPEECH, "PartOfSpeech is missing or blank"));
        }

        List<NormalizedMeaning> meanings = parseMeanings(fieldValuesByName.get("Meaning"));
        if (meanings.isEmpty()) {
            warnings.add(warning(VocabularyNormalizationIssue.MISSING_MEANING, "Meaning is missing, blank, or contains no usable sense"));
        }

        NormalizedPitchAccent pitchAccent = parsePitchAccent(fieldValuesByName.get("PitchAccent"), warnings);
        List<NormalizedExample> examples = parseExamples(fieldValuesByName.get("ExamplesRendered"), warnings);
        NormalizedJlptLevel level = parseLevel(fieldValuesByName, warnings);
        Map<String, String> extras = collectExtras(fieldValuesByName, warnings);

        boolean validForPromotion = warnings.stream()
                .noneMatch(warning -> warning.severity() == VocabularyNormalizationSeverity.FATAL);

        return new VocabularyNormalizationResult(
                sourceRef,
                sourceNoteId,
                entryId,
                expression,
                reading,
                partOfSpeech,
                pitchAccent,
                meanings,
                examples,
                level,
                ContentSearchNormalizer.normalize(expression),
                ContentSearchNormalizer.normalize(reading),
                extras,
                warnings,
                validForPromotion);
    }

    private List<NormalizedMeaning> parseMeanings(String raw) {
        String cleaned = AnkiFieldTextNormalizer.text(raw);
        if (cleaned == null) return List.of();
        String collapsed = REPEATED_MEANING_SEPARATOR.matcher(cleaned).replaceAll(" / ");
        List<NormalizedMeaning> result = new ArrayList<>();
        int order = 1;
        for (String fragment : MEANING_SPLIT.split(collapsed)) {
            String text = fragment.trim();
            if (!text.isBlank()) result.add(new NormalizedMeaning(order++, text));
        }
        return List.copyOf(result);
    }

    private NormalizedPitchAccent parsePitchAccent(String html, List<VocabularyNormalizationWarning> warnings) {
        if (html == null || html.isBlank()) return null;
        Matcher terminal = PITCH_TERMINAL.matcher(html);
        Matcher mora = PITCH_MORA.matcher(html);
        if (!terminal.find() || !mora.find()) {
            warnings.add(warning(VocabularyNormalizationIssue.PITCH_ACCENT_PARSE_FAILED,
                    "PitchAccent HTML did not contain the expected data-pitch-terminal-states/j1 attributes"));
            return null;
        }
        return new NormalizedPitchAccent(terminal.group(1), mora.group(1));
    }

    private List<NormalizedExample> parseExamples(String renderedHtml, List<VocabularyNormalizationWarning> warnings) {
        if (renderedHtml == null || renderedHtml.isBlank()) return List.of();
        List<ParsedExample> parsed;
        try {
            parsed = exampleHtmlParser.parse(renderedHtml);
        } catch (RuntimeException exception) {
            warnings.add(warning(VocabularyNormalizationIssue.EXAMPLE_PARSE_FALLBACK,
                    "ExamplesRendered HTML parsing threw " + exception.getClass().getSimpleName()));
            return List.of();
        }
        if (parsed.isEmpty()) {
            warnings.add(warning(VocabularyNormalizationIssue.EXAMPLE_PARSE_FALLBACK,
                    "ExamplesRendered was non-blank but no example section was recognized"));
            return List.of();
        }
        List<NormalizedExample> result = new ArrayList<>();
        int order = 1;
        boolean droppedAny = false;
        for (ParsedExample example : parsed) {
            if (example.audioFileName() != null) {
                warnings.add(warning(VocabularyNormalizationIssue.AUDIO_REFERENCE_UNEXPECTED,
                        "ExampleHtmlParser reported an audio reference; Ticket 1.5 staging extraction should already have removed it"));
            }
            if (example.japaneseText() == null || example.japaneseText().isBlank()) {
                droppedAny = true;
                continue;
            }
            result.add(new NormalizedExample(order++, example.meaningLabel(), example.japaneseText(),
                    example.reading(), example.translation()));
        }
        if (droppedAny && result.isEmpty()) {
            warnings.add(warning(VocabularyNormalizationIssue.EXAMPLE_PARSE_FALLBACK,
                    "Every parsed example was missing usable Japanese text"));
        }
        return List.copyOf(result);
    }

    private NormalizedJlptLevel parseLevel(Map<String, String> values, List<VocabularyNormalizationWarning> warnings) {
        String wordJlpt = blankToNull(values.get("WordJLPT"));
        String jlpt = blankToNull(values.get("JLPT"));
        String raw = wordJlpt != null ? wordJlpt : jlpt;
        String field = wordJlpt != null ? "WordJLPT" : jlpt != null ? "JLPT" : null;
        if (raw == null) {
            return new NormalizedJlptLevel(null, null, null);
        }
        Matcher matcher = JLPT_LEVEL.matcher(raw.trim());
        if (!matcher.matches()) {
            warnings.add(warning(VocabularyNormalizationIssue.INVALID_JLPT_LEVEL,
                    "Could not parse a JLPT N1-N5 level out of " + field + "=" + raw));
            return new NormalizedJlptLevel(null, raw, field);
        }
        return new NormalizedJlptLevel(matcher.group(1).toUpperCase(Locale.ROOT), raw, field);
    }

    private Map<String, String> collectExtras(Map<String, String> values, List<VocabularyNormalizationWarning> warnings) {
        Map<String, String> extras = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String field = entry.getKey();
            if (PRIMARY_FIELDS.contains(field) || SENTINEL_FIELDS.contains(field)) continue;
            if (!KNOWN_EXTRA_FIELDS.contains(field)) {
                warnings.add(warning(VocabularyNormalizationIssue.UNKNOWN_EXTRA_FIELD,
                        "Field \"" + field + "\" is not part of the known VOCABULARY schema (primary/sentinel/extra)"));
            }
            String cleaned = AnkiFieldTextNormalizer.text(entry.getValue());
            if (cleaned != null) extras.put(field, cleaned);
        }
        return Map.copyOf(extras);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static VocabularyNormalizationWarning warning(VocabularyNormalizationIssue issue, String message) {
        return new VocabularyNormalizationWarning(issue, message);
    }
}
