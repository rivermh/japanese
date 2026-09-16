package com.japanese.content.importer;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import tools.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Reproducible, read-only JLPT-MAX private-staging profiling tool (test/dev scope only).
 * Not part of the production build path; not wired into any startup runner or production
 * service. Runs only when -Djapanese.actual-apkg=<path> is explicitly supplied (opt-in),
 * exactly like PrivateApkgExtractorTest.optionalActualApkgRunsOnlyInFreshH2Staging - so the
 * general Gradle test suite (and CI without the real ~1.1GB apkg file) always passes/skips
 * cleanly. Uses only an isolated in-memory H2 (never a production DB) and never opens any
 * ZIP media entry or audio binary. Writes a plain-text report under build/reports/ and makes
 * zero production DB / entity changes.
 */
class JlptMaxStagingProfilingReport {

    private static final String VOCAB = "JLPT MAX덱 어휘";
    private static final String GRAMMAR = "JLPT MAX덱 문법";
    private static final String QUESTION = "JLPT MAX덱 어휘문제";
    private static final String REFERENCE = "JLPT MAX덱 참조표";
    private static final Pattern JLPT_FIELD = Pattern.compile("(?i)(?:jlpt[-_ ]*)?(n[1-5])");
    private static final Pattern JLPT_TAG = Pattern.compile("(?i)jlpt::?\\s*(n[1-5])");
    private static final Pattern JLPT_ANY = Pattern.compile("(?i)\\bn([1-5])\\b");
    private static final Pattern HTML_TAG = Pattern.compile("<[a-zA-Z/][^>]*>");
    private static final Pattern IMG_REF = Pattern.compile("(?i)<img|\\.(jpg|jpeg|png|gif|webp)");
    private static final Pattern SOUND_REF = Pattern.compile("(?i)\\[sound:|\\.(mp3|ogg|wav|m4a)");
    private static final Pattern RUBY_REF = Pattern.compile("(?i)<ruby|<rt");
    private static final Pattern MCQ_MARK = Pattern.compile("(?i)[\\u2460-\\u2469]|\\b[A-D][.)]\\s|<li[ >]|choice|option|보기");

    @Test
    void ticket1StagingProfiling() throws Exception {
        String source = System.getProperty("japanese.actual-apkg");
        assumeTrue(source != null && Files.isRegularFile(Path.of(source)),
                "japanese.actual-apkg system property must point at the real JLPT-MAX apkg file");

        String url = "jdbc:h2:mem:ticket1_profiling_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .cleanDisabled(true).load().migrate();
        ObjectMapper json = new ObjectMapper();
        var extractor = new PrivateApkgExtractor(new DriverManagerDataSource(url, "sa", ""), json, new GrammarHtmlParser());
        var summary = extractor.extract(Path.of(source), "ticket1-profiling");

        // Relative to the test JVM working directory (the Gradle module root) so the tool stays
        // reproducible on any machine - never a session/user-specific absolute path.
        Path reportPath = Path.of("build", "reports", "jlpt-max-profiling", "staging-profile.txt");
        Files.createDirectories(reportPath.getParent());
        try (Connection db = DriverManager.getConnection(url, "sa", "");
             PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            out.println("JLPT-MAX Ticket 1 Staging Profiling Report");
            out.println("source file: " + source);
            out.println("extractor summary: " + summary);
            out.println();
            profile(db, json, out);
        }
        System.out.println("TICKET1_REPORT_WRITTEN " + reportPath.toAbsolutePath());
    }

    private void profile(Connection db, ObjectMapper json, PrintWriter out) throws Exception {
        List<Row> rows = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("select category, note_type, tags, deck_paths, "
                     + "field_names, field_values, card_metadata from private_apkg_notes order by id")) {
            while (rs.next()) {
                Row r = new Row();
                r.category = rs.getString(1);
                r.noteType = rs.getString(2);
                r.tags = rs.getString(3);
                r.deckPaths = json.readValue(rs.getString(4), List.class);
                r.fieldNames = json.readValue(rs.getString(5), List.class);
                r.fieldValues = json.readValue(rs.getString(6), List.class);
                r.cardMeta = json.readValue(rs.getString(7), List.class);
                r.byName = new LinkedHashMap<>();
                for (int i = 0; i < r.fieldNames.size(); i++) {
                    r.byName.put((String) r.fieldNames.get(i), i < r.fieldValues.size() ? (String) r.fieldValues.get(i) : "");
                }
                rows.add(r);
            }
        }

