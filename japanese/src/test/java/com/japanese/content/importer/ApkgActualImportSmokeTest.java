package com.japanese.content.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.content.entity.ContentItem;
import com.japanese.content.repository.ContentItemRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("import-sample")
@EnabledIfSystemProperty(named = "japanese.actual-apkg", matches = ".+")
class ApkgActualImportSmokeTest {

    @Autowired
    private ContentItemRepository contentItemRepository;

    @DynamicPropertySource
    static void importProperties(DynamicPropertyRegistry registry) {
        registry.add("japanese.import.apkg-path", () -> System.getProperty("japanese.actual-apkg"));
        registry.add("japanese.import.limit", () -> "20");
        registry.add("japanese.import.grammar-enabled", () -> "false");
    }

    @Test
    @Transactional
    void importsTwentyRecordsFromTheRealApkgIntoReviewState() {
        Path source = Path.of(System.getProperty("japanese.actual-apkg"));
        assertThat(Files.isRegularFile(source)).isTrue();

        List<ContentItem> items = contentItemRepository.findAll();

        assertThat(items).hasSize(20);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.isPublished()).isFalse();
            assertThat(item.getSourceRef()).isEqualTo("JLPT-MAX-Deck-2.1.1.apkg");
            assertThat(item.getWord()).isNotNull();
            assertThat(item.getWord().getExpression()).isNotBlank();
        });
        assertThat(items).anySatisfy(item -> assertThat(item.getExamples()).isNotEmpty());
    }
}
