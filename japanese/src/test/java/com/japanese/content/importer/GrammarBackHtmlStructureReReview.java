package com.japanese.content.importer;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import tools.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * READ-ONLY, opt-in, full-population re-review of regular-GRAMMAR BackHTML structure, prompted by
 * a mid-implementation discovery while building {@link GrammarNormalizationParser}'s example
 * extraction: every real note's {@code section._j4a} blocks turned out to be "Nuance"/"Connection"/
 * "Confusable patterns" reference cards, NOT bilingual example sentences as
 * {@link GrammarHtmlParser} (and every measurement built on it since Ticket 1) assumed. This tool
 * verifies that reinterpretation against the FULL 1,078-note population (not just a handful of
 * samples) before any parser redesign is attempted. Not part of the production build path; runs
 * only when -Djapanese.actual-apkg=&lt;path&gt; is supplied (opt-in, same gate as every other
 * profiling tool in this package). Uses only an isolated in-memory H2, never a production DB, and
 * never opens any ZIP media entry or audio binary.
 */
class GrammarBackHtmlStructureReReview {

    @Test
    void reReviewRegularGrammarBackHtmlStructure() throws Exception {
        String source = System.getProperty("japanese.actual-apkg");
        assumeTrue(source != null && Files.isRegularFile(Path.of(source)),
                "japanese.actual-apkg system property must point at the real JLPT-MAX apkg file");

        String url = "jdbc:h2:mem:grammar_backhtml_rereview_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .cleanDisabled(true).load().migrate();
        ObjectMapper json = new ObjectMapper();
        var extractor = new PrivateApkgExtractor(new DriverManagerDataSource(url, "sa", ""), json, new GrammarHtmlParser());
        var summary = extractor.extract(Path.of(source), "grammar-backhtml-rereview");

        Path reportPath = Path.of("build", "reports", "jlpt-max-profiling", "grammar-backhtml-re-review.txt");
        Files.createDirectories(reportPath.getParent());
        try (Connection db = DriverManager.getConnection(url, "sa", "");
             PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            out.println("Grammar BackHTML structure re-review (full population, actual APKG)");
            out.println("source file: " + source);
            out.println("extractor summary categories: " + summary.categories());
            out.println();
            var rows = GrammarNormalizationProfilingReport.loadRows(db, json);
            List<GrammarNormalizationProfilingReport.Row> grammar = rows.stream()
                    .filter(r -> "GRAMMAR".equals(r.category)).toList();
            out.println("total category=GRAMMAR notes: " + grammar.size());
            out.println();
            profile(grammar, out);
        }
        System.out.println("GRAMMAR_BACKHTML_REREVIEW_WRITTEN " + reportPath.toAbsolutePath());
    }