        // ===== A. overall =====
        out.println("=== A. OVERALL ===");
        long totalNotes = rows.size();
        long totalCards = rows.stream().mapToLong(r -> r.cardMeta.size()).sum();
        long multiCard = rows.stream().filter(r -> r.cardMeta.size() > 1).count();
        out.println("total notes: " + totalNotes);
        out.println("total cards: " + totalCards);
        out.println("multi-card notes: " + multiCard);
        Map<String, Long> byNoteType = new TreeMap<>();
        Map<String, Long> byCategory = new TreeMap<>();
        Map<String, Long> crossTab = new TreeMap<>();
        for (Row r : rows) {
            byNoteType.merge(r.noteType, 1L, Long::sum);
            byCategory.merge(r.category, 1L, Long::sum);
            crossTab.merge(r.category + " | " + r.noteType, 1L, Long::sum);
        }
        out.println("note type distribution: " + byNoteType);
        out.println("category distribution: " + byCategory);
        out.println("category x note_type cross-tab: " + crossTab);
        long comprehensiveGrammar = rows.stream().filter(r -> r.category.equals("COMPREHENSIVE") && r.noteType.equals(GRAMMAR)).count();
        long comprehensiveQuestion = rows.stream().filter(r -> r.category.equals("COMPREHENSIVE") && r.noteType.equals(QUESTION)).count();
        out.println("COMPREHENSIVE grammar-model notes: " + comprehensiveGrammar);
        out.println("COMPREHENSIVE question-model notes: " + comprehensiveQuestion);
        long mixedDeckNotes = rows.stream().filter(r -> r.category.equals("COMPREHENSIVE")
                && r.deckPaths.stream().anyMatch(p -> !isComprehensivePath((String) p))).count();
        out.println("COMPREHENSIVE notes that ALSO have a non-comprehensive deck path (mixed): " + mixedDeckNotes);
        out.println();

        // ===== B. field inventory per note type =====
        out.println("=== B. FIELD INVENTORY PER NOTE TYPE ===");
        Map<String, Set<List<Object>>> schemaVariants = new TreeMap<>();
        for (Row r : rows) schemaVariants.computeIfAbsent(r.noteType, k -> new HashSet<>()).add(r.fieldNames);
        for (var e : schemaVariants.entrySet()) {
            out.println(e.getKey() + " -> distinct field_names schemas seen: " + e.getValue().size()
                    + (e.getValue().size() > 1 ? "  *** SCHEMA INCONSISTENCY ***" : ""));
        }
        for (String noteType : byNoteType.keySet()) {
            out.println("--- fields for note_type: " + noteType + " ---");
            List<Row> subset = rows.stream().filter(r -> r.noteType.equals(noteType)).toList();
            List<String> fieldOrder = subset.get(0).fieldNames.stream().map(Object::toString).toList();
            for (String field : fieldOrder) {
                long exists = subset.size();
                long nonEmpty = 0, htmlCount = 0, rubyCount = 0, imgCount = 0, soundCount = 0, jsonLike = 0, delimLike = 0;
                int min = Integer.MAX_VALUE, max = 0; long sum = 0;
                for (Row r : subset) {
                    String v = r.byName.getOrDefault(field, "");
                    if (v != null && !v.isBlank()) {
                        nonEmpty++;
                        int len = v.length();
                        min = Math.min(min, len); max = Math.max(max, len); sum += len;
                        if (HTML_TAG.matcher(v).find()) htmlCount++;
                        if (RUBY_REF.matcher(v).find()) rubyCount++;
                        if (IMG_REF.matcher(v).find()) imgCount++;
                        if (SOUND_REF.matcher(v).find()) soundCount++;
                        String t = v.trim();
                        if (t.startsWith("{") || t.startsWith("[")) jsonLike++;
                        if (countOccurrences(v, " / ") >= 1 || countOccurrences(v, ";") >= 2 || countOccurrences(v, "|") >= 2) delimLike++;
                    }
                }
                long empty = exists - nonEmpty;
                double ratio = exists == 0 ? 0 : (double) nonEmpty / exists * 100.0;
                double avg = nonEmpty == 0 ? 0 : (double) sum / nonEmpty;
                out.printf("  %-20s exists=%d nonEmpty=%d empty=%d nonEmptyPct=%.1f%% minLen=%d maxLen=%d avgLen=%.1f html=%d ruby=%d img=%d soundResidue=%d jsonLike=%d delimLike=%d%n",
                        field, exists, nonEmpty, empty, ratio, nonEmpty == 0 ? 0 : min, max, avg, htmlCount, rubyCount, imgCount, soundCount, jsonLike, delimLike);
            }
        }
        out.println();

