package com.japanese.content.service;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.japanese.content.importer.ExampleHtmlParser;
import com.japanese.content.importer.GrammarHtmlParser;
import com.japanese.content.importer.GrammarNormalizationParser;
import com.japanese.content.importer.GrammarNormalizationResult;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.PrivateApkgExtractor;
import com.japanese.content.importer.VocabularyNormalizationParser;
import com.japanese.content.importer.VocabularyNormalizationResult;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * JLPT-MAX Ticket 4B step 8/29: reproducible, read-only actual-data profiling tool (test/dev scope
 * only) that measures real dedup/conflict signal population sizes on the v2.1.1 deck <em>before</em>
 * any classification rule is written, so the rule set that {@link NormalizedCandidateConflictAnalyzer}
 * eventually implements is designed against real numbers rather than assumption. Not part of the
 * production build path. Runs only when -Djapanese.actual-apkg=&lt;path&gt; is explicitly supplied
 * (opt-in, same gate/SHA-256 pin as {@code NormalizedCandidateStoreRealApkgReport}), so the general
 * Gradle test suite always passes/skips cleanly without the real ~1.1GB apkg file.
 *
 * <p>This profiles directly over {@link VocabularyNormalizationResult}/{@link GrammarNormalizationResult}
 * (no Spring context, no JPA, no full candidate persistence) rather than through
 * {@link NormalizedCandidateStore}: {@code NormalizedCandidateStore.saveVocabulary}/{@code saveGrammar}
 * map a Result into a {@code NormalizedContentCandidate} snapshot losslessly and deterministically
 * (Ticket 4A), so every field this report groups/compares on (entryId/unitId/expression/reading/
 * pattern/meanings/level/meaningGloss/connection/nuance) has the exact same value in the persisted
 * candidate store. This keeps the profiling run lightweight and avoids the known OOM risk of parsing
 * the real apkg inside a full {@code @SpringBootTest} JVM (see {@code NormalizedCandidateStoreRealApkgReport}).
 */
class NormalizedCandidateConflictProfilingReport {

    private static final String EXPECTED_SHA_256 =
            "9d8be3ff6b23e11ef890a146dffec7ec4649de4bcbd491be439a11b991fd154d";

    @Test
    void ticket4bConflictProfiling() throws Exception {
        String source = System.getProperty("japanese.actual-apkg");
        assumeTrue(source != null && Files.isRegularFile(Path.of(source)),
                "japanese.actual-apkg system property must point at the real JLPT-MAX apkg file");
        String actualSha = sha256(Path.of(source));

        String url = "jdbc:h2:mem:ticket4b_conflict_profiling_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .cleanDisabled(true).load().migrate();
        ObjectMapper json = new ObjectMapper();
        var extractor = new PrivateApkgExtractor(new DriverManagerDataSource(url, "sa", ""), json, new GrammarHtmlParser());
        var summary = extractor.extract(Path.of(source), "ticket4b-conflict-profiling");

        VocabularyNormalizationParser vocabularyParser = new VocabularyNormalizationParser(new ExampleHtmlParser());
        GrammarNormalizationParser grammarParser = new GrammarNormalizationParser();

        List<VocabularyNormalizationResult> vocabulary = new ArrayList<>();
        List<GrammarNormalizationResult> grammar = new ArrayList<>();
        try (Connection db = DriverManager.getConnection(url, "sa", "");
             Statement statement = db.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select source_note_id, category, field_names, field_values from private_apkg_notes "
                             + "where source_ref = 'ticket4b-conflict-profiling' "
                             + "and category in ('VOCABULARY', 'GRAMMAR') order by category, source_note_id")) {
            while (rows.next()) {
                long noteId = rows.getLong(1);
                String category = rows.getString(2);
                Map<String, String> byName = fieldMap(json, rows.getString(3), rows.getString(4));
                if ("VOCABULARY".equals(category)) {
                    vocabulary.add(vocabularyParser.parse("ticket4b-conflict-profiling", noteId, byName));
                } else {
                    grammar.add(grammarParser.parse("ticket4b-conflict-profiling", noteId, category, byName));
                }
            }
        }