    private void profile(List<GrammarNormalizationProfilingReport.Row> rows, PrintWriter out) {
        long sectionJ4rCount = 0;
        long j4zPresent = 0;
        List<Integer> j4zLengths = new ArrayList<>();
        long glossPresent = 0;
        List<Integer> glossLengths = new ArrayList<>();
        Map<Integer, Long> sectionJ4aCountDistribution = new TreeMap<>();
        Map<String, Long> label0Distribution = new TreeMap<>();
        Map<String, Long> label1Distribution = new TreeMap<>();
        Map<String, Long> label2Distribution = new TreeMap<>();
        long connectionPresent = 0;
        List<Integer> connectionLengths = new ArrayList<>();
        Map<Integer, Long> confusableLiCountDistribution = new TreeMap<>();
        long frontBackMarkTextMismatch = 0;
        long frontMarkMissingInBack = 0;

        for (var r : rows) {
            String frontHtml = r.byName.get("FrontHTML");
            String backHtml = r.byName.get("BackHTML");
            Element front = Jsoup.parseBodyFragment(frontHtml == null ? "" : frontHtml).body();
            Element back = Jsoup.parseBodyFragment(backHtml == null ? "" : backHtml).body();

            if (back.selectFirst("section._j4r") != null) sectionJ4rCount++;

            Element j4z = back.selectFirst("div._j4z");
            if (j4z != null && !j4z.text().isBlank()) {
                j4zPresent++;
                j4zLengths.add(j4z.text().trim().length());
            }

            Element gloss = back.selectFirst("div._j4x");
            if (gloss != null && !gloss.text().isBlank()) {
                glossPresent++;
                glossLengths.add(gloss.text().trim().length());
            }

            Elements sections = back.select("section._j4a");
            sectionJ4aCountDistribution.merge(sections.size(), 1L, Long::sum);
            if (sections.size() > 0) {
                label0Distribution.merge(labelOf(sections.get(0)), 1L, Long::sum);
            }
            if (sections.size() > 1) {
                label1Distribution.merge(labelOf(sections.get(1)), 1L, Long::sum);
                Element connectionValue = sections.get(1).selectFirst("div._j4v");
                if (connectionValue != null && !connectionValue.text().isBlank()) {
                    connectionPresent++;
                    connectionLengths.add(connectionValue.text().trim().length());
                }
            }
            if (sections.size() > 2) {
                label2Distribution.merge(labelOf(sections.get(2)), 1L, Long::sum);
                Elements confusableItems = sections.get(2).select("ul._j1d > li._j1f");
                confusableLiCountDistribution.merge(confusableItems.size(), 1L, Long::sum);
            }

            Element frontMark = front.selectFirst("mark");
            Element backMark = back.selectFirst("section._j4r mark");
            if (backMark == null) {
                frontMarkMissingInBack++;
            } else if (frontMark != null && !frontMark.text().equals(backMark.text())) {
                frontBackMarkTextMismatch++;
            }
        }

        out.println("=== section._j4r (top-level back wrapper) presence: " + sectionJ4rCount + " / " + rows.size() + " ===");
        out.println();
        out.println("=== div._j4z (front-sentence Korean translation, formerly mislabeled \"explanation\") ===");
        out.println("present/non-blank: " + j4zPresent + " / " + rows.size());
        out.println("length: " + stats(j4zLengths));
        out.println();
        out.println("=== div._j4x (meaning gloss, e.g. \"저~\", \"아직 ~하지 않았다\" - currently extracted by nobody) ===");
        out.println("present/non-blank: " + glossPresent + " / " + rows.size());
        out.println("length: " + stats(glossLengths));
        out.println();
        out.println("=== section._j4a count per note (formerly mislabeled \"examples\", always expected 3: Nuance/Connection/Confusable) ===");
        out.println("distribution: " + sectionJ4aCountDistribution);
        out.println("label at index 0 (expected \"뉘앙스\"=Nuance): " + label0Distribution);
        out.println("label at index 1 (expected \"접속\"=Connection): " + label1Distribution);
        out.println("label at index 2 (expected \"헷갈리는 문형\"=Confusable patterns): " + label2Distribution);
        out.println();
        out.println("=== index-1 (\"접속\"/Connection) div._j4v - the REAL grammatical connection info, NOT the Kind field ===");
        out.println("present/non-blank: " + connectionPresent + " / " + rows.size());
        out.println("length: " + stats(connectionLengths));
        out.println();
        out.println("=== index-2 (\"헷갈리는 문형\"/Confusable patterns) ul._j1d > li._j1f count ===");
        out.println("distribution: " + confusableLiCountDistribution);
        out.println();
        out.println("=== sanity: does BackHTML's own section._j4r > ... > mark duplicate FrontHTML's <mark> text? ===");
        out.println("back mark missing entirely: " + frontMarkMissingInBack + " / " + rows.size());
        out.println("front/back mark text mismatch (when both present): " + frontBackMarkTextMismatch + " / " + rows.size());
        out.println();
        profileFrontExampleSentence(rows, out);
        out.println();
        profileFrontBackJapaneseTextEquality(rows, out);
    }

