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
 * JLPT-MAX Ticket 4D (real-APKG semantics re-measured for Ticket 4E-0): opt-in verification tool
 * (test/dev scope only) that runs the full real chain - extract -&gt; normalize -&gt;
 * {@link NormalizedCandidateStore} (4A) -&gt; {@link NormalizedCandidateConflictAnalyzer} (4B) -&gt;
 * {@link NormalizedCandidatePromotionReadinessService} (4D/4E-0) - against the actual v2.1.1
 * JLPT-MAX deck and reports the real promotion-readiness breakdown. Not part of the production build
 * path. Runs only when {@code -Djapanese.actual-apkg=<path>} is explicitly supplied (opt-in, same
 * SHA-256-pinned gate as {@code NormalizedCandidateStoreRealApkgReport}/
 * {@code NormalizedCandidateConflictAnalyzerRealApkgReport}), in its own fresh isolated in-memory H2
 * database, and as its own separate Gradle invocation.
 *
 * <p>{@code SOURCE_REF} is the exact canonical JLPT-MAX Vocabulary sourceRef literal ({@code
 * ApkgVocabularyImporter.SOURCE_REF} / {@code NormalizedVocabularyMeaningLanguagePolicy}'s literal) -
 * this real file's true, source-native identity, not a synthetic placeholder. Ticket 4D originally used
 * a synthetic {@code "ticket4d-promotion-readiness"} ref here specifically to stay clear of {@code
 * ContentSourceCatalog}'s canonical-source seeding; Ticket 4E-0 makes that avoidance actively
 * misleading, since {@code NormalizedVocabularyMeaningLanguagePolicy} only ever resolves against this
 * exact literal - measuring under any other sourceRef would make the Vocabulary meaning-language axis
 * permanently unresolved against the real deck regardless of how the policy actually behaves for it.
 *
 * <p>No Ticket 4C human decision is submitted here - this reports the real, currently-unreviewed
 * state of the actual deck. {@code ContentSourceCatalog} ("sample" profile, already active here) does
 * seed a {@code ContentSource} row for this exact sourceRef at context startup, with {@code
 * rightsStatus = UNKNOWN} - this test never calls {@code ContentSourceRightsService.reviewRights} or
 * otherwise mutates it, so every candidate is expected to show {@code SOURCE_RIGHTS_MANUAL_REVIEW}
 * (the source is registered, just not yet rights-cleared) rather than the old {@code
 * SOURCE_NOT_REGISTERED} - that is a correct, expected finding, not a defect and not a rights-check
 * weakening.
 */
@SpringBootTest
@ActiveProfiles("sample")
class NormalizedCandidatePromotionReadinessRealApkgReport {

    private static final String URL = "jdbc:h2:mem:promotion_readiness_real_apkg_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String EXPECTED_SHA_256 =
            "9d8be3ff6b23e11ef890a146dffec7ec4649de4bcbd491be439a11b991fd154d";
    /** Matches {@code ApkgVocabularyImporter.SOURCE_REF} / {@code NormalizedVocabularyMeaningLanguagePolicy}. */
    private static final String SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";

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
            out.println("JLPT-MAX Ticket 4D/4E-0 Actual Promotion Readiness Report (v2.1.1)");
            out.println("source file: " + source);
            out.println("source sha-256: " + EXPECTED_SHA_256);
            out.println("source ref: " + SOURCE_REF + " (canonical - matches ApkgVocabularyImporter.SOURCE_REF)");
            out.println("NOTE: ContentSourceCatalog (\"sample\" profile) seeds this exact sourceRef's");
            out.println("      ContentSource with rightsStatus=UNKNOWN at startup; this test never mutates");
            out.println("      it, so SOURCE_RIGHTS_MANUAL_REVIEW is expected on every candidate - this");
            out.println("      ticket never auto-clears the JLPT-MAX source's rights status.");
            out.println();
            printSummary("VOCABULARY", vocabulary, out);
            out.println();
            printSummary("GRAMMAR", grammar, out);
        }

        System.out.println("TICKET4D_ACTUAL_VOCAB_SUMMARY " + vocabulary);
        System.out.println("TICKET4D_ACTUAL_GRAMMAR_SUMMARY " + grammar);

        // Ticket 4E-0 structurally changed this axis against the real deck (measured under this exact
        // canonical sourceRef, not preserved from the old Ticket 4D synthetic-sourceRef numbers):
        //  - identity is resolved for both real candidate types (ProductionContentSlugPolicy supports
        //    VOCABULARY/GRAMMAR unconditionally) - PRODUCTION_IDENTITY_POLICY_UNRESOLVED no longer
        //    appears on any real candidate.
        //  - this is the exact canonical sourceRef, so NormalizedVocabularyMeaningLanguagePolicy
        //    resolves "ko" for every real Vocabulary candidate - VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED
        //    no longer appears either.
        //  - the source is registered (ContentSourceCatalog seeds it), just not rights-cleared
        //    (rightsStatus stays UNKNOWN - this test never mutates it), so every candidate is blocked by
        //    SOURCE_RIGHTS_MANUAL_REVIEW instead of the old SOURCE_NOT_REGISTERED.
        //  - Grammar mapping is now ratified (Ticket 4E-8: Grammar.explanation = meaningGloss +
        //    "\n\n" + nuance) - GRAMMAR_MAPPING_POLICY_UNRESOLVED no longer exists; only
        //    GRAMMAR_EXPLANATION_TOO_LONG remains, and is expected to be zero against the real deck
        //    (MISSING_MEANING_GLOSS/MISSING_NUANCE are FATAL, so every non-fatal real candidate has
        //    both fields non-blank, and no real v2.1.1 note's composed length has been observed to
        //    exceed the 2000-char column limit - unverified by this change against the actual file;
        //    see this ticket's final report).
        //  - READY_FOR_DRAFT_PROMOTION is therefore still zero against the real deck - not because
        //    identity, Vocabulary meaning-language, or Grammar mapping are unresolved (they are now all
        //    resolved), but because rights are never auto-cleared here.
        assertThat(vocabulary.readyForDraftPromotion()).isZero();
        assertThat(grammar.readyForDraftPromotion()).isZero();
        assertThat(vocabulary.blockedByIssueCode().get("PRODUCTION_IDENTITY_POLICY_UNRESOLVED")).isZero();
        assertThat(grammar.blockedByIssueCode().get("PRODUCTION_IDENTITY_POLICY_UNRESOLVED")).isZero();
        assertThat(vocabulary.blockedByIssueCode().get("VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED")).isZero();
        assertThat(vocabulary.blockedByIssueCode().get("SOURCE_NOT_REGISTERED")).isZero();
        assertThat(grammar.blockedByIssueCode().get("SOURCE_NOT_REGISTERED")).isZero();
        assertThat(vocabulary.blockedByIssueCode().get("SOURCE_RIGHTS_MANUAL_REVIEW"))
                .isEqualTo(vocabulary.totalCandidates());
        assertThat(grammar.blockedByIssueCode().get("SOURCE_RIGHTS_MANUAL_REVIEW"))
                .isEqualTo(grammar.totalCandidates());
        assertThat(grammar.blockedByIssueCode().get("GRAMMAR_EXPLANATION_TOO_LONG")).isZero();
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