        // ===== C. Vocabulary quality/profile =====
        out.println("=== C. VOCABULARY QUALITY/PROFILE ===");
        List<Row> vocab = rows.stream().filter(r -> r.noteType.equals(VOCAB)).toList();
        AnkiFieldTextNormalizer.class.getName(); // ensure class loaded
        long wEmpty = 0, rEmpty = 0, mEmpty = 0, pEmpty = 0, lEmpty = 0, exEmpty = 0;
        long overWord = 0, overReading = 0, overMeaning = 0, overExample = 0;
        ExampleHtmlParser exampleParser = new ExampleHtmlParser();
        for (Row r : vocab) {
            String word = AnkiFieldTextNormalizer.text(r.byName.get("Word"));
            String reading = AnkiFieldTextNormalizer.text(r.byName.get("Reading"));
            String meaning = AnkiFieldTextNormalizer.text(r.byName.get("Meaning"));
            String pos = AnkiFieldTextNormalizer.text(r.byName.get("PartOfSpeech"));
            String jlpt = firstNonBlank(r.byName.get("WordJLPT"), r.byName.get("JLPT"));
            String examplesRaw = r.byName.get("ExamplesRendered");
            if (blank(word)) wEmpty++; else if (word.length() > 120) overWord++;
            if (blank(reading)) rEmpty++; else if (reading.length() > 120) overReading++;
            if (blank(meaning)) mEmpty++;
            else for (String part : meaning.split("\\s+/(?:\\s+|$)")) if (part.trim().length() > 500) overMeaning++;
            if (blank(pos)) pEmpty++;
            if (blank(jlpt)) lEmpty++;
            if (blank(examplesRaw)) exEmpty++;
            else {
                var examples = exampleParser.parse(examplesRaw);
                for (var ex : examples) {
                    if (safeLen(ex.japaneseText()) > 1000 || safeLen(ex.reading()) > 1000 || safeLen(ex.translation()) > 1000) overExample++;
                }
            }
        }
        out.println("vocabulary notes: " + vocab.size());
        out.println("Word empty: " + wEmpty + "  (> Word.expression length 120: " + overWord + ")");
        out.println("Reading empty: " + rEmpty + "  (> Word.reading length 120: " + overReading + ")");
        out.println("Meaning empty: " + mEmpty + "  (individual sense > Meaning.text length 500: " + overMeaning + ")");
        out.println("PartOfSpeech empty: " + pEmpty);
        out.println("JLPT/WordJLPT empty: " + lEmpty);
        out.println("ExamplesRendered empty: " + exEmpty + "  (any parsed example field > 1000: " + overExample + ")");
        out.println();

