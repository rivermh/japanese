package com.japanese.content.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentReviewHistoryRepository;
import com.japanese.content.repository.ContentSourceRepository;
import com.japanese.content.service.ContentQueryService;
import com.japanese.content.service.ContentReviewService;
import com.japanese.content.service.ContentSourceRightsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("import-sample")
class ApkgVocabularyImporterTest {

    private static Path apkg;

    @Autowired
    private ContentItemRepository contentItemRepository;

    @Autowired
    private ContentQueryService contentQueryService;

    @Autowired
    private ContentReviewService contentReviewService;

    @Autowired
    private ContentReviewHistoryRepository contentReviewHistoryRepository;

    @Autowired
    private ContentSourceRepository contentSourceRepository;

    @Autowired
    private ContentSourceRightsService sourceRights;

    @BeforeAll
    static void createFixture() throws Exception {
        Path collection = Files.createTempFile("japanese-test-collection-", ".anki21");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + collection);
             Statement statement = connection.createStatement()) {
            statement.execute("create table notetypes (id integer primary key, name text not null)");
            statement.execute("create table fields (ntid integer not null, ord integer not null, name text not null)");
            statement.execute("create table notes (id integer primary key, mid integer not null, tags text not null, flds text not null)");
            statement.execute("insert into notetypes values (99, 'JLPT MAX덱 어휘')");
            List<String> fields = vocabularyFields();
            for (int index = 0; index < fields.size(); index++) {
                statement.execute("insert into fields values (99, " + index + ", '" + fields.get(index) + "')");
            }
            statement.execute("insert into notes values (1, 99, '', '"
                    + fields("ci-fixture-1", "食べる", "たべる", "2", "먹다", "N5", examplesHtml()) + "')");
            statement.execute("insert into notes values (2, 99, '', '"
                    + fields("ci-fixture-2", "サーバー", "サーバー", "", "서버", "", "") + "')");
        }
        apkg = Files.createTempFile("japanese-test-", ".apkg");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(apkg))) {
            zip.putNextEntry(new ZipEntry("collection.anki21"));
            zip.write(Files.readAllBytes(collection));
            zip.closeEntry();
        }
        Files.deleteIfExists(collection);
    }

    @DynamicPropertySource
    static void importProperties(DynamicPropertyRegistry registry) {
        registry.add("japanese.import.apkg-path", () -> apkg.toString());
        registry.add("japanese.import.limit", () -> "2");
    }

    @AfterAll
    static void deleteFixture() throws Exception {
        Files.deleteIfExists(apkg);
    }

    @Test
    @Transactional
    void importsOnlyVocabularyFieldsAsUnpublishedContent() {
        assertThat(contentItemRepository.count()).isEqualTo(2);

        ContentItem item = contentItemRepository.findBySlug("jlpt-max-ci-fixture-1").orElseThrow();
        assertThat(item.isPublished()).isFalse();
        assertThat(item.getWord().getExpression()).isEqualTo("食べる");
        assertThat(item.getWord().getMeanings()).extracting(meaning -> meaning.getText())
                .containsExactly("먹다");
        assertThat(item.getExamples()).singleElement()
                .satisfies(example -> assertThat(example.getTranslation()).isEqualTo("매일 먹습니다."));
        assertThat(item.getExamples()).singleElement()
                .satisfies(example -> assertThat(example.getAudioFileName()).isEqualTo("jlpt-v2-example-ci-fixture.mp3"));
        assertThat(item.getLevels()).extracting(level -> level.getCode()).containsExactly("N5");
        assertThat(contentQueryService.findBySlug("jlpt-max-ci-fixture-1")).isEmpty();

        var source = contentSourceRepository.findBySourceRef("JLPT-MAX-Deck-2.1.1.apkg").orElseThrow();
        assertThat(source.getRightsStatus()).isEqualTo(ContentSourceRightsStatus.UNKNOWN);
        sourceRights.reviewRights(source.getId(), ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,
                "테스트 fixture의 권리 조건 검토", true, null);
        sourceRights.reviewRights(source.getId(), ContentSourceRightsStatus.ALLOWED,
                "테스트 fixture 공개 허용", true, "Fixture attribution");

        contentReviewService.publish(item.getId());

        assertThat(contentQueryService.findBySlug("jlpt-max-ci-fixture-1")).isPresent();

        ContentItem rejected = contentItemRepository.findBySlug("jlpt-max-ci-fixture-2").orElseThrow();
        contentReviewService.reject(rejected.getId(), "뜻 검토 필요");
        assertThat(rejected.getReviewStatus().name()).isEqualTo("REJECTED");
        assertThat(rejected.getReviewNote()).isEqualTo("뜻 검토 필요");
        assertThat(contentQueryService.search(null)).extracting(value -> value.slug())
                .doesNotContain("jlpt-max-ci-fixture-2");
        assertThat(contentReviewHistoryRepository.findByContentItemIdOrderByReviewedAtDesc(rejected.getId()))
                .singleElement()
                .satisfies(history -> assertThat(history.getStatus().name()).isEqualTo("REJECTED"));

        contentReviewService.reset(rejected.getId(), "import correction");

        assertThat(rejected.getReviewStatus().name()).isEqualTo("PENDING");
        assertThat(rejected.getReviewNote()).isNull();
        assertThat(contentReviewHistoryRepository.findByContentItemIdOrderByReviewedAtDesc(rejected.getId()))
                .hasSize(2);
    }

    private static List<String> vocabularyFields() {
        List<String> fields = new ArrayList<>();
        fields.add("EntryID");
        fields.add("Word");
        fields.add("VocabularyContext");
        fields.add("Reading");
        fields.add("PitchAccent");
        fields.add("Meaning");
        fields.add("MeaningV2");
        fields.add("ExamplesV2");
        fields.add("ExamplesRendered");
        fields.add("MediaIntentsV2");
        fields.add("PartOfSpeech");
        fields.add("KanjiDetails");
        fields.add("JLPT");
        fields.add("CanonicalRecordHash");
        fields.add("WordAudio");
        fields.add("WordAudioFile");
        fields.addAll(IntStream.range(1, 6).boxed().flatMap(index -> java.util.stream.Stream.of(
                "Example" + index + "JP", "Example" + index + "Reading", "Example" + index + "KO",
                "Example" + index + "Sense", "Example" + index + "Audio", "Example" + index + "PitchAccent"
        )).toList());
        fields.add("UsageRegister");
        fields.add("ConjugationDetails");
        fields.add("StudyPriority");
        fields.add("UsageDetails");
        fields.add("WordFormationDetails");
        fields.add("RelatedWords");
        fields.add("WordJLPT");
        fields.add("KanaAuxiliaryWord");
        fields.add("KanaAuxiliaryReading");
        fields.add("KanaAuxiliaryContext");
        fields.add("KanaAuxiliaryJLPT");
        fields.add("RetiredKanaAuxiliaryCard");
        fields.add("KoreanRecallPrompt");
        return fields;
    }

    private static String fields(String entryId, String word, String reading, String pitchAccent,
                                 String meaning, String level, String examples) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < 59; index++) {
            values.add("");
        }
        values.set(0, entryId);
        values.set(1, word);
        values.set(3, reading);
        values.set(4, "<span data-pitch-terminal-states=\"[true]\"><span j1=\"[[2,0]]\">"
                + pitchAccent + "</span></span>");
        values.set(5, meaning);
        values.set(8, examples);
        values.set(10, "명사");
        values.set(12, level);
        values.set(52, level);
        return String.join("\u001f", values).replace("'", "''");
    }

    private static String examplesHtml() {
        return "<section class=\"_j47\" aria-label=\"뜻 묶음: 먹다\">"
                + "<div class=\"_j48\" lang=\"ja\">毎朝食べます。</div>"
                + "<div class=\"_jau\" lang=\"ja\">まいあさたべます。</div>"
                + "<audio src=\"jlpt-v2-example-ci-fixture.mp3\"></audio>"
                + "<div class=\"_j49\">매일 먹습니다.</div></section>";
    }
}
