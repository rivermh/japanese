package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.japanese.content.dto.PromotionReadinessModels.OverallStatus;
import com.japanese.content.dto.PromotionReadinessModels.ReadinessSummary;
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
 * JLPT-MAX Ticket 4D: opt-in verification tool (test/dev scope only) that runs the full real
 * chain - extract -&gt; normalize -&gt; {@link NormalizedCandidateStore} (4A) -&gt;
 * {@link NormalizedCandidateConflictAnalyzer} (4B) -&gt;
 * {@link NormalizedCandidatePromotionReadinessService} (this ticket) - against the actual v2.1.1
 * JLPT-MAX deck and reports the real promotion-readiness breakdown. Not part of the production build
 * path. Runs only when {@code -Djapanese.actual-apkg=<path>} is explicitly supplied (opt-in, same
 * SHA-256-pinned gate as {@code NormalizedCandidateStoreRealApkgReport}/
 * {@code NormalizedCandidateConflictAnalyzerRealApkgReport}), in its own fresh isolated in-memory H2
 * database, and as its own separate Gradle invocation.
 *
 * <p>No Ticket 4C human decision is submitted here - this reports the real, currently-unreviewed
 * state of the actual deck. No {@code ContentSource} row is registered either (this ticket never
 * auto-registers or auto-allows the JLPT-MAX source), so every candidate is expected to show
 * {@code SOURCE_NOT_REGISTERED} - that is a correct, expected finding, not a defect.
 */
@SpringBootTest
@ActiveProfiles("sample")
class NormalizedCandidatePromotionReadinessRealApkgReport {

    private static final String URL = "jdbc:h2:mem:promotion_readiness_real_apkg_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String EXPECTED_SHA_256 =
            "9d8be3ff6b23e11ef890a146dffec7ec4649de4bcbd491be439a11b991fd154d";
    private static final String SOURCE_REF = "ticket4d-promotion-readiness";

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
    }

    @Autowired DataSource dataSource;
    @Autowired PrivateApkgExtractor extractor;
    @Autowired VocabularyNormalizationParser vocabularyParser;
    @Autowired GrammarNormalizationParser grammarParser;
    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired NormalizedCandidatePromotionReadinessService readiness;

    @Test
    void ticket4dPromotionReadinessAgainstRealApkg() throws Exception {
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

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, SOURCE_REF);
        analyzer.analyze(NormalizedCandidateType.GRAMMAR, SOURCE_REF);

        ReadinessSummary vocabulary = readiness.summary(NormalizedCandidateType.VOCABULARY, SOURCE_REF);
        ReadinessSummary grammar = readiness.summary(NormalizedCandidateType.GRAMMAR, SOURCE_REF);

        // Idempotency: computing the summary twice over the same unchanged snapshot must be identical.
        assertThat(readiness.summary(NormalizedCandidateType.VOCABULARY, SOURCE_REF)).isEqualTo(vocabulary);
        assertThat(readiness.summary(NormalizedCandidateType.GRAMMAR, SOURCE_REF)).isEqualTo(grammar);

        Path reportPath = Path.of("build", "reports", "jlpt-max-profiling", "promotion-readiness-actual.txt");
        Files.createDirectories(reportPath.getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            out.println("JLPT-MAX Ticket 4D Actual Promotion Readiness Report (v2.1.1)");
            out.println("source file: " + source);
            out.println("source sha-256: " + EXPECTED_SHA_256);
            out.println("NOTE: no ContentSource row is registered for this scope, so SOURCE_NOT_REGISTERED");
            out.println("      is expected on every candidate - this ticket never auto-registers or");
            out.println("      auto-allows the JLPT-MAX source's rights status.");
            out.println();
            printSummary("VOCABULARY", vocabulary, out);
            out.println();
            printSummary("GRAMMAR", grammar, out);
        }

        System.out.println("TICKET4D_ACTUAL_VOCAB_SUMMARY " + vocabulary);
        System.out.println("TICKET4D_ACTUAL_GRAMMAR_SUMMARY " + grammar);

        // Every real candidate today carries PRODUCTION_IDENTITY_POLICY_UNRESOLVED and
        // SOURCE_NOT_REGISTERED unconditionally (this ticket never resolves either), so
        // READY_FOR_DRAFT_PROMOTION is expected to be exactly zero against the real deck - a
        // structural finding of this ticket, not a bug to fix by relaxing either policy.
        assertThat(vocabulary.readyForDraftPromotion()).isZero();
        assertThat(grammar.readyForDraftPromotion()).isZero();
        assertThat(vocabulary.blockedByIssueCode().get("PRODUCTION_IDENTITY_POLICY_UNRESOLVED"))
                .isEqualTo(vocabulary.totalCandidates());
        assertThat(grammar.blockedByIssueCode().get("PRODUCTION_IDENTITY_POLICY_UNRESOLVED"))
                .isEqualTo(grammar.totalCandidates());
        assertThat(grammar.blockedByIssueCode().get("GRAMMAR_MAPPING_POLICY_UNRESOLVED"))
                .isEqualTo(grammar.totalCandidates());
        assertThat(vocabulary.totalCandidates()).isEqualTo(9160);
        assertThat(grammar.totalCandidates()).isEqualTo(1078);
    }

    private void printSummary(String label, ReadinessSummary summary, PrintWriter out) {
        out.println("=== " + label + " ===");
        out.println("total candidates: " + summary.totalCandidates());
        out.println("READY_FOR_DRAFT_PROMOTION: " + summary.readyForDraftPromotion());
        out.println("BLOCKED: " + summary.blocked());
        out.println("ALREADY_PROMOTED: " + summary.alreadyPromoted());
        out.println("--- quality breakdown ---");
        summary.qualityBreakdown().forEach((k, v) -> out.println("  " + k + ": " + v));
        out.println("--- pair resolution breakdown ---");
        summary.pairResolutionBreakdown().forEach((k, v) -> out.println("  " + k + ": " + v));
        out.println("--- mapping breakdown ---");
        summary.mappingBreakdown().forEach((k, v) -> out.println("  " + k + ": " + v));
        out.println("--- source rights breakdown ---");
        summary.sourceRightsBreakdown().forEach((k, v) -> out.println("  " + k + ": " + v));
        out.println("--- candidates by blocker count ---");
        summary.candidatesByBlockerCountBreakdown().forEach((k, v) -> out.println("  " + k + " blocker(s): " + v));
        out.println("--- blocker code counts (0 omitted) ---");
        summary.blockedByIssueCode().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .forEach(e -> out.println("  " + e.getKey() + ": " + e.getValue()));
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
