package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.japanese.content.entity.NormalizedCandidateMatchAssessment;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.importer.GrammarNormalizationParser;
import com.japanese.content.importer.PrivateApkgExtractor;
import com.japanese.content.importer.VocabularyNormalizationParser;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * JLPT-MAX Ticket 4B step 23/29: opt-in verification tool (test/dev scope only) that runs the full
 * real chain - extract -&gt; normalize -&gt; {@link NormalizedCandidateStore} (Ticket 4A) -&gt;
 * {@link NormalizedCandidateConflictAnalyzer} (this ticket) - against the actual v2.1.1 JLPT-MAX
 * deck and reports the final persisted pair/assessment counts. Not part of the production build
 * path. Runs only when -Djapanese.actual-apkg=&lt;path&gt; is explicitly supplied (opt-in, same
 * SHA-256-pinned gate as {@code NormalizedCandidateStoreRealApkgReport}), in its own fresh isolated
 * in-memory H2 database, and as its own separate Gradle invocation to avoid the known OOM risk of a
 * real apkg parse sharing a JVM with the rest of the suite.
 */
@SpringBootTest
@ActiveProfiles("sample")
class NormalizedCandidateConflictAnalyzerRealApkgReport {

    private static final String URL = "jdbc:h2:mem:conflict_analyzer_real_apkg_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String EXPECTED_SHA_256 =
            "9d8be3ff6b23e11ef890a146dffec7ec4649de4bcbd491be439a11b991fd154d";
    private static final String SOURCE_REF = "ticket4b-conflict-analysis";

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
    }

    @Autowired
    DataSource dataSource;
    @Autowired
    PrivateApkgExtractor extractor;
    @Autowired
    VocabularyNormalizationParser vocabularyParser;
    @Autowired
    GrammarNormalizationParser grammarParser;
    @Autowired
    NormalizedCandidateStore store;
    @Autowired
    NormalizedCandidateConflictAnalyzer analyzer;

    @Test
    void ticket4bConflictAnalysisAgainstRealApkg() throws Exception {
        String source = System.getProperty("japanese.actual-apkg");
        assumeTrue(source != null && Files.isRegularFile(Path.of(source)),
                "japanese.actual-apkg system property must point at the real JLPT-MAX apkg file");
        assertThat(sha256(Path.of(source))).isEqualTo(EXPECTED_SHA_256);

        ObjectMapper json = new ObjectMapper();
        extractor.extract(Path.of(source), SOURCE_REF);

        try (Connection db = dataSource.getConnection();
                Statement statement = db.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select source_note_id, category, field_names, field_values from private_apkg_notes "
                                + "where source_ref = '" + SOURCE_REF + "' "
                                + "and category in ('VOCABULARY', 'GRAMMAR') order by category, source_note_id")) {
            while (rows.next()) {
                long noteId = rows.getLong(1);
                String category = rows.getString(2);
                Map<String, String> byName = fieldMap(json, rows.getString(3), rows.getString(4));
                if ("VOCABULARY".equals(category)) {
                    store.saveVocabulary(vocabularyParser.parse(SOURCE_REF, noteId, byName));
                } else {
                    store.saveGrammar(grammarParser.parse(SOURCE_REF, noteId, category, byName));
                }
            }
        }

        NormalizedCandidateAnalysisSummary vocabularyFirstRun = analyzer.analyze(NormalizedCandidateType.VOCABULARY, SOURCE_REF);
        NormalizedCandidateAnalysisSummary grammarFirstRun = analyzer.analyze(NormalizedCandidateType.GRAMMAR, SOURCE_REF);
        NormalizedCandidateAnalysisSummary vocabularySecondRun = analyzer.analyze(NormalizedCandidateType.VOCABULARY, SOURCE_REF);
        NormalizedCandidateAnalysisSummary grammarSecondRun = analyzer.analyze(NormalizedCandidateType.GRAMMAR, SOURCE_REF);

        assertThat(vocabularySecondRun).isEqualTo(vocabularyFirstRun);
        assertThat(grammarSecondRun).isEqualTo(grammarFirstRun);

        // Reported/cross-checked via plain SQL (not the JPA repository) so this doesn't need to
        // keep a Hibernate session open across the report-writing code below.
        try (Connection db = dataSource.getConnection()) {
            List<PairRow> vocabularyPairs = loadPairs(db, NormalizedCandidateType.VOCABULARY);
            List<PairRow> grammarPairs = loadPairs(db, NormalizedCandidateType.GRAMMAR);
            assertThat(vocabularyPairs).hasSize(vocabularyFirstRun.pairCount());
            assertThat(grammarPairs).hasSize(grammarFirstRun.pairCount());

            Path reportPath = Path.of("build", "reports", "jlpt-max-profiling", "conflict-analysis-actual.txt");
            Files.createDirectories(reportPath.getParent());
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
                out.println("JLPT-MAX Ticket 4B Actual Conflict Analysis Report (v2.1.1)");
                out.println("source file: " + source);
                out.println("source sha-256: " + EXPECTED_SHA_256);
                out.println();
                printSummary("VOCABULARY", vocabularyFirstRun, vocabularyPairs, db, out);
                out.println();
                printSummary("GRAMMAR", grammarFirstRun, grammarPairs, db, out);
            }
        }
        System.out.println("TICKET4B_ACTUAL_VOCAB_SUMMARY " + vocabularyFirstRun);
        System.out.println("TICKET4B_ACTUAL_GRAMMAR_SUMMARY " + grammarFirstRun);

        // Independent-review follow-up (item 9): pin the full known-good v2.1.1 classification
        // breakdown, not just candidateCount/idempotency - a regression that silently reclassifies a
        // pair (e.g. the new partOfSpeech secondary comparison, item 7A) must fail this assertion
        // loudly instead of only showing up as a diff buried in the printed report.
        assertThat(vocabularyFirstRun.candidateCount()).isEqualTo(9160);
        assertThat(vocabularyFirstRun.uniqueCount()).isEqualTo(9158);
        assertThat(vocabularyFirstRun.exactDuplicateCount()).isEqualTo(0);
        assertThat(vocabularyFirstRun.possibleDuplicateCount()).isEqualTo(1);
        assertThat(vocabularyFirstRun.conflictCount()).isEqualTo(0);
        assertThat(vocabularyFirstRun.pairCount()).isEqualTo(1);

        assertThat(grammarFirstRun.candidateCount()).isEqualTo(1078);
        assertThat(grammarFirstRun.uniqueCount()).isEqualTo(1052);
        assertThat(grammarFirstRun.exactDuplicateCount()).isEqualTo(0);
        assertThat(grammarFirstRun.possibleDuplicateCount()).isEqualTo(32);
        assertThat(grammarFirstRun.conflictCount()).isEqualTo(0);
        assertThat(grammarFirstRun.pairCount()).isEqualTo(32);
    }

    private List<PairRow> loadPairs(Connection db, NormalizedCandidateType type) throws Exception {
        List<PairRow> pairs = new java.util.ArrayList<>();
        try (var statement = db.prepareStatement(
                "select p.id, p.left_candidate_id, p.right_candidate_id, p.assessment "
                        + "from normalized_candidate_match_pairs p "
                        + "join normalized_content_candidates c on c.id = p.left_candidate_id "
                        + "where c.candidate_type = ? and c.source_ref = ? order by p.id")) {
            statement.setString(1, type.name());
            statement.setString(2, SOURCE_REF);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    pairs.add(new PairRow(rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getString(4)));
                }
            }
        }
        return pairs;
    }

    private void printSummary(String label, NormalizedCandidateAnalysisSummary summary, List<PairRow> pairs,
            Connection db, PrintWriter out) throws Exception {
        out.println("=== " + label + " ===");
        out.println("candidates: " + summary.candidateCount());
        out.println("unique (no pair row): " + summary.uniqueCount());
        out.println("exact duplicate pairs: " + summary.exactDuplicateCount());
        out.println("possible duplicate pairs: " + summary.possibleDuplicateCount());
        out.println("conflict pairs: " + summary.conflictCount());
        out.println("total pairs persisted: " + summary.pairCount());
        out.println("--- pairs (up to 50) ---");
        long assessedExact = 0;
        for (PairRow pair : pairs) {
            out.println("  " + pair.leftId + " <-> " + pair.rightId + " : " + pair.assessment);
            if (NormalizedCandidateMatchAssessment.EXACT_DUPLICATE.name().equals(pair.assessment)) {
                assessedExact++;
            }
            try (var statement = db.prepareStatement(
                    "select evidence_code, field_name, detail from normalized_candidate_match_evidence "
                            + "where pair_id = ? order by position")) {
                statement.setLong(1, pair.id);
                try (ResultSet evidence = statement.executeQuery()) {
                    while (evidence.next()) {
                        out.println("      " + evidence.getString(1)
                                + (evidence.getString(2) == null ? "" : " (" + evidence.getString(2) + ")")
                                + (evidence.getString(3) == null ? "" : " - " + evidence.getString(3)));
                    }
                }
            }
        }
        out.println("(cross-check) exact duplicate rows fetched: " + assessedExact);
    }

    private record PairRow(long id, long leftId, long rightId, String assessment) {
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fieldMap(ObjectMapper json, String fieldNamesJson, String fieldValuesJson) {
        List<Object> fieldNames = json.readValue(fieldNamesJson, List.class);
        List<Object> fieldValues = json.readValue(fieldValuesJson, List.class);
        Map<String, String> byName = new LinkedHashMap<>();
        for (int i = 0; i < fieldNames.size(); i++) {
            byName.put((String) fieldNames.get(i), i < fieldValues.size() ? (String) fieldValues.get(i) : "");
        }
        return byName;
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
