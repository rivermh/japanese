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
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Ticket 3B-1 verification tool (test/dev scope only): runs every real category=GRAMMAR,
 * IsBasic-active note (the 1,078-note subset Ticket 3A.1 profiled) through
 * {@link GrammarNormalizationParser} - using the corrected BackHTML semantic model confirmed by
 * the Ticket 3B-1 full-population re-review (see {@link GrammarBackHtmlStructureReReview}) - and
 * reports outcome/warning counts. Not part of the production build path. Runs only when
 * -Djapanese.actual-apkg=&lt;path&gt; is explicitly supplied (opt-in, same gate as
 * {@link PrivateApkgExtractorTest#optionalActualApkgRunsOnlyInFreshH2Staging()} and
 * {@link VocabularyNormalizationRealApkgReport}), so the general Gradle test suite (and CI without
 * the real ~1.1GB apkg file) always passes/skips cleanly. Uses only an isolated in-memory H2
 * (never a production DB) and never opens any ZIP media entry or audio binary - it only calls
 * {@link PrivateApkgExtractor}, which already enforces that, and then a pure in-memory parser.
 */
class GrammarNormalizationRealApkgReport {

    @Test
    void ticket3b1GrammarNormalizationAgainstRealApkg() throws Exception {
        String source = System.getProperty("japanese.actual-apkg");
        assumeTrue(source != null && Files.isRegularFile(Path.of(source)),
                "japanese.actual-apkg system property must point at the real JLPT-MAX apkg file");

        String url = "jdbc:h2:mem:ticket3b1_grammar_normalization_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .cleanDisabled(true).load().migrate();
        ObjectMapper json = new ObjectMapper();
        var extractor = new PrivateApkgExtractor(new DriverManagerDataSource(url, "sa", ""), json, new GrammarHtmlParser());
        var summary = extractor.extract(Path.of(source), "ticket3b1-grammar-normalization");
        GrammarNormalizationParser parser = new GrammarNormalizationParser();

        long total = 0;
        long fatal = 0;
        long reviewRequired = 0;
        int maxPatternLength = 0;
        int maxMeaningGlossLength = 0;
        int maxConnectionLength = 0;
        long confusablePatternsMissingOrWrongCount = 0;
        Map<GrammarNormalizationIssue, Long> issueCounts = new EnumMap<>(GrammarNormalizationIssue.class);
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            var rows = GrammarNormalizationProfilingReport.loadRows(db, json);
            for (var row : rows) {
                if (!"GRAMMAR".equals(row.category)) continue; // scope: category=GRAMMAR only, per Ticket 3B-1
                total++;
                GrammarNormalizationResult result = parser.parse("ticket3b1-grammar-normalization", row.id,
                        row.category, row.byName);
                if (!result.hasNoFatalIssues()) fatal++;
                if (result.warnings().stream().anyMatch(w -> w.severity() == GrammarNormalizationSeverity.REVIEW_REQUIRED)) {
                    reviewRequired++;
                }
                for (GrammarNormalizationWarning warning : result.warnings()) {
                    issueCounts.merge(warning.issue(), 1L, Long::sum);
                }
                if (result.pattern() != null) maxPatternLength = Math.max(maxPatternLength, result.pattern().length());
                if (result.meaningGloss() != null) maxMeaningGlossLength = Math.max(maxMeaningGlossLength, result.meaningGloss().length());
                if (result.connection() != null) maxConnectionLength = Math.max(maxConnectionLength, result.connection().length());
                if (result.confusablePatterns().size() != 3) confusablePatternsMissingOrWrongCount++;
            }
        }

        Path reportPath = Path.of("build", "reports", "jlpt-max-profiling", "grammar-normalization.txt");
        Files.createDirectories(reportPath.getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            out.println("JLPT-MAX Ticket 3B-1 Grammar Normalization Report (corrected semantic model)");
            out.println("source file: " + source);
            out.println("extractor summary categories: " + summary.categories());
            out.println();
            out.println("total category=GRAMMAR notes parsed: " + total);
            out.println("fatal (hasNoFatalIssues=false): " + fatal);
            out.println("review-required (at least one REVIEW_REQUIRED warning): " + reviewRequired);
            out.println("max pattern length: " + maxPatternLength);
            out.println("max meaningGloss length: " + maxMeaningGlossLength);
            out.println("max connection length: " + maxConnectionLength);
            out.println("notes with confusablePatterns.size() != 3: " + confusablePatternsMissingOrWrongCount);
            out.println("issue counts:");
            for (GrammarNormalizationIssue issue : GrammarNormalizationIssue.values()) {
                out.printf("  %-36s %6d%n", issue, issueCounts.getOrDefault(issue, 0L));
            }
        }
        System.out.println("TICKET3B1_GRAMMAR_NORMALIZATION_TOTAL " + total);
        System.out.println("TICKET3B1_GRAMMAR_NORMALIZATION_FATAL " + fatal);
        System.out.println("TICKET3B1_GRAMMAR_NORMALIZATION_REVIEW_REQUIRED " + reviewRequired);
        System.out.println("TICKET3B1_GRAMMAR_NORMALIZATION_MAX_PATTERN_LENGTH " + maxPatternLength);
        System.out.println("TICKET3B1_GRAMMAR_NORMALIZATION_MAX_MEANING_GLOSS_LENGTH " + maxMeaningGlossLength);
        System.out.println("TICKET3B1_GRAMMAR_NORMALIZATION_MAX_CONNECTION_LENGTH " + maxConnectionLength);
        System.out.println("TICKET3B1_GRAMMAR_NORMALIZATION_CONFUSABLE_COUNT_WRONG " + confusablePatternsMissingOrWrongCount);
        issueCounts.forEach((issue, count) -> System.out.println("TICKET3B1_GRAMMAR_NORMALIZATION_ISSUE " + issue + " " + count));

        assertThat(total).isEqualTo(summary.categories().getOrDefault("GRAMMAR", 0L));
    }
}
