package com.japanese.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Reproducible, read-only Ticket 2 verification tool (test/dev scope only): runs every real
 * VOCABULARY note through {@link VocabularyNormalizationParser} and reports outcome/warning
 * counts. Not part of the production build path. Runs only when
 * -Djapanese.actual-apkg=&lt;path&gt; is explicitly supplied (opt-in, same gate as
 * {@link PrivateApkgExtractorTest#optionalActualApkgRunsOnlyInFreshH2Staging()} and
 * {@link JlptMaxStagingProfilingReport}), so the general Gradle test suite (and CI without the
 * real ~1.1GB apkg file) always passes/skips cleanly. Uses only an isolated in-memory H2 (never a
 * production DB) and never opens any ZIP media entry or audio binary - it only calls
 * {@link PrivateApkgExtractor}, which already enforces that, and then a pure in-memory parser.
 */
class VocabularyNormalizationRealApkgReport {

    @Test
    void ticket2VocabularyNormalizationAgainstRealApkg() throws Exception {
        String source = System.getProperty("japanese.actual-apkg");
        assumeTrue(source != null && Files.isRegularFile(Path.of(source)),
                "japanese.actual-apkg system property must point at the real JLPT-MAX apkg file");

        String url = "jdbc:h2:mem:ticket2_vocab_normalization_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .cleanDisabled(true).load().migrate();
        ObjectMapper json = new ObjectMapper();
        var extractor = new PrivateApkgExtractor(new DriverManagerDataSource(url, "sa", ""), json, new GrammarHtmlParser());
        var summary = extractor.extract(Path.of(source), "ticket2-vocab-normalization");
        VocabularyNormalizationParser parser = new VocabularyNormalizationParser(new ExampleHtmlParser());

        long total = 0;
        long invalid = 0;
        Map<VocabularyNormalizationIssue, Long> warningCounts = new EnumMap<>(VocabularyNormalizationIssue.class);
        try (Connection db = DriverManager.getConnection(url, "sa", "");
             Statement statement = db.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select source_note_id, field_names, field_values from private_apkg_notes "
                             + "where source_ref = 'ticket2-vocab-normalization' and category = 'VOCABULARY' "
                             + "order by source_note_id")) {
            while (rows.next()) {
                total++;
                long noteId = rows.getLong(1);
                List<Object> fieldNames = json.readValue(rows.getString(2), List.class);
                List<Object> fieldValues = json.readValue(rows.getString(3), List.class);
                Map<String, String> byName = new LinkedHashMap<>();
                for (int i = 0; i < fieldNames.size(); i++) {
                    byName.put((String) fieldNames.get(i), i < fieldValues.size() ? (String) fieldValues.get(i) : "");
                }
                VocabularyNormalizationResult result = parser.parse("ticket2-vocab-normalization", noteId, byName);
                if (!result.validForPromotion()) invalid++;
                for (VocabularyNormalizationWarning warning : result.warnings()) {
                    warningCounts.merge(warning.issue(), 1L, Long::sum);
                }
            }
        }

        Path reportPath = Path.of("build", "reports", "jlpt-max-profiling", "vocabulary-normalization.txt");
        Files.createDirectories(reportPath.getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            out.println("JLPT-MAX Ticket 2 Vocabulary Normalization Report");
            out.println("source file: " + source);
            out.println("extractor summary categories: " + summary.categories());
            out.println();
            out.println("total vocabulary normalized: " + total);
            out.println("invalid/fatal candidates (validForPromotion=false): " + invalid);
            out.println("warning counts by issue:");
            for (VocabularyNormalizationIssue issue : VocabularyNormalizationIssue.values()) {
                out.printf("  %-24s %6d%n", issue, warningCounts.getOrDefault(issue, 0L));
            }
        }
        System.out.println("TICKET2_VOCAB_NORMALIZATION_TOTAL " + total);
        System.out.println("TICKET2_VOCAB_NORMALIZATION_INVALID " + invalid);
        warningCounts.forEach((issue, count) -> System.out.println("TICKET2_VOCAB_NORMALIZATION_WARNING " + issue + " " + count));

        assertThat(total).isEqualTo(summary.categories().getOrDefault("VOCABULARY", 0L));
    }
}