    /**
     * Ticket 3B-1 safety follow-up: does FrontHTML's own div._j4u (ruby-stripped) equal BackHTML's
     * duplicate div._j4u (ruby-stripped)? Report-only - does not change how frontExample is parsed
     * (it stays sourced from FrontHTML only) and does not design any new ruby/reading
     * representation.
     */
    private void profileFrontBackJapaneseTextEquality(List<GrammarNormalizationProfilingReport.Row> rows, PrintWriter out) {
        long bothPresent = 0;
        long exactEqual = 0;
        long different = 0;
        for (var r : rows) {
            String frontHtml = r.byName.get("FrontHTML");
            String backHtml = r.byName.get("BackHTML");
            Element front = Jsoup.parseBodyFragment(frontHtml == null ? "" : frontHtml).body();
            Element back = Jsoup.parseBodyFragment(backHtml == null ? "" : backHtml).body();
            Element frontContainer = front.selectFirst("div._j4u");
            Element backContainer = back.selectFirst("section._j4r div._j4u");
            if (frontContainer == null || backContainer == null) continue;
            String frontText = rubyStrippedText(frontContainer);
            String backText = rubyStrippedText(backContainer);
            if (frontText == null || backText == null) continue;
            bothPresent++;
            if (frontText.equals(backText)) exactEqual++;
            else different++;
        }
        out.println("=== sanity: FrontHTML div._j4u (ruby-stripped) vs BackHTML's own duplicate div._j4u (ruby-stripped) ===");
        out.println("both present/non-blank: " + bothPresent + " / " + rows.size());
        out.println("exact equal: " + exactEqual + " / " + bothPresent);
        out.println("different: " + different + " / " + bothPresent);
        out.println("(report only - frontExample continues to be sourced from FrontHTML's own div._j4u only;");
        out.println(" no new reading/ruby representation designed here)");
    }

    private String rubyStrippedText(Element element) {
        Element copy = element.clone();
        copy.select("rt").remove();
        return AnkiFieldTextNormalizer.text(copy.html());
    }

    /**
     * Ticket 3B-1 follow-up: FrontHTML's div._j4u contains the <mark> as a descendant, so
     * div._j4u.text() should be the FULL example sentence (context + the marked answer fragment
     * filled in), not just the mark's own short fragment used for {@code pattern}. This checks
     * whether that full-sentence extraction is safe/stable across the whole population before
     * adding a frontExample field.
     */
    private void profileFrontExampleSentence(List<GrammarNormalizationProfilingReport.Row> rows, PrintWriter out) {
        long containerPresent = 0;
        long containerBlank = 0;
        long containsMarkAsSubstring = 0;
        long rubyPresentInContainer = 0;
        long readingExtractable = 0;
        List<Integer> sentenceLengths = new ArrayList<>();
        List<Integer> readingLengths = new ArrayList<>();

        for (var r : rows) {
            String frontHtml = r.byName.get("FrontHTML");
            Element front = Jsoup.parseBodyFragment(frontHtml == null ? "" : frontHtml).body();
            Element container = front.selectFirst("div._j4u");
            Element mark = front.selectFirst("mark");
            if (container == null) continue;
            containerPresent++;

            Element copy = container.clone();
            copy.select("rt").remove();
            String withoutRuby = AnkiFieldTextNormalizer.text(copy.html());
            if (withoutRuby == null || withoutRuby.isBlank()) {
                containerBlank++;
                continue;
            }
            sentenceLengths.add(withoutRuby.length());
            if (mark != null && !mark.text().isBlank() && withoutRuby.contains(mark.text())) {
                containsMarkAsSubstring++;
            }
            if (!container.select("ruby").isEmpty()) {
                rubyPresentInContainer++;
                String reading = container.select("ruby rt").text().trim();
                if (!reading.isBlank()) {
                    readingExtractable++;
                    readingLengths.add(reading.length());
                }
            }
        }

        out.println("=== FrontHTML div._j4u full-sentence extraction feasibility (candidate frontExample) ===");
        out.println("div._j4u present: " + containerPresent + " / " + rows.size());
        out.println("div._j4u present but blank after ruby-stripped normalization: " + containerBlank + " / " + rows.size());
        out.println("sentence length (ruby-stripped): " + stats(sentenceLengths));
        out.println("sentence text contains the <mark> fragment as a substring: " + containsMarkAsSubstring + " / " + rows.size());
        out.println("div._j4u contains at least one <ruby>: " + rubyPresentInContainer + " / " + rows.size());
        out.println("ruby rt reading extractable (non-blank) when ruby present: " + readingExtractable + " / " + rubyPresentInContainer);
        out.println("reading length: " + stats(readingLengths));
    }

    private String labelOf(Element section) {
        Element label = section.selectFirst("div._jcq");
        return label == null ? "(none)" : label.text().trim();
    }

    private String stats(List<Integer> lens) {
        if (lens.isEmpty()) return "(no data)";
        int min = lens.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = lens.stream().mapToInt(Integer::intValue).max().orElse(0);
        double avg = lens.stream().mapToInt(Integer::intValue).average().orElse(0);
        return String.format("n=%d min=%d max=%d avg=%.1f", lens.size(), min, max, avg);
    }
}