        // dedup profiling for vocabulary
        out.println("--- vocabulary duplicate/identity profiling (report only, no merge) ---");
        Map<String, Long> byWord = new HashMap<>(), byWordReading = new HashMap<>(), byWordReadingLevel = new HashMap<>(), byEntryId = new HashMap<>();
        Map<String, Set<String>> meaningsByWordReading = new HashMap<>();
        for (Row r : vocab) {
            String word = norm(AnkiFieldTextNormalizer.text(r.byName.get("Word")));
            String reading = norm(AnkiFieldTextNormalizer.text(r.byName.get("Reading")));
            String meaning = AnkiFieldTextNormalizer.text(r.byName.get("Meaning"));
            String jlpt = normJlpt(firstNonBlank(r.byName.get("WordJLPT"), r.byName.get("JLPT")));
            String entryId = r.byName.get("EntryID");
            if (word != null) byWord.merge(word, 1L, Long::sum);
            if (word != null && reading != null) byWordReading.merge(word + "|" + reading, 1L, Long::sum);
            if (word != null && reading != null) byWordReadingLevel.merge(word + "|" + reading + "|" + jlpt, 1L, Long::sum);
            if (entryId != null && !entryId.isBlank()) byEntryId.merge(entryId, 1L, Long::sum);
            if (word != null && reading != null)
                meaningsByWordReading.computeIfAbsent(word + "|" + reading, k -> new HashSet<>()).add(meaning == null ? "" : meaning);
        }
        out.println("exact Word duplicate groups (>1 note): " + byWord.values().stream().filter(c -> c > 1).count());
        out.println("Word+Reading duplicate groups (>1 note): " + byWordReading.values().stream().filter(c -> c > 1).count());
        out.println("Word+Reading+Level duplicate groups (>1 note): " + byWordReadingLevel.values().stream().filter(c -> c > 1).count());
        out.println("EntryID duplicate groups (>1 note): " + byEntryId.values().stream().filter(c -> c > 1).count());
        out.println("same Word+Reading but differing Meaning groups: "
                + meaningsByWordReading.values().stream().filter(s -> s.size() > 1).count());
        out.println();

        // ===== D. Grammar quality/profile =====
        out.println("=== D. GRAMMAR QUALITY/PROFILE ===");
        List<Row> grammar = rows.stream().filter(r -> r.noteType.equals(GRAMMAR)).toList();
        GrammarHtmlParser grammarParser = new GrammarHtmlParser();
        long uEmpty = 0, fEmpty = 0, bEmpty = 0, kEmpty = 0, gjlEmpty = 0;
        long frontFallback = 0, backFallback = 0, patternEmpty = 0, explanationEmpty = 0, examplesZero = 0;
        long overPattern = 0, overExplanation = 0, overConnection = 0;
        for (Row r : grammar) {
            String unitId = r.byName.get("UnitID");
            String frontHtml = r.byName.get("FrontHTML");
            String backHtml = r.byName.get("BackHTML");
            String kind = r.byName.get("Kind");
            String level = r.byName.get("Level");
            if (blank(unitId)) uEmpty++;
            if (blank(frontHtml)) fEmpty++;
            if (blank(backHtml)) bEmpty++;
            if (blank(kind)) kEmpty++;
            if (blank(level)) gjlEmpty++;
            Element front = Jsoup.parseBodyFragment(frontHtml == null ? "" : frontHtml).body();
            Element back = Jsoup.parseBodyFragment(backHtml == null ? "" : backHtml).body();
            boolean fFallback = front.selectFirst("mark") == null && front.selectFirst("div[lang=ja]") == null;
            Element explDiv = back.selectFirst("div._j4z");
            boolean bFallback = explDiv == null || explDiv.text().trim().isBlank();
            if (fFallback) frontFallback++;
            if (bFallback) backFallback++;
            String preTruncPattern = fFallback ? front.text() : (front.selectFirst("mark") != null ? front.selectFirst("mark").text() : front.selectFirst("div[lang=ja]").text());
            String preTruncExplanation = bFallback ? back.text() : explDiv.text().trim();
            if (preTruncPattern != null && preTruncPattern.length() > 200) overPattern++;
            if (preTruncExplanation != null && preTruncExplanation.length() > 2000) overExplanation++;
            String connectionNormalized = AnkiFieldTextNormalizer.text(kind);
            if (connectionNormalized != null && connectionNormalized.length() > 500) overConnection++;
            var parsed = grammarParser.parse(frontHtml, backHtml, kind, unitId);
            if (parsed.pattern() == null || parsed.pattern().isBlank()) patternEmpty++;
            if (parsed.explanation() == null || parsed.explanation().isBlank()) explanationEmpty++;
            if (parsed.examples().isEmpty()) examplesZero++;
        }
        out.println("grammar notes (all decks, incl. COMPREHENSIVE): " + grammar.size());
        out.println("UnitID empty: " + uEmpty + "  FrontHTML empty: " + fEmpty + "  BackHTML empty: " + bEmpty
                + "  Kind empty: " + kEmpty + "  Level empty: " + gjlEmpty);
        out.println("GrammarHtmlParser front-fallback (no <mark>/div[lang=ja]): " + frontFallback);
        out.println("GrammarHtmlParser back-fallback (no/blank div._j4z): " + backFallback);
        out.println("parsed pattern empty (post-parse): " + patternEmpty);
        out.println("parsed explanation empty (post-parse): " + explanationEmpty);
        out.println("parsed examples count == 0: " + examplesZero);
        out.println("pre-truncation pattern length > 200 (Grammar.pattern limit): " + overPattern);
        out.println("pre-truncation explanation length > 2000 (Grammar.explanation limit): " + overExplanation);
        out.println("connection length > 500 (Grammar.connection limit): " + overConnection);
        out.println();

