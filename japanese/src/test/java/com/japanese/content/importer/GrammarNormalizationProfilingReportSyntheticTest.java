package com.japanese.content.importer;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Compile/logic sanity check for {@link GrammarNormalizationProfilingReport}, using a tiny
 * synthetic Grammar-model fixture (not the real JLPT-MAX apkg). This test always runs in the
 * general Gradle suite (no -Djapanese.actual-apkg opt-in needed) and proves the report's
 * loading/cross-tab/structure logic executes correctly end-to-end through the real extraction and
 * H2 persistence path. It does NOT measure real JLPT-MAX Grammar data - the counts asserted here
 * describe this synthetic fixture only, not the actual 3,605-note corpus.
 */
class GrammarNormalizationProfilingReportSyntheticTest {
    @TempDir Path temp;

    @Test
    void loadsAndWritesReportAgainstSyntheticGrammarFixture() throws Exception {
        Path sqlite = temp.resolve("collection.anki21");
        try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + sqlite);
             Statement sql = source.createStatement()) {
            sql.execute("create table notetypes(id integer primary key,name text)");
            sql.execute("create table fields(ntid integer,ord integer,name text)");
            sql.execute("create table decks(id integer primary key,name text)");
            sql.execute("create table notes(id integer primary key,guid text,mid integer,tags text,flds text)");
            sql.execute("create table cards(id integer primary key,nid integer,did integer,ord integer)");
            sql.execute("insert into notetypes values(1,'JLPT MAX덱 문법')");
            String[] fieldNames = {"Level", "Kind", "UnitID", "FrontHTML", "BackHTML",
                    "IsBasic", "IsGrammarForm", "IsPassageBlank", "IsSentenceArrangement"};
            for (int i = 0; i < fieldNames.length; i++) {
                sql.execute("insert into fields values(1," + i + ",'" + fieldNames[i] + "')");
            }
            sql.execute("insert into decks values(1,'JLPT MAX덱/문법')");
            sql.execute("insert into decks values(2,'JLPT MAX덱/종합 실전/문법')");

            // note 1: normal GRAMMAR deck, <mark> pattern, structured explanation, one example.
            insertGrammarNote(source, 1, "g1", "N3",
                    "2", "U-001",
                    "<div><mark>てしまう</mark>する形</div>",
                    "<div class=\"_j4z\">완료/후회를 나타낸다.</div>"
                            + "<section class=\"_j4a\"><div class=\"_jcq\">食べてしまった</div><div class=\"_j4v\">다 먹어버렸다</div></section>",
                    "1", "1", "1", "");
            // note 2: normal GRAMMAR deck, div[lang=ja] fallback selector, no explanation div (back.text() fallback).
            insertGrammarNote(source, 2, "g2", "N2",
                    "3", "U-002",
                    "<div lang=\"ja\">というものだ</div>",
                    "<div>설명 텍스트만 있고 구조화된 div가 없음</div>",
                    "1", "1", "1", "");
            // note 3: COMPREHENSIVE deck (grammar model placed under 종합 실전), choice/answer-like signal.
            insertGrammarNote(source, 3, "g3", "N1",
                    "*", "U-101",
                    "<div>質問文</div>",
                    "<ol><li>① 選択肢A</li><li>② 選択肢B</li></ol><div>정답: ①</div>",
                    "", "", "", "");
            // note 4: COMPREHENSIVE deck, IsSentenceArrangement set, ordering signal.
            insertGrammarNote(source, 4, "g4", "N4",
                    "0", "U-102",
                    "<div>語句を並び替えなさい</div>",
                    "<div>순서대로 배열하시오</div>",
                    "", "", "", "1");

            sql.execute("insert into cards values(10,1,1,0)");
            sql.execute("insert into cards values(11,2,1,0)");
            sql.execute("insert into cards values(12,3,2,0)");
            sql.execute("insert into cards values(13,4,2,0)");
        }
        Path apkg = temp.resolve("fixture.apkg");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(apkg))) {
            zip.putNextEntry(new ZipEntry("collection.anki21"));
            Files.copy(sqlite, zip);
            zip.closeEntry();
        }

        String url = "jdbc:h2:mem:grammar_profiling_synthetic_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .cleanDisabled(true).load().migrate();
        ObjectMapper json = new ObjectMapper();
        DriverManagerDataSource ds = new DriverManagerDataSource(url, "sa", "");
        PrivateApkgExtractor extractor = new PrivateApkgExtractor(ds, json, new GrammarHtmlParser());
        var summary = extractor.extract(apkg, "grammar-profiling-synthetic");
        assertThat(summary.categories()).containsEntry("GRAMMAR", 2L).containsEntry("COMPREHENSIVE", 2L);

        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            var rows = GrammarNormalizationProfilingReport.loadRows(db, json);
            assertThat(rows).hasSize(4);
            assertThat(rows).extracting(r -> r.category).containsExactlyInAnyOrder(
                    "GRAMMAR", "GRAMMAR", "COMPREHENSIVE", "COMPREHENSIVE");
            assertThat(rows.get(0).byName.keySet()).contains(
                    "Level", "Kind", "UnitID", "FrontHTML", "BackHTML",
                    "IsBasic", "IsGrammarForm", "IsPassageBlank", "IsSentenceArrangement");

            StringWriter buffer = new StringWriter();
            try (PrintWriter out = new PrintWriter(buffer)) {
                GrammarNormalizationProfilingReport.writeReport(rows, out);
            }
            String report = buffer.toString();
            assertThat(report)
                    .contains("total grammar-model notes: 4")
                    .contains("=== 5. Is* FLAG NOTE-LEVEL CROSS-TAB ===")
                    .contains("=== 6. Kind FIELD ANALYSIS ===")
                    .contains("=== 7/8. NORMAL GRAMMAR FrontHTML STRUCTURE")
                    .contains("=== 9/10. COMPREHENSIVE BackHTML STRUCTURE")
                    .contains("=== 11. IsSentenceArrangement FOCUSED ANALYSIS ===")
                    .contains("IsSentenceArrangement non-empty notes: 1")
                    .contains("=== 15. UnitID / IDENTITY ===")
                    .contains("=== 16. JLPT LEVEL x CATEGORY x SUBTYPE ===");
        }
    }

    private static void insertGrammarNote(Connection source, long id, String guid, String level, String kind, String unitId,
            String frontHtml, String backHtml, String isBasic, String isGrammarForm, String isPassageBlank,
            String isSentenceArrangement) throws Exception {
        String flds = String.join("", level, kind, unitId, frontHtml, backHtml,
                isBasic, isGrammarForm, isPassageBlank, isSentenceArrangement);
        try (PreparedStatement insert = source.prepareStatement("insert into notes values(?,?,1,'',?)")) {
            insert.setLong(1, id);
            insert.setString(2, guid);
            insert.setString(3, flds);
            insert.executeUpdate();
        }
    }
}
