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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * JLPT-MAX Ticket 3A.1: reproducible, read-only Grammar-model profiling tool (test/dev scope
 * only), extending the Ticket 1 {@link JlptMaxStagingProfilingReport} approach with the
 * note-level detail (Is* flag cross-tabs, Kind distinct values, FrontHTML/BackHTML DOM structure,
 * length-bucket distributions) needed to design a future GrammarNormalizationParser. Not part of
 * the production build path; not wired into any startup runner or production service. Runs only
 * when -Djapanese.actual-apkg=&lt;path&gt; is explicitly supplied (opt-in, same gate as
 * {@link PrivateApkgExtractorTest#optionalActualApkgRunsOnlyInFreshH2Staging()}), so the general
 * Gradle test suite (and CI without the real ~1.1GB apkg file) always passes/skips cleanly. Uses
 * only an isolated in-memory H2 (never a production DB) and never opens any ZIP media entry or
 * audio binary - it only calls {@link PrivateApkgExtractor}, which already enforces that.
 *
 * <p>This class intentionally never asserts a fixed field-name schema for the Grammar model: it
 * reads whatever {@code field_names} the real note type actually carries and reports the
 * observed schema, so an incorrect assumption about field names (e.g. "IsBasic") is visible in
 * the report rather than silently masked by a hardcoded constant.
 */
class GrammarNormalizationProfilingReport {

    private static final String GRAMMAR_MODEL = "JLPT MAX덱 문법";
    private static final int[] LENGTH_THRESHOLDS = {50, 80, 100, 120, 200};
    private static final Pattern CHOICE_SIGNAL =
            Pattern.compile("(?i)[①-⑩]|\\b[A-D][.)]\\s|<li[ >]|choice|option|보기");
    private static final Pattern ANSWER_SIGNAL = Pattern.compile("(?i)정답|해설|answer|explanation");
    private static final Pattern BLANK_SIGNAL = Pattern.compile("_{2,}|\\(\\s*\\)|（\\s*）|□|빈칸|blank");
    private static final Pattern ORDER_SIGNAL = Pattern.compile("(?i)순서|배열|order|arrange|나열");

    @Test
    void ticket3a1GrammarProfiling() throws Exception {
        String source = System.getProperty("japanese.actual-apkg");
        assumeTrue(source != null && Files.isRegularFile(Path.of(source)),
                "japanese.actual-apkg system property must point at the real JLPT-MAX apkg file");

        String url = "jdbc:h2:mem:ticket3a1_grammar_profiling_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .cleanDisabled(true).load().migrate();
        ObjectMapper json = new ObjectMapper();
        var extractor = new PrivateApkgExtractor(new DriverManagerDataSource(url, "sa", ""), json, new GrammarHtmlParser());
        var summary = extractor.extract(Path.of(source), "ticket3a1-grammar-profiling");

        Path reportPath = Path.of("build", "reports", "jlpt-max-profiling", "grammar-profiling.txt");
        Files.createDirectories(reportPath.getParent());
        try (Connection db = DriverManager.getConnection(url, "sa", "");
             PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            out.println("JLPT-MAX Ticket 3A.1 Grammar Profiling Report");
            out.println("source file: " + source);
            out.println("extractor summary: " + summary);
            out.println();
            List<Row> rows = loadRows(db, json);
            writeReport(rows, out);
        }
        System.out.println("TICKET3A1_GRAMMAR_REPORT_WRITTEN " + reportPath.toAbsolutePath());
    }

    // ===================================================================================
    // loading
    // ===================================================================================

    static List<Row> loadRows(Connection db, ObjectMapper json) throws Exception {
        List<Row> rows = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("select source_note_id, category, tags, field_names, field_values "
                     + "from private_apkg_notes where note_type = '" + GRAMMAR_MODEL.replace("'", "''")
                     + "' order by id")) {
            while (rs.next()) {
                Row r = new Row();
                r.id = rs.getLong(1);
                r.category = rs.getString(2);
                r.tags = rs.getString(3);
                List<Object> fieldNames = json.readValue(rs.getString(4), List.class);
                List<Object> fieldValues = json.readValue(rs.getString(5), List.class);
                r.fieldNames = fieldNames.stream().map(Object::toString).toList();
                r.byName = new LinkedHashMap<>();
                for (int i = 0; i < fieldNames.size(); i++) {
                    r.byName.put((String) fieldNames.get(i), i < fieldValues.size() ? (String) fieldValues.get(i) : "");
                }
                rows.add(r);
            }
        }
        return rows;
    }

    // ===================================================================================
    // report
    // ===================================================================================

    static void writeReport(List<Row> rows, PrintWriter out) {
        GrammarHtmlParser parser = new GrammarHtmlParser();

        out.println("=== 0. FIELD SCHEMA (as actually observed on this model) ===");
        Set<List<String>> schemas = new HashSet<>();
        for (Row r : rows) schemas.add(r.fieldNames);
        out.println("distinct field_names schemas seen for " + GRAMMAR_MODEL + ": " + schemas.size()
                + (schemas.size() > 1 ? "  *** SCHEMA INCONSISTENCY - later sections assume a single schema ***" : ""));
        if (!rows.isEmpty()) out.println("field order: " + rows.get(0).fieldNames);
        for (String flag : List.of("IsBasic", "IsGrammarForm", "IsPassageBlank", "IsSentenceArrangement", "Kind", "UnitID", "Level", "FrontHTML", "BackHTML")) {
            boolean present = !rows.isEmpty() && rows.get(0).byName.containsKey(flag);
            out.println("  expected field '" + flag + "' present in schema: " + present);
        }
        out.println();

        long total = rows.size();
        Map<String, Long> byCategory = new TreeMap<>();
        for (Row r : rows) byCategory.merge(r.category, 1L, Long::sum);
        out.println("=== A. OVERALL ===");
        out.println("total grammar-model notes: " + total);
        out.println("category distribution: " + byCategory);
        out.println();

        section5IsFlagCrossTab(rows, out);
        section6Kind(rows, out);
        List<Row> normalGrammar = rows.stream().filter(r -> "GRAMMAR".equals(r.category)).toList();
        List<Row> comprehensive = rows.stream().filter(r -> "COMPREHENSIVE".equals(r.category)).toList();
        section7and8FrontHtmlStructure(normalGrammar, parser, out);
        section9and10BackHtmlStructure(comprehensive, parser, out);
        section11SentenceArrangement(rows, comprehensive, parser, out);
        section12FlagMeaning(rows, out);
        section13ExplanationOver2000(rows, parser, out);
        section14Examples(normalGrammar, comprehensive, parser, out);
        section15UnitId(rows, out);
        section16LevelBySubtype(rows, out);
    }

    // ===== 5. Is* flag note-level cross-tab =====
    private static void section5IsFlagCrossTab(List<Row> rows, PrintWriter out) {
        out.println("=== 5. Is* FLAG NOTE-LEVEL CROSS-TAB ===");
        List<String> flags = List.of("IsBasic", "IsGrammarForm", "IsPassageBlank", "IsSentenceArrangement");
        for (String flag : flags) {
            Map<String, Long> distinct = valueCounts(rows, flag);
            out.println("distinct raw values for " + flag + ": " + distinct);
        }
        Map<List<String>, Long> combo = new LinkedHashMap<>();
        for (Row r : rows) {
            List<String> key = new ArrayList<>();
            key.add(r.category);
            for (String flag : flags) key.add(raw(r, flag));
            combo.merge(key, 1L, Long::sum);
        }
        out.println("distinct (category, IsBasic, IsGrammarForm, IsPassageBlank, IsSentenceArrangement) combinations: " + combo.size());
        combo.entrySet().stream()
                .sorted(Map.Entry.<List<String>, Long>comparingByValue().reversed())
                .limit(100)
                .forEach(e -> out.println("  category=" + e.getKey().get(0)
                        + " IsBasic=" + quote(e.getKey().get(1))
                        + " IsGrammarForm=" + quote(e.getKey().get(2))
                        + " IsPassageBlank=" + quote(e.getKey().get(3))
                        + " IsSentenceArrangement=" + quote(e.getKey().get(4))
                        + " -> " + e.getValue() + " notes"));
        out.println();
    }

    // ===== 6. Kind field analysis =====
    private static void section6Kind(List<Row> rows, PrintWriter out) {
        out.println("=== 6. Kind FIELD ANALYSIS ===");
        Map<String, Long> kindCounts = valueCounts(rows, "Kind");
        out.println("Kind distinct raw values -> count: " + kindCounts);
        Map<String, Map<String, Long>> kindByCategory = new TreeMap<>();
        Map<String, Map<String, Long>> kindByLevel = new TreeMap<>();
        Map<String, Map<List<String>, Long>> kindByFlagCombo = new TreeMap<>();
        List<String> flags = List.of("IsBasic", "IsGrammarForm", "IsPassageBlank", "IsSentenceArrangement");
        for (Row r : rows) {
            String kind = quote(raw(r, "Kind"));
            kindByCategory.computeIfAbsent(kind, k -> new TreeMap<>()).merge(r.category, 1L, Long::sum);
            kindByLevel.computeIfAbsent(kind, k -> new TreeMap<>()).merge(quote(raw(r, "Level")), 1L, Long::sum);
            List<String> combo = flags.stream().map(f -> raw(r, f)).toList();
            kindByFlagCombo.computeIfAbsent(kind, k -> new LinkedHashMap<>()).merge(combo, 1L, Long::sum);
        }
        out.println("Kind x category: " + kindByCategory);
        out.println("Kind x Level: " + kindByLevel);
        out.println("Kind x (IsBasic,IsGrammarForm,IsPassageBlank,IsSentenceArrangement): " + kindByFlagCombo);
        out.println();
    }

    // ===== 7/8. normal GRAMMAR FrontHTML structure + pattern length =====
    private static void section7and8FrontHtmlStructure(List<Row> normalGrammar, GrammarHtmlParser parser, PrintWriter out) {
        out.println("=== 7/8. NORMAL GRAMMAR FrontHTML STRUCTURE + PATTERN LENGTH (category=GRAMMAR, n=" + normalGrammar.size() + ") ===");
        if (normalGrammar.isEmpty()) {
            out.println("(no rows in this category - skipped)");
            out.println();
            return;
        }
        List<Integer> rawLens = new ArrayList<>();
        List<Integer> selectedLens = new ArrayList<>();
        List<Integer> ownTextLens = new ArrayList<>();
        List<Integer> firstChildLens = new ArrayList<>();
        long markCount = 0, divLangJaCount = 0, bothAbsent = 0;
        long selectedIsMark = 0, selectedIsDivLangJa = 0, selectedIsFallback = 0;
        Map<String, Long> childTagPresence = new TreeMap<>();
        Map<String, Long> childClassPresence = new TreeMap<>();
        List<String> skeletons = new ArrayList<>();
        for (Row r : normalGrammar) {
            String frontHtml = raw(r, "FrontHTML");
            Element front = Jsoup.parseBodyFragment(frontHtml == null ? "" : frontHtml).body();
            rawLens.add(frontHtml == null ? 0 : frontHtml.length());
            Element mark = front.selectFirst("mark");
            Element divLangJa = front.selectFirst("div[lang=ja]");
            if (mark != null) markCount++;
            if (divLangJa != null) divLangJaCount++;
            if (mark == null && divLangJa == null) bothAbsent++;
            Element selected = mark != null ? mark : divLangJa;
            if (selected == mark && mark != null) selectedIsMark++;
            else if (selected == divLangJa && divLangJa != null) selectedIsDivLangJa++;
            else selectedIsFallback++;
            String selectedText = selected == null ? front.text() : selected.text();
            selectedLens.add(selectedText.length());
            if (selected != null) {
                ownTextLens.add(selected.ownText().length());
                Element firstChildEl = selected.children().isEmpty() ? null : selected.children().first();
                firstChildLens.add(firstChildEl == null ? selected.ownText().length() : firstChildEl.text().length());
                for (Element child : selected.children()) childTagPresence.merge(child.tagName(), 1L, Long::sum);
                for (Element el : selected.getAllElements()) for (String cls : el.classNames()) childClassPresence.merge(cls, 1L, Long::sum);
            }
            if (skeletons.size() < 8) {
                StringBuilder sb = new StringBuilder();
                sb.append("  [note ").append(r.id).append("] selected=")
                        .append(selected == null ? "(fallback:front.text())" : selected.tagName()
                                + (selected.className().isBlank() ? "" : "." + selected.className()))
                        .append(" rawLen=").append(frontHtml == null ? 0 : frontHtml.length())
                        .append(" selectedTextLen=").append(selectedText.length()).append('\n');
                appendSkeleton(front, sb, "    ", 0, 3);
                skeletons.add(sb.toString());
            }
        }
        out.println("FrontHTML raw length: " + stats(rawLens));
        out.println("<mark> present: " + markCount + " / " + normalGrammar.size());
        out.println("div[lang=ja] present: " + divLangJaCount + " / " + normalGrammar.size());
        out.println("neither present (parser falls back to front.text()): " + bothAbsent + " / " + normalGrammar.size());
        out.println("parser selected element: mark=" + selectedIsMark + " div[lang=ja]=" + selectedIsDivLangJa + " fallback=" + selectedIsFallback);
        out.println("selected element text length (= current parser pre-truncation pattern length): " + stats(selectedLens));
        out.println("selected element text length threshold buckets (count exceeding each threshold): " + thresholdBuckets(selectedLens));
        out.println("selected element OWN text only (excludes descendant elements) length: " + stats(ownTextLens));
        out.println("selected element first-child-element text length (candidate narrower boundary): " + stats(firstChildLens));
        out.println("direct child tag frequency within selected element (notes containing at least one): " + childTagPresence);
        out.println("descendant CSS class frequency within selected element (top 20 by count): " + topN(childClassPresence, 20));
        out.println("--- sample structure skeletons (tag/class + <=12-char leaf fragments only, no full copyrighted text) ---");
        skeletons.forEach(out::print);
        out.println();
    }

    // ===== 9/10. COMPREHENSIVE BackHTML structure + content classification signals =====
    private static void section9and10BackHtmlStructure(List<Row> comprehensive, GrammarHtmlParser parser, PrintWriter out) {
        out.println("=== 9/10. COMPREHENSIVE BackHTML STRUCTURE + CONTENT SIGNALS (category=COMPREHENSIVE, n=" + comprehensive.size() + ") ===");
        if (comprehensive.isEmpty()) {
            out.println("(no rows in this category - skipped)");
            out.println();
            return;
        }
        Map<String, Long> tagPresence = new TreeMap<>();
        Map<String, Long> divClassPresence = new TreeMap<>();
        Map<String, Long> sectionClassPresence = new TreeMap<>();
        Map<String, Long> otherClassPresence = new TreeMap<>();
        long hasForm = 0, hasInput = 0, hasOl = 0, hasUl = 0, hasLi = 0, hasTable = 0, hasRuby = 0;
        long hasExplDiv = 0, hasExampleSection = 0;
        long choiceSignal = 0, answerSignal = 0, blankSignal = 0, orderSignal = 0;
        List<String> skeletons = new ArrayList<>();
        for (Row r : comprehensive) {
            String backHtml = raw(r, "BackHTML");
            Element back = Jsoup.parseBodyFragment(backHtml == null ? "" : backHtml).body();
            Set<String> tagsInNote = new TreeSet<>();
            for (Element el : back.getAllElements()) tagsInNote.add(el.tagName());
            for (String tag : tagsInNote) tagPresence.merge(tag, 1L, Long::sum);
            for (Element div : back.select("div")) for (String cls : div.classNames()) divClassPresence.merge(cls, 1L, Long::sum);
            for (Element section : back.select("section")) for (String cls : section.classNames()) sectionClassPresence.merge(cls, 1L, Long::sum);
            for (Element el : back.getAllElements()) {
                if (el.tagName().equals("div") || el.tagName().equals("section")) continue;
                for (String cls : el.classNames()) otherClassPresence.merge(el.tagName() + "." + cls, 1L, Long::sum);
            }
            if (tagsInNote.contains("form")) hasForm++;
            if (tagsInNote.contains("input")) hasInput++;
            if (tagsInNote.contains("ol")) hasOl++;
            if (tagsInNote.contains("ul")) hasUl++;
            if (tagsInNote.contains("li")) hasLi++;
            if (tagsInNote.contains("table")) hasTable++;
            if (tagsInNote.contains("ruby")) hasRuby++;
            if (back.selectFirst("div._j4z") != null) hasExplDiv++;
            if (!back.select("section._j4a").isEmpty()) hasExampleSection++;
            String plain = back.text();
            if (CHOICE_SIGNAL.matcher(backHtml == null ? "" : backHtml).find() || CHOICE_SIGNAL.matcher(plain).find()) choiceSignal++;
            if (ANSWER_SIGNAL.matcher(plain).find()) answerSignal++;
            if (BLANK_SIGNAL.matcher(plain).find()) blankSignal++;
            if (ORDER_SIGNAL.matcher(plain).find()) orderSignal++;
            if (skeletons.size() < 8) {
                StringBuilder sb = new StringBuilder();
                sb.append("  [note ").append(r.id).append("] rawLen=").append(backHtml == null ? 0 : backHtml.length()).append('\n');
                appendSkeleton(back, sb, "    ", 0, 3);
                skeletons.add(sb.toString());
            }
        }
        out.println("descendant tag presence (notes containing at least one): " + tagPresence);
        out.println("div class presence (top 30): " + topN(divClassPresence, 30));
        out.println("section class presence (top 30): " + topN(sectionClassPresence, 30));
        out.println("other tag.class presence (top 30, candidates for a narrower selector): " + topN(otherClassPresence, 30));
        out.println("has <form>: " + hasForm + "  has <input>: " + hasInput + "  has <ol>: " + hasOl
                + "  has <ul>: " + hasUl + "  has <li>: " + hasLi + "  has <table>: " + hasTable + "  has <ruby>: " + hasRuby);
        out.println("has div._j4z (current explanation selector): " + hasExplDiv + " / " + comprehensive.size());
        out.println("has section._j4a (current example selector): " + hasExampleSection + " / " + comprehensive.size());
        out.println("plain-text choice-like signal (circled digit/A-D)/<li>/'choice'/'option'/'보기'): " + choiceSignal + " / " + comprehensive.size());
        out.println("plain-text answer/explanation-label signal (정답/해설/answer/explanation): " + answerSignal + " / " + comprehensive.size());
        out.println("plain-text blank-marker signal (___/()/（）/□/빈칸/blank): " + blankSignal + " / " + comprehensive.size());
        out.println("plain-text ordering-label signal (순서/배열/order/arrange/나열): " + orderSignal + " / " + comprehensive.size());
        out.println("--- sample structure skeletons (tag/class + <=12-char leaf fragments only, no full copyrighted text) ---");
        skeletons.forEach(out::print);
        out.println();
    }

    // ===== 11. IsSentenceArrangement focused analysis =====
    private static void section11SentenceArrangement(List<Row> all, List<Row> comprehensive, GrammarHtmlParser parser, PrintWriter out) {
        out.println("=== 11. IsSentenceArrangement FOCUSED ANALYSIS ===");
        List<Row> arrangement = all.stream().filter(r -> !blank(raw(r, "IsSentenceArrangement"))).toList();
        out.println("IsSentenceArrangement non-empty notes: " + arrangement.size());
        Map<String, Long> byCategory = new TreeMap<>();
        for (Row r : arrangement) byCategory.merge(r.category, 1L, Long::sum);
        out.println("  category distribution: " + byCategory);
        Map<List<String>, Long> coFlags = new LinkedHashMap<>();
        for (Row r : arrangement) {
            List<String> key = List.of(quote(raw(r, "IsBasic")), quote(raw(r, "IsGrammarForm")), quote(raw(r, "IsPassageBlank")));
            coFlags.merge(key, 1L, Long::sum);
        }
        out.println("  co-occurring (IsBasic,IsGrammarForm,IsPassageBlank) among these notes: " + coFlags);

        List<Row> arrangementComprehensive = arrangement.stream().filter(r -> "COMPREHENSIVE".equals(r.category)).toList();
        List<Row> restComprehensive = comprehensive.stream().filter(r -> blank(raw(r, "IsSentenceArrangement"))).toList();
        out.println("  --- BackHTML signal comparison: SentenceArrangement COMPREHENSIVE (n=" + arrangementComprehensive.size()
                + ") vs rest of COMPREHENSIVE (n=" + restComprehensive.size() + ") ---");
        out.println("  SentenceArrangement subset: " + signalSummary(arrangementComprehensive));
        out.println("  rest of COMPREHENSIVE:      " + signalSummary(restComprehensive));
        out.println();
    }

    private static String signalSummary(List<Row> rows) {
        if (rows.isEmpty()) return "(empty)";
        long choice = 0, answer = 0, blankSig = 0, order = 0, hasOl = 0, hasLi = 0, hasTable = 0;
        for (Row r : rows) {
            String backHtml = raw(r, "BackHTML");
            Element back = Jsoup.parseBodyFragment(backHtml == null ? "" : backHtml).body();
            String plain = back.text();
            if (CHOICE_SIGNAL.matcher(backHtml == null ? "" : backHtml).find() || CHOICE_SIGNAL.matcher(plain).find()) choice++;
            if (ANSWER_SIGNAL.matcher(plain).find()) answer++;
            if (BLANK_SIGNAL.matcher(plain).find()) blankSig++;
            if (ORDER_SIGNAL.matcher(plain).find()) order++;
            Set<String> tags = new HashSet<>();
            for (Element el : back.getAllElements()) tags.add(el.tagName());
            if (tags.contains("ol")) hasOl++;
            if (tags.contains("li")) hasLi++;
            if (tags.contains("table")) hasTable++;
        }
        return String.format("choice=%d answer=%d blank=%d order=%d ol=%d li=%d table=%d (of %d)",
                choice, answer, blankSig, order, hasOl, hasLi, hasTable, rows.size());
    }

    // ===== 12. IsPassageBlank / IsBasic / IsGrammarForm meaning: sentinel vs subtype =====
    private static void section12FlagMeaning(List<Row> rows, PrintWriter out) {
        out.println("=== 12. IsPassageBlank / IsBasic / IsGrammarForm: TEMPLATE-TRIGGER vs SUBTYPE EVIDENCE ===");
        for (String flag : List.of("IsBasic", "IsGrammarForm", "IsPassageBlank")) {
            List<Row> active = rows.stream().filter(r -> !blank(raw(r, flag))).toList();
            List<Row> inactive = rows.stream().filter(r -> blank(raw(r, flag))).toList();
            out.println("--- " + flag + " active=" + active.size() + " inactive=" + inactive.size() + " ---");
            out.println("  active raw values: " + valueCounts(active, flag));
            out.println("  active category distribution: " + categoryCounts(active));
            out.println("  inactive category distribution: " + categoryCounts(inactive));
            out.println("  avg FrontHTML length: active=" + avgLen(active, "FrontHTML") + " inactive=" + avgLen(inactive, "FrontHTML"));
            out.println("  avg BackHTML length: active=" + avgLen(active, "BackHTML") + " inactive=" + avgLen(inactive, "BackHTML"));
        }
        out.println();
    }

    private static Map<String, Long> categoryCounts(List<Row> rows) {
        Map<String, Long> m = new TreeMap<>();
        for (Row r : rows) m.merge(r.category, 1L, Long::sum);
        return m;
    }

    private static String avgLen(List<Row> rows, String field) {
        if (rows.isEmpty()) return "n/a (0 notes)";
        double avg = rows.stream().mapToInt(r -> safeLen(raw(r, field))).average().orElse(0);
        return String.format("%.1f", avg);
    }

    // ===== 13. explanation > 2000 breakdown =====
    private static void section13ExplanationOver2000(List<Row> rows, GrammarHtmlParser parser, PrintWriter out) {
        out.println("=== 13. explanation > 2000 BREAKDOWN ===");
        long grammarStructured = 0, grammarFallback = 0, comprehensiveStructured = 0, comprehensiveFallback = 0;
        long grammarTotal = 0, comprehensiveTotal = 0;
        for (Row r : rows) {
            String backHtml = raw(r, "BackHTML");
            Element back = Jsoup.parseBodyFragment(backHtml == null ? "" : backHtml).body();
            Element explDiv = back.selectFirst("div._j4z");
            boolean fallback = explDiv == null || explDiv.text().trim().isBlank();
            String explanation = fallback ? back.text() : explDiv.text().trim();
            boolean over = explanation != null && explanation.length() > 2000;
            boolean isGrammar = "GRAMMAR".equals(r.category);
            if (isGrammar) grammarTotal++; else comprehensiveTotal++;
            if (over) {
                if (isGrammar) { if (fallback) grammarFallback++; else grammarStructured++; }
                else { if (fallback) comprehensiveFallback++; else comprehensiveStructured++; }
            }
        }
        out.println("GRAMMAR (n=" + grammarTotal + "): >2000 via structured div._j4z=" + grammarStructured
                + "  >2000 via back.text() fallback=" + grammarFallback
                + "  total >2000=" + (grammarStructured + grammarFallback));
        out.println("COMPREHENSIVE (n=" + comprehensiveTotal + "): >2000 via structured div._j4z=" + comprehensiveStructured
                + "  >2000 via back.text() fallback=" + comprehensiveFallback
                + "  total >2000=" + (comprehensiveStructured + comprehensiveFallback));
        out.println();
    }

    // ===== 14. examples distribution =====
    private static void section14Examples(List<Row> normalGrammar, List<Row> comprehensive, GrammarHtmlParser parser, PrintWriter out) {
        out.println("=== 14. EXAMPLES DISTRIBUTION (via current section._j4a selector) ===");
        out.println("GRAMMAR: " + exampleStats(normalGrammar, parser));
        out.println("COMPREHENSIVE: " + exampleStats(comprehensive, parser));
        out.println();
    }

    private static String exampleStats(List<Row> rows, GrammarHtmlParser parser) {
        if (rows.isEmpty()) return "(empty)";
        List<Integer> counts = new ArrayList<>();
        for (Row r : rows) {
            var parsed = parser.parse(raw(r, "FrontHTML"), raw(r, "BackHTML"), raw(r, "Kind"), raw(r, "UnitID"));
            counts.add(parsed.examples().size());
        }
        long zero = counts.stream().filter(c -> c == 0).count();
        return String.format("count=%d min=%d max=%d avg=%.2f zeroExampleNotes=%d", rows.size(),
                counts.stream().mapToInt(Integer::intValue).min().orElse(0),
                counts.stream().mapToInt(Integer::intValue).max().orElse(0),
                counts.stream().mapToInt(Integer::intValue).average().orElse(0), zero);
    }

    // ===== 15. UnitID / identity =====
    private static void section15UnitId(List<Row> rows, PrintWriter out) {
        out.println("=== 15. UnitID / IDENTITY ===");
        long nonBlank = rows.stream().filter(r -> !blank(raw(r, "UnitID"))).count();
        Set<String> distinct = new HashSet<>();
        for (Row r : rows) { String v = raw(r, "UnitID"); if (!blank(v)) distinct.add(v); }
        out.println("UnitID non-blank: " + nonBlank + " / " + rows.size() + "  distinct: " + distinct.size());
        out.println("--- sample UnitID values by category (first 5 each, no invented parsing rule) ---");
        for (String category : List.of("GRAMMAR", "COMPREHENSIVE")) {
            List<String> samples = rows.stream().filter(r -> category.equals(r.category))
                    .map(r -> raw(r, "UnitID")).filter(v -> !blank(v)).distinct().limit(5).toList();
            out.println("  " + category + ": " + samples);
        }
        out.println();
    }

    // ===== 16. JLPT level x category x subtype =====
    private static void section16LevelBySubtype(List<Row> rows, PrintWriter out) {
        out.println("=== 16. JLPT LEVEL x CATEGORY x SUBTYPE ===");
        Map<String, Map<String, Long>> levelByCategory = new TreeMap<>();
        for (Row r : rows) levelByCategory.computeIfAbsent(r.category, k -> new TreeMap<>()).merge(quote(raw(r, "Level")), 1L, Long::sum);
        out.println("category x Level: " + levelByCategory);
        Map<String, Map<String, Long>> levelByArrangement = new TreeMap<>();
        for (Row r : rows) {
            String key = blank(raw(r, "IsSentenceArrangement")) ? "IsSentenceArrangement=empty" : "IsSentenceArrangement=set";
            levelByArrangement.computeIfAbsent(key, k -> new TreeMap<>()).merge(quote(raw(r, "Level")), 1L, Long::sum);
        }
        out.println("IsSentenceArrangement(set/empty) x Level: " + levelByArrangement);
        out.println();
    }

    // ===================================================================================
    // helpers
    // ===================================================================================

    private static String raw(Row r, String field) {
        return r.byName.get(field);
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }

    private static int safeLen(String v) {
        return v == null ? 0 : v.length();
    }

    private static String quote(String v) {
        return blank(v) ? "∅" : "'" + v.trim() + "'";
    }

    private static Map<String, Long> valueCounts(List<Row> rows, String field) {
        Map<String, Long> counts = new TreeMap<>();
        for (Row r : rows) counts.merge(quote(raw(r, field)), 1L, Long::sum);
        return counts;
    }

    private static String stats(List<Integer> lens) {
        if (lens.isEmpty()) return "(no data)";
        int min = lens.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = lens.stream().mapToInt(Integer::intValue).max().orElse(0);
        double avg = lens.stream().mapToInt(Integer::intValue).average().orElse(0);
        return String.format("n=%d min=%d max=%d avg=%.1f", lens.size(), min, max, avg);
    }

    private static Map<Integer, Long> thresholdBuckets(List<Integer> lens) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (int t : LENGTH_THRESHOLDS) {
            long over = lens.stream().filter(l -> l > t).count();
            result.put(t, over);
        }
        return result;
    }

    private static Map<String, Long> topN(Map<String, Long> counts, int n) {
        Map<String, Long> result = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed())
                .limit(n)
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private static void appendSkeleton(Element el, StringBuilder sb, String indent, int depth, int maxDepth) {
        if (depth > maxDepth) {
            if (!el.children().isEmpty()) sb.append(indent).append("...\n");
            return;
        }
        for (Element child : el.children()) {
            String classes = child.classNames().isEmpty() ? "" : "." + String.join(".", new TreeSet<>(child.classNames()));
            sb.append(indent).append(child.tagName()).append(classes);
            String ownText = child.ownText().trim();
            if (!ownText.isBlank()) sb.append("  [\"").append(excerpt(ownText, 12)).append("\"]");
            sb.append('\n');
            appendSkeleton(child, sb, indent + "  ", depth + 1, maxDepth);
        }
    }

    private static String excerpt(String v, int max) {
        if (v == null) return "";
        String clean = v.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    static class Row {
        long id;
        String category;
        String tags;
        List<String> fieldNames;
        Map<String, String> byName;
    }
}