        out.println("--- grammar duplicate/identity profiling (report only, no merge) ---");
        Map<String, Long> byPattern = new HashMap<>(), byPatternLevel = new HashMap<>(), byUnitId = new HashMap<>();
        Map<String, Set<String>> levelsByPattern = new HashMap<>();
        for (Row r : grammar) {
            var parsed = grammarParser.parse(r.byName.get("FrontHTML"), r.byName.get("BackHTML"), r.byName.get("Kind"), r.byName.get("UnitID"));
            String pattern = norm(parsed.pattern());
            String level = normJlpt(r.byName.get("Level"));
            String unitId = r.byName.get("UnitID");
            if (pattern != null) byPattern.merge(pattern, 1L, Long::sum);
            if (pattern != null) byPatternLevel.merge(pattern + "|" + level, 1L, Long::sum);
            if (unitId != null && !unitId.isBlank()) byUnitId.merge(unitId, 1L, Long::sum);
            if (pattern != null) levelsByPattern.computeIfAbsent(pattern, k -> new HashSet<>()).add(level == null ? "" : level);
        }
        out.println("normalized pattern duplicate groups (>1 note): " + byPattern.values().stream().filter(c -> c > 1).count());
        out.println("pattern+Level duplicate groups (>1 note): " + byPatternLevel.values().stream().filter(c -> c > 1).count());
        out.println("UnitID duplicate groups (>1 note): " + byUnitId.values().stream().filter(c -> c > 1).count());
        out.println("same pattern but differing Level groups: " + levelsByPattern.values().stream().filter(s -> s.size() > 1).count());
        out.println();

        // ===== E. PRACTICE / COMPREHENSIVE question profile =====
        out.println("=== E. PRACTICE/COMPREHENSIVE QUESTION PROFILE ===");
        List<Row> question = rows.stream().filter(r -> r.noteType.equals(QUESTION)).toList();
        out.println("question-model notes: " + question.size());
        for (String field : List.of("QuestionType", "PromptJP", "PromptKO", "AnswerJP", "AnswerKO", "ExplanationHTML")) {
            long nonEmpty = question.stream().filter(r -> !blank(r.byName.get(field))).count();
            out.printf("  %-16s filled=%d / %d (%.1f%%)%n", field, nonEmpty, question.size(),
                    question.isEmpty() ? 0 : nonEmpty * 100.0 / question.size());
        }
        out.println("full field name list for question model (from first note): "
                + (question.isEmpty() ? "N/A" : question.get(0).fieldNames));
        long mcqSignal = 0;
        for (Row r : question) {
            boolean found = false;
            for (String v : r.byName.values()) if (v != null && MCQ_MARK.matcher(v).find()) { found = true; break; }
            if (found) mcqSignal++;
        }
        out.println("question notes with a multiple-choice-like signal (circled digits/A)-D)/<li>/'choice'/'option'/'보기') in ANY field value: "
                + mcqSignal + " / " + question.size());
        out.println();

