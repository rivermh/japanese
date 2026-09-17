package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.importer.GrammarNormalizationParser;
import com.japanese.content.importer.PrivateApkgExtractor;
import com.japanese.content.importer.VocabularyNormalizationParser;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.EnumMap;
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
 * Ticket 4A verification tool (test/dev scope only): runs every real VOCABULARY and
 * category=GRAMMAR note through the normalization parsers and then through
 * {@link NormalizedCandidateStore}, reporting how many candidates of each quality state were
 * persisted. Not part of the production build path. Runs only when
 * -Djapanese.actual-apkg=&lt;path&gt; is explicitly supplied (opt-in, same gate as
 * {@code VocabularyNormalizationRealApkgReport}/{@code GrammarNormalizationRealApkgReport}), so the
 * general Gradle test suite always passes/skips cleanly without the real ~1.1GB apkg file. Unlike
 * those two (which run parsing only, with no Spring context), this tool exercises real JPA
 * persistence, so it boots a full {@code @SpringBootTest} context against its own fresh, isolated
 * in-memory H2 database - never a shared or production database. Run it as its own separate Gradle
 * invocation (not alongside the full test suite) to avoid this environment's known OOM when a real
 * apkg parse runs in the same JVM as the rest of the suite.
 */
@SpringBootTest
@ActiveProfiles("sample")
class NormalizedCandidateStoreRealApkgReport {

    private static final String URL = "jdbc:h2:mem:normalized_candidate_real_apkg_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String EXPECTED_SHA_256 =
            "9d8be3ff6b23e11ef890a146dffec7ec4649de4bcbd491be439a11b991fd154d";

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
    NormalizedContentCandidateRepository repository;

    @Test
    void ticket4aCandidatePersistenceAgainstRealApkg() throws Exception {
        String source = System.getProperty("japanese.actual-apkg");
        assumeTrue(source != null && Files.isRegularFile(Path.of(source)),
                "japanese.actual-apkg system property must point at the real JLPT-MAX apkg file");
        assertThat(sha256(Path.of(source))).isEqualTo(EXPECTED_SHA_256);

        ObjectMapper json = new ObjectMapper();
        var summary = extractor.extract(Path.of(source), "ticket4a-candidate-persistence");

        long vocabularyTotal = 0;
        Map<NormalizedCandidateQualityState, Long> vocabularyQuality = new EnumMap<>(NormalizedCandidateQualityState.class);
        long grammarTotal = 0;
        Map<NormalizedCandidateQualityState, Long> grammarQuality = new EnumMap<>(NormalizedCandidateQualityState.class);

        try (Connection db = dataSource.getConnection();
                Statement statement = db.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select source_note_id, category, field_names, field_values from private_apkg_notes "
                                + "where source_ref = 'ticket4a-candidate-persistence' "
                                + "and category in ('VOCABULARY', 'GRAMMAR') order by category, source_note_id")) {
            while (rows.next()) {
                long noteId = rows.getLong(1);
                String category = rows.getString(2);
                Map<String, String> byName = fieldMap(json, rows.getString(3), rows.getString(4));
                if ("VOCABULARY".equals(category)) {
                    var result = vocabularyParser.parse("ticket4a-candidate-persistence", noteId, byName);
                    var saved = store.saveVocabulary(result);
                    vocabularyTotal++;
                    vocabularyQuality.merge(saved.getQualityState(), 1L, Long::sum);
                } else {
                    var result = grammarParser.parse("ticket4a-candidate-persistence", noteId, category, byName);
                    var saved = store.saveGrammar(result);
                    grammarTotal++;
                    grammarQuality.merge(saved.getQualityState(), 1L, Long::sum);
                }
            }
        }

        long persistedVocabulary = repository.findByCandidateType(NormalizedCandidateType.VOCABULARY).size();
        long persistedGrammar = repository.findByCandidateType(NormalizedCandidateType.GRAMMAR).size();

        Path reportPath = Path.of("build", "reports", "jlpt-max-profiling", "normalized-candidate-persistence.txt");
        Files.createDirectories(reportPath.getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            out.println("JLPT-MAX Ticket 4A Normalized Candidate Persistence Report");
            out.println("source file: " + source);
            out.println("source sha-256: " + EXPECTED_SHA_256);
            out.println("extractor summary categories: " + summary.categories());
            out.println();
            out.println("vocabulary notes parsed and saved: " + vocabularyTotal);
            out.println("vocabulary candidates persisted (repository count): " + persistedVocabulary);
            out.println("vocabulary quality state counts:");
            for (NormalizedCandidateQualityState state : NormalizedCandidateQualityState.values()) {
                out.printf("  %-16s %6d%n", state, vocabularyQuality.getOrDefault(state, 0L));
            }
            out.println();
            out.println("grammar notes parsed and saved: " + grammarTotal);
            out.println("grammar candidates persisted (repository count): " + persistedGrammar);
            out.println("grammar quality state counts:");
            for (NormalizedCandidateQualityState state : NormalizedCandidateQualityState.values()) {
                out.printf("  %-16s %6d%n", state, grammarQuality.getOrDefault(state, 0L));
            }
            out.println();
            out.println("combined total: " + (vocabularyTotal + grammarTotal));
        }
        System.out.println("TICKET4A_CANDIDATE_VOCABULARY_TOTAL " + vocabularyTotal);
        System.out.println("TICKET4A_CANDIDATE_VOCABULARY_PERSISTED " + persistedVocabulary);
        vocabularyQuality.forEach((state, count) -> System.out.println("TICKET4A_CANDIDATE_VOCABULARY_QUALITY " + state + " " + count));
        System.out.println("TICKET4A_CANDIDATE_GRAMMAR_TOTAL " + grammarTotal);
        System.out.println("TICKET4A_CANDIDATE_GRAMMAR_PERSISTED " + persistedGrammar);
        grammarQuality.forEach((state, count) -> System.out.println("TICKET4A_CANDIDATE_GRAMMAR_QUALITY " + state + " " + count));
        System.out.println("TICKET4A_CANDIDATE_COMBINED_TOTAL " + (vocabularyTotal + grammarTotal));

        assertThat(vocabularyTotal).isEqualTo(summary.categories().getOrDefault("VOCABULARY", 0L));
        assertThat(grammarTotal).isEqualTo(summary.categories().getOrDefault("GRAMMAR", 0L));
        assertThat(persistedVocabulary).isEqualTo(vocabularyTotal);
        assertThat(persistedGrammar).isEqualTo(grammarTotal);

        // Known-good absolute counts for JLPT-MAX-Deck v2.1.1 (pinned to EXPECTED_SHA_256 above);
        // this test is an opt-in regression check against that exact deck, not a generic contract.
        assertThat(vocabularyTotal).isEqualTo(9160L);
        assertThat(grammarTotal).isEqualTo(1078L);
        assertThat(vocabularyTotal + grammarTotal).isEqualTo(10238L);
        assertThat(vocabularyQuality.getOrDefault(NormalizedCandidateQualityState.FATAL, 0L)).isEqualTo(1L);
        assertThat(grammarQuality.getOrDefault(NormalizedCandidateQualityState.FATAL, 0L)).isEqualTo(0L);
        assertThat(grammarQuality.getOrDefault(NormalizedCandidateQualityState.REVIEW_REQUIRED, 0L)).isEqualTo(0L);
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
