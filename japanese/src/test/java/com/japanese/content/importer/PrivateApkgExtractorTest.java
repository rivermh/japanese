package com.japanese.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PrivateApkgExtractorTest {
    @TempDir Path temp;

    @Test
    void optionalActualApkgRunsOnlyInFreshH2Staging() throws Exception {
        String source = System.getProperty("japanese.actual-apkg");
        assumeTrue(source != null && Files.isRegularFile(Path.of(source)));
        String url = "jdbc:h2:mem:actual_private_stage_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .cleanDisabled(true).load().migrate();
        var extractor = new PrivateApkgExtractor(new DriverManagerDataSource(url, "sa", ""),
                new ObjectMapper(), new GrammarHtmlParser());
        var result = extractor.extract(Path.of(source), "private-jlpt-max");
        assertThat(result.notes()).isGreaterThan(20_000);
        assertThat(result.inserted()).isEqualTo(result.notes());
        assertThat(result.categories().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(result.notes());
        var repeat = extractor.extract(Path.of(source), "private-jlpt-max");
        assertThat(repeat.inserted()).isZero();
        assertThat(repeat.skipped()).isEqualTo(result.notes());
        System.out.println("PRIVATE_APKG_SUMMARY " + result);
    }

    @Test
    void preservesAllModelsFieldsDecksAndCardsWithoutPersistingAudioOrChangingProduction() throws Exception {
        Path sqlite = temp.resolve("collection.anki21");
        try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + sqlite);
             Statement sql = source.createStatement()) {
            sql.execute("create table notetypes(id integer primary key,name text)");
            sql.execute("create table fields(ntid integer,ord integer,name text)");
            sql.execute("create table decks(id integer primary key,name text)");
            sql.execute("create table notes(id integer primary key,guid text,mid integer,tags text,flds text)");
            sql.execute("create table cards(id integer primary key,nid integer,did integer,ord integer)");
            String[] types = {"JLPT MAX덱 어휘", "JLPT MAX덱 문법", "JLPT MAX덱 어휘문제", "JLPT MAX덱 참조표", "Alien"};
            String[] names = {"Word", "FrontHTML", "PromptJP", "TableHTML", "Unseen"};
            for (int i = 0; i < types.length; i++) {
                sql.execute("insert into notetypes values(" + (i + 1) + ",'" + types[i] + "')");
                sql.execute("insert into fields values(" + (i + 1) + ",0,'" + names[i] + "')");
            }
            sql.execute("insert into fields values(1,1,'WordAudio')");
            sql.execute("insert into decks values(1,'JLPT MAX덱/어휘')");
            sql.execute("insert into decks values(2,'JLPT MAX덱/종합 실전/문법')");
            sql.execute("insert into decks values(3,'JLPT MAX덱/연습')");
            sql.execute("insert into notes values(1,'g1',1,' jlpt::N5 ', '猫[sound:secret.mp3]' || char(31) || '[sound:private.mp3]')");
            sql.execute("insert into notes values(2,'g2',2,'', '<mark>文法</mark>')");
            sql.execute("insert into notes values(3,'g3',3,'', '問題')");
            sql.execute("insert into notes values(4,'g4',3,'', '総合')");
            sql.execute("insert into notes values(5,'g5',4,'', '<table><tr><td>表</td></tr></table>')");
            sql.execute("insert into notes values(6,'g6',5,'', 'unknown')");
            sql.execute("insert into notes values(7,'g7',2,'', '<mark>一般文法</mark>')");
            sql.execute("insert into cards values(10,1,1,0)");
            sql.execute("insert into cards values(11,1,1,1)");
            sql.execute("insert into cards values(12,2,2,0)");
            sql.execute("insert into cards values(13,3,3,0)");
            sql.execute("insert into cards values(14,4,2,0)");
            sql.execute("insert into cards values(15,5,3,0)");
            sql.execute("insert into cards values(16,7,3,0)");
            // Unknown note has no card and must still survive.
        }
        Path apkg = temp.resolve("fixture.apkg");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(apkg))) {
            zip.putNextEntry(new ZipEntry("collection.anki21"));
            Files.copy(sqlite, zip);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("secret.mp3"));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
        }
        String url = "jdbc:h2:mem:private_stage_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .cleanDisabled(true).load().migrate();
        DriverManagerDataSource db = new DriverManagerDataSource(url, "sa", "");
        PrivateApkgExtractor extractor = new PrivateApkgExtractor(db, new ObjectMapper(), new GrammarHtmlParser());
        var first = extractor.extract(apkg, "private-fixture");
        assertThat(first.notes()).isEqualTo(7);
        assertThat(first.categories()).containsEntry("VOCABULARY", 1L).containsEntry("GRAMMAR", 1L)
                .containsEntry("PRACTICE", 1L).containsEntry("COMPREHENSIVE", 2L)
                .containsEntry("REFERENCE", 1L).containsEntry("OTHER", 1L);
        assertThat(first.multiCardNotes()).isEqualTo(1);
        assertThat(first.audioReferenceNotes()).isEqualTo(1);
        assertThat(first.rawFields()).isEqualTo(8);
        var again = extractor.extract(apkg, "private-fixture");
        assertThat(again.inserted()).isZero();
        assertThat(again.skipped()).isEqualTo(7);
        try (Connection target = db.getConnection(); Statement sql = target.createStatement()) {
            try (ResultSet rows = sql.executeQuery("select field_names,field_values,normalized_values,tags,card_metadata from private_apkg_notes where source_note_id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("[\"Word\",\"WordAudio\"]");
                assertThat(rows.getString(2)).isEqualTo("[\"猫\",\"\"]");
                assertThat(rows.getString(3)).contains("猫");
                assertThat(rows.getString(4)).isEqualTo(" jlpt::N5 ");
                assertThat(rows.getString(5)).contains("\"id\":10", "\"id\":11");
            }
            try (ResultSet rows = sql.executeQuery("select count(*) from private_apkg_notes where field_values like '%secret.mp3%' or field_values like '%private.mp3%'")) {
                rows.next(); assertThat(rows.getInt(1)).isZero();
            }
            try (ResultSet rows = sql.executeQuery("select count(*) from content_items")) {
                rows.next(); assertThat(rows.getInt(1)).isZero();
            }
            try (ResultSet rows = sql.executeQuery("select count(*) from content_sources")) {
                rows.next(); assertThat(rows.getInt(1)).isZero();
            }
        }
        assertThat(PrivateApkgExtractor.category("Alien", List.of())).isEqualTo("OTHER");
    }

    @Test
    void stripsHtmlAudioElementVariantsWhilePreservingSurroundingMarkupAndText() throws Exception {
        Path sqlite = temp.resolve("audio-variants.anki21");
        String richField =
                "<span class=\"kor\">텍스트1</span>"
                        + "<audio src=\"secret1.mp3\"></audio>"
                        + "<ruby>猫<rt>ねこ</rt></ruby>"
                        + "<audio src='secret2.mp3'>"
                        + "<div class=\"jp\">テキスト2</div>"
                        + "<audio controls data-x=\"y\" src=\"secret3.mp3\" preload=\"none\">"
                        + "<AUDIO SRC=\"secret4.mp3\"></AUDIO>"
                        + "<span>텍스트3</span>"
                        + "<audio\n  src=\"secret5.mp3\"\n  controls\n></audio>"
                        + "<table><tr><td>표</td></tr></table>";
        try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + sqlite);
             Statement sql = source.createStatement()) {
            sql.execute("create table notetypes(id integer primary key,name text)");
            sql.execute("create table fields(ntid integer,ord integer,name text)");
            sql.execute("create table decks(id integer primary key,name text)");
            sql.execute("create table notes(id integer primary key,guid text,mid integer,tags text,flds text)");
            sql.execute("create table cards(id integer primary key,nid integer,did integer,ord integer)");
            sql.execute("insert into notetypes values(1,'JLPT MAX덱 어휘')");
            sql.execute("insert into fields values(1,0,'ExamplesRendered')");
            sql.execute("insert into decks values(1,'JLPT MAX덱/어휘')");
            try (PreparedStatement insert = source.prepareStatement("insert into notes values(1,'gaudio',1,'',?)")) {
                insert.setString(1, richField);
                insert.executeUpdate();
            }
            sql.execute("insert into cards values(20,1,1,0)");
        }
        Path apkg = temp.resolve("audio-variants.apkg");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(apkg))) {
            zip.putNextEntry(new ZipEntry("collection.anki21"));
            Files.copy(sqlite, zip);
            zip.closeEntry();
        }
        String url = "jdbc:h2:mem:audio_variant_stage_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .cleanDisabled(true).load().migrate();
        DriverManagerDataSource db = new DriverManagerDataSource(url, "sa", "");
        PrivateApkgExtractor extractor = new PrivateApkgExtractor(db, new ObjectMapper(), new GrammarHtmlParser());
        var result = extractor.extract(apkg, "private-audio-variants");
        assertThat(result.notes()).isEqualTo(1);
        assertThat(result.audioReferenceNotes()).isEqualTo(1);
        try (Connection target = db.getConnection(); Statement sql = target.createStatement();
             ResultSet rows = sql.executeQuery("select field_values from private_apkg_notes where source_note_id=1")) {
            assertThat(rows.next()).isTrue();
            String stored = rows.getString(1);
            // Every audio-tag variant (double/single quote src, extra attributes, case, multi-line,
            // with/without explicit closing tag) must be gone - filenames and the <audio markup itself.
            assertThat(stored)
                    .doesNotContain("secret1.mp3", "secret2.mp3", "secret3.mp3", "secret4.mp3", "secret5.mp3")
                    .doesNotContainIgnoringCase("<audio");
            // Non-audio HTML and the surrounding Korean/Japanese text must survive untouched.
            assertThat(stored)
                    .contains("텍스트1", "텍스트3", "猫", "ねこ",
                            "テキスト2", "표")
                    .contains("<span", "<ruby", "<rt", "<div", "<table", "<tr", "<td");
        }
        // Re-running against the same source must still be idempotent after the hardening change.
        var repeat = extractor.extract(apkg, "private-audio-variants");
        assertThat(repeat.inserted()).isZero();
        assertThat(repeat.skipped()).isEqualTo(1);
    }
}