        // ===== F. REFERENCE =====
        out.println("=== F. REFERENCE ===");
        List<Row> reference = rows.stream().filter(r -> r.noteType.equals(REFERENCE)).toList();
        out.println("reference notes: " + reference.size());
        out.println("full field name list: " + (reference.isEmpty() ? "N/A" : reference.get(0).fieldNames));
        int idx = 1;
        for (Row r : reference) {
            String title = r.byName.get("Title");
            String partLabel = r.byName.get("PartLabel");
            String tableKind = r.byName.get("TableKind");
            String tableHtml = r.byName.get("TableHTML");
            boolean hasTable = tableHtml != null && (tableHtml.contains("<tr") || tableHtml.contains("<table"));
            out.println("  [" + (idx++) + "] Title=" + excerpt(title, 80) + " | PartLabel=" + excerpt(partLabel, 60)
                    + " | TableKind=" + excerpt(tableKind, 40) + " | TableHTML len=" + safeLen(tableHtml)
                    + " hasTableMarkup=" + hasTable + " excerpt=" + excerpt(tableHtml, 200));
        }
        out.println();

        // ===== 6. JLPT level provenance conflict =====
        out.println("=== 6. JLPT LEVEL PROVENANCE CONFLICT ===");
        long match = 0, mismatch = 0, fieldOnlyNoTag = 0, tagOnlyNoField = 0, deckMismatch = 0, undetermined = 0;
        for (Row r : rows) {
            if (!(r.noteType.equals(VOCAB) || r.noteType.equals(GRAMMAR))) continue;
            String fieldRaw = r.noteType.equals(GRAMMAR) ? r.byName.get("Level") : firstNonBlank(r.byName.get("WordJLPT"), r.byName.get("JLPT"));
            String fieldLevel = normJlpt(fieldRaw);
            Matcher tagMatcher = JLPT_TAG.matcher(r.tags == null ? "" : r.tags);
            String tagLevel = tagMatcher.find() ? tagMatcher.group(1).toUpperCase() : null;
            Set<String> deckLevels = new HashSet<>();
            for (Object p : r.deckPaths) {
                Matcher dm = JLPT_ANY.matcher((String) p);
                while (dm.find()) deckLevels.add("N" + dm.group(1));
            }
            if (fieldLevel != null && tagLevel != null) { if (fieldLevel.equals(tagLevel)) match++; else mismatch++; }
            else if (fieldLevel != null) fieldOnlyNoTag++;
            else if (tagLevel != null) tagOnlyNoField++;
            if (fieldLevel != null && !deckLevels.isEmpty() && !deckLevels.contains(fieldLevel)) deckMismatch++;
            if (fieldLevel == null && tagLevel == null && deckLevels.isEmpty()) undetermined++;
        }
        out.println("field/tag level match: " + match);
        out.println("field/tag level mismatch: " + mismatch);
        out.println("field present, no tag level: " + fieldOnlyNoTag);
        out.println("tag level present, no field: " + tagOnlyNoField);
        out.println("field level not found among deck-path levels (deck mismatch): " + deckMismatch);
        out.println("completely undetermined (no field/tag/deck level signal): " + undetermined);
        out.println();

        // ===== 7. audio isolation reconfirm (text-only, no binary access) =====
        out.println("=== 7. AUDIO ISOLATION RECONFIRM (raw stored field_values only; no media binaries opened) ===");
        long soundResidue = rows.stream().filter(r -> r.byName.values().stream()
                .anyMatch(v -> v != null && SOUND_REF.matcher(v).find())).count();
        out.println("notes whose STORED raw field_values still contain a sound/media filename residue: " + soundResidue
                + " (expected 0 - PrivateApkgExtractor strips these before insert)");
    }

    private static boolean isComprehensivePath(String path) {
        return path.contains("종합 실전") || path.contains("종합실전") || path.contains("모의") || path.toLowerCase().contains("mock");
    }

    private static boolean blank(String v) { return v == null || v.isBlank(); }
    private static int safeLen(String v) { return v == null ? 0 : v.length(); }
    private static String norm(String v) { return v == null ? null : v.trim().toLowerCase(); }
    private static String normJlpt(String raw) {
        if (raw == null) return null;
        Matcher m = JLPT_FIELD.matcher(raw.trim());
        return m.matches() ? m.group(1).toUpperCase() : null;
    }
    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }
    private static String excerpt(String v, int max) {
        if (v == null) return "null";
        String clean = v.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }
    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) { count++; idx += needle.length(); }
        return count;
    }

    private static class Row {
        String category, noteType, tags;
        List<Object> deckPaths, fieldNames, fieldValues, cardMeta;
        Map<String, String> byName;
    }
}