        Path reportPath = Path.of("build", "reports", "jlpt-max-profiling", "conflict-analysis-profiling.txt");
        Files.createDirectories(reportPath.getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            out.println("JLPT-MAX Ticket 4B Conflict/Dedup Profiling Report");
            out.println("source file: " + source);
            out.println("source sha-256 (actual): " + actualSha);
            out.println("source sha-256 (expected v2.1.1): " + EXPECTED_SHA_256);
            out.println("canonical v2.1.1: " + actualSha.equals(EXPECTED_SHA_256));
            out.println("extractor summary categories: " + summary.categories());
            out.println();
            reportVocabulary(vocabulary, out);
            out.println();
            reportGrammar(grammar, out);
        }
        System.out.println("TICKET4B_PROFILING_WRITTEN " + reportPath.toAbsolutePath());
        System.out.println("TICKET4B_PROFILING_VOCAB_TOTAL " + vocabulary.size());
        System.out.println("TICKET4B_PROFILING_GRAMMAR_TOTAL " + grammar.size());
    }

    // ===================================================================================
    // Vocabulary
    // ===================================================================================

    private static void reportVocabulary(List<VocabularyNormalizationResult> rows, PrintWriter out) {
        out.println("=== VOCABULARY (n=" + rows.size() + ") ===");

        Map<String, List<VocabularyNormalizationResult>> byEntryId = new LinkedHashMap<>();
        long nonNullEntryId = 0;
        for (var r : rows) {
            String key = normalize(r.entryId());
            if (key == null) continue;
            nonNullEntryId++;
            byEntryId.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        List<List<VocabularyNormalizationResult>> entryIdDupeGroups =
                byEntryId.values().stream().filter(g -> g.size() > 1).toList();
        long entryIdDupeRows = entryIdDupeGroups.stream().mapToLong(List::size).sum();

        long sameEntrySameExprReading = 0, sameEntryDiffExpr = 0, sameEntryDiffReading = 0;
        for (var group : entryIdDupeGroups) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    var a = group.get(i);
                    var b = group.get(j);
                    boolean sameExpr = eq(normalize(a.expression()), normalize(b.expression()));
                    boolean sameReading = eq(normalize(a.reading()), normalize(b.reading()));
                    if (!sameExpr) sameEntryDiffExpr++;
                    else if (!sameReading) sameEntryDiffReading++;
                    else sameEntrySameExprReading++;
                }
            }
        }

        Map<String, List<VocabularyNormalizationResult>> byExpression = new LinkedHashMap<>();
        Map<String, List<VocabularyNormalizationResult>> byReading = new LinkedHashMap<>();
        Map<String, List<VocabularyNormalizationResult>> byExprReading = new LinkedHashMap<>();
        for (var r : rows) {
            String expr = normalize(searchExpr(r));
            String reading = normalize(searchReading(r));
            if (expr != null) byExpression.computeIfAbsent(expr, k -> new ArrayList<>()).add(r);
            if (reading != null) byReading.computeIfAbsent(reading, k -> new ArrayList<>()).add(r);
            if (expr != null && reading != null) {
                byExprReading.computeIfAbsent(expr + " " + reading, k -> new ArrayList<>()).add(r);
            }
        }
        List<List<VocabularyNormalizationResult>> exprGroups =
                byExpression.values().stream().filter(g -> g.size() > 1).toList();
        List<List<VocabularyNormalizationResult>> readingGroups =
                byReading.values().stream().filter(g -> g.size() > 1).toList();
        List<List<VocabularyNormalizationResult>> exprReadingGroups =
                byExprReading.values().stream().filter(g -> g.size() > 1).toList();

        long diffLevel = 0, diffMeanings = 0, diffEntryId = 0, totalExprReadingPairs = 0, fullyIdenticalPairs = 0;
        int largestExprReadingGroup = 0;
        for (var group : exprReadingGroups) {
            largestExprReadingGroup = Math.max(largestExprReadingGroup, group.size());
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    totalExprReadingPairs++;
                    var a = group.get(i);
                    var b = group.get(j);
                    boolean sameLevel = eq(levelCode(a), levelCode(b));
                    boolean sameMeanings = eq(meaningKey(a.meanings()), meaningKey(b.meanings()));
                    if (!sameLevel) diffLevel++;
                    if (!sameMeanings) diffMeanings++;
                    if (!eq(normalize(a.entryId()), normalize(b.entryId()))) diffEntryId++;
                    if (sameLevel && sameMeanings) fullyIdenticalPairs++;
                }
            }
        }

        out.println("total: " + rows.size());
        out.println("non-null EntryID: " + nonNullEntryId);
        out.println("duplicate EntryID groups: " + entryIdDupeGroups.size() + "  rows: " + entryIdDupeRows
                + "  largest group: " + entryIdDupeGroups.stream().mapToInt(List::size).max().orElse(0));
        out.println("  same EntryID pairs, same expression+reading: " + sameEntrySameExprReading);
        out.println("  same EntryID pairs, different expression: " + sameEntryDiffExpr);
        out.println("  same EntryID pairs, different reading (same expression): " + sameEntryDiffReading);
        out.println("duplicate normalized-expression-only groups: " + exprGroups.size());
        out.println("duplicate normalized-reading-only groups: " + readingGroups.size());
        out.println("duplicate normalized expression+reading groups: " + exprReadingGroups.size()
                + "  largest group: " + largestExprReadingGroup + "  total pairs: " + totalExprReadingPairs);
        out.println("  same expression+reading pairs, different level: " + diffLevel);
        out.println("  same expression+reading pairs, different meanings: " + diffMeanings);
        out.println("  same expression+reading pairs, different (or missing) EntryID: " + diffEntryId);
        out.println("  same expression+reading pairs, fully identical (level+meanings): " + fullyIdenticalPairs);
        out.println("--- sample expression+reading duplicate groups (up to 10) ---");
        exprReadingGroups.stream().limit(10).forEach(group -> {
            out.println("  group (" + group.size() + " rows):");
            for (var r : group) {
                out.println("    entryId=" + r.entryId() + " expr=" + searchExpr(r) + " reading=" + searchReading(r)
                        + " level=" + levelCode(r) + " meanings=" + meaningKey(r.meanings()));
            }
        });
    }

    private static String searchExpr(VocabularyNormalizationResult r) {
        return r.normalizedSearchExpression() != null ? r.normalizedSearchExpression() : r.expression();
    }

    private static String searchReading(VocabularyNormalizationResult r) {
        return r.normalizedSearchReading() != null ? r.normalizedSearchReading() : r.reading();
    }

    private static String levelCode(VocabularyNormalizationResult r) {
        return r.level() == null ? null : r.level().code();
    }

    private static String meaningKey(List<NormalizedMeaning> meanings) {
        return meanings.stream().map(NormalizedMeaning::text).map(String::trim).sorted()
                .reduce((a, b) -> a + "|" + b).orElse("");
    }

    // ===================================================================================
    // Grammar
    // ===================================================================================

    private static void reportGrammar(List<GrammarNormalizationResult> rows, PrintWriter out) {
        out.println("=== GRAMMAR (n=" + rows.size() + ") ===");

        Map<String, List<GrammarNormalizationResult>> byUnitId = new LinkedHashMap<>();
        long nonNullUnitId = 0;
        for (var r : rows) {
            String key = normalize(r.unitId());
            if (key == null) continue;
            nonNullUnitId++;
            byUnitId.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        List<List<GrammarNormalizationResult>> unitIdDupeGroups =
                byUnitId.values().stream().filter(g -> g.size() > 1).toList();
        long unitIdDupeRows = unitIdDupeGroups.stream().mapToLong(List::size).sum();

        long sameUnitSamePattern = 0, sameUnitDiffPattern = 0;
        for (var group : unitIdDupeGroups) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    var a = group.get(i);
                    var b = group.get(j);
                    if (eq(normalize(a.pattern()), normalize(b.pattern()))) sameUnitSamePattern++;
                    else sameUnitDiffPattern++;
                }
            }
        }

        Map<String, List<GrammarNormalizationResult>> byPattern = new LinkedHashMap<>();
        for (var r : rows) {
            String key = normalize(r.pattern());
            if (key != null) byPattern.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        List<List<GrammarNormalizationResult>> patternGroups =
                byPattern.values().stream().filter(g -> g.size() > 1).toList();
        int largestPatternGroup = patternGroups.stream().mapToInt(List::size).max().orElse(0);

        Map<String, List<GrammarNormalizationResult>> byPatternLevel = new LinkedHashMap<>();
        for (var r : rows) {
            String pattern = normalize(r.pattern());
            if (pattern == null) continue;
            String level = levelCode(r);
            byPatternLevel.computeIfAbsent(pattern + " " + (level == null ? "" : level), k -> new ArrayList<>())
                    .add(r);
        }
        List<List<GrammarNormalizationResult>> patternLevelGroups =
                byPatternLevel.values().stream().filter(g -> g.size() > 1).toList();

        long patternGroupsWithMultipleLevels = 0;
        for (var group : patternGroups) {
            long distinctLevels = group.stream().map(NormalizedCandidateConflictProfilingReport::levelCode)
                    .map(v -> v == null ? " null" : v).distinct().count();
            if (distinctLevels > 1) patternGroupsWithMultipleLevels++;
        }

        long samePatternDiffUnitId = 0;
        for (var group : patternGroups) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    var a = group.get(i);
                    var b = group.get(j);
                    if (!eq(normalize(a.unitId()), normalize(b.unitId()))) samePatternDiffUnitId++;
                }
            }
        }

        long samePatternLevelDiffGloss = 0, samePatternLevelDiffConnection = 0, samePatternLevelDiffNuance = 0;
        long totalPatternLevelPairs = 0, patternLevelFullyIdentical = 0;
        for (var group : patternLevelGroups) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    totalPatternLevelPairs++;
                    var a = group.get(i);
                    var b = group.get(j);
                    boolean sameGloss = eq(normalize(a.meaningGloss()), normalize(b.meaningGloss()));
                    boolean sameConnection = eq(normalize(a.connection()), normalize(b.connection()));
                    boolean sameNuance = eq(normalize(a.nuance()), normalize(b.nuance()));
                    if (!sameGloss) samePatternLevelDiffGloss++;
                    if (!sameConnection) samePatternLevelDiffConnection++;
                    if (!sameNuance) samePatternLevelDiffNuance++;
                    if (sameGloss && sameConnection && sameNuance) patternLevelFullyIdentical++;
                }
            }
        }

        out.println("total: " + rows.size());
        out.println("non-null UnitID: " + nonNullUnitId);
        out.println("duplicate UnitID groups: " + unitIdDupeGroups.size() + "  rows: " + unitIdDupeRows
                + "  largest group: " + unitIdDupeGroups.stream().mapToInt(List::size).max().orElse(0));
        out.println("  same UnitID pairs, same pattern: " + sameUnitSamePattern);
        out.println("  same UnitID pairs, different pattern: " + sameUnitDiffPattern);
        out.println("duplicate normalized pattern groups: " + patternGroups.size()
                + "  largest group: " + largestPatternGroup);
        out.println("  pattern groups containing more than one distinct level: " + patternGroupsWithMultipleLevels);
        out.println("  same-pattern pairs, different UnitID: " + samePatternDiffUnitId);
        out.println("pattern+level groups: " + patternLevelGroups.size() + "  total pairs: " + totalPatternLevelPairs);
        out.println("  same pattern+level pairs, different meaningGloss: " + samePatternLevelDiffGloss);
        out.println("  same pattern+level pairs, different connection: " + samePatternLevelDiffConnection);
        out.println("  same pattern+level pairs, different nuance: " + samePatternLevelDiffNuance);
        out.println("  same pattern+level pairs, fully identical (gloss+connection+nuance): " + patternLevelFullyIdentical);
        out.println("--- sample duplicate pattern groups (up to 10) ---");
        patternGroups.stream().limit(10).forEach(group -> {
            out.println("  group (" + group.size() + " rows):");
            for (var r : group) {
                out.println("    unitId=" + r.unitId() + " pattern=" + normalize(r.pattern()) + " level=" + levelCode(r)
                        + " gloss=" + excerpt(normalize(r.meaningGloss())));
            }
        });
    }

    private static String excerpt(String v) {
        if (v == null) return "null";
        String clean = v.replaceAll("\\s+", " ");
        return clean.length() <= 60 ? clean : clean.substring(0, 60) + "...";
    }

    private static String levelCode(GrammarNormalizationResult r) {
        return r.level() == null ? null : r.level().code();
    }

    // ===================================================================================
    // helpers
    // ===================================================================================

    private static String normalize(String v) {
        if (v == null) return null;
        String trimmed = java.text.Normalizer.normalize(v, java.text.Normalizer.Form.NFKC).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fieldMap(ObjectMapper json, String fieldNamesJson, String fieldValuesJson) {
        List<Object> fieldNames = json.readValue(fieldNamesJson, List.class);
        List<Object> fieldValues = json.readValue(fieldValuesJson, List.class);
        Map<String, String> byName = new LinkedHashMap<>();
        for (int i = 0; i < fieldNames.size(); i++) {
            byName.put((String) fieldNames.get(i), i < fieldValues.size() ? (String) fieldValues.get(i) : "");
        }
        return byName;
    }

    private String sha256(Path file) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 20];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
