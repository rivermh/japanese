package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.dto.CategoryOverview;
import com.japanese.content.dto.ContentDetails;
import com.japanese.content.dto.ContentSummary;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Example;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.ContentItemRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
class ContentQueryServiceTest {

    @Autowired
    private ContentQueryService contentQueryService;

    @Autowired
    private SampleContentDataLoader sampleContentDataLoader;

    @Autowired
    private ContentItemRepository contentItemRepository;

    @BeforeEach
    void seedSampleContent() throws Exception {
        sampleContentDataLoader.run();
    }

    @Test
    void returnsSeededWordGrammarAndNonJlptContent() {
        List<ContentSummary> contents = contentQueryService.search(null);

        assertThat(contents).hasSize(3);
        assertThat(contents).extracting(ContentSummary::slug)
                .containsExactly("taberu", "temo-ii", "server");
        assertThat(contents).filteredOn(content -> "server".equals(content.slug()))
                .singleElement()
                .satisfies(content -> assertThat(content.levels()).isEmpty());
    }

    @Test
    void searchesExpressionReadingMeaningAndGrammar() {
        assertThat(contentQueryService.search("\u98df\u3079\u308b")).extracting(ContentSummary::slug)
                .containsExactly("taberu");
        assertThat(contentQueryService.search("\u3044\u3044")).extracting(ContentSummary::slug)
                .containsExactly("temo-ii");
        assertThat(contentQueryService.search("\uc11c\ubc84")).extracting(ContentSummary::slug)
                .containsExactly("server");
    }

    @Test
    void filtersPublishedContentByTypeLevelAndCategory() {
        assertThat(contentQueryService.search(null, ContentType.WORD, "N5", "daily-life"))
                .extracting(ContentSummary::slug)
                .containsExactly("taberu");
        assertThat(contentQueryService.search(null, null, null, "it"))
                .extracting(ContentSummary::slug)
                .containsExactly("server");
    }

    @Test
    void listsCategoriesWithPublishedContentCounts() {
        assertThat(contentQueryService.categoryOverview())
                .filteredOn(category -> "it".equals(category.slug()))
                .singleElement()
                .satisfies(category -> assertThat(category.publicContentCount()).isEqualTo(1));
        assertThat(contentQueryService.categoryOverview())
                .filteredOn(category -> "jlpt".equals(category.slug()))
                .singleElement()
                .satisfies(category -> assertThat(category.publicContentCount()).isEqualTo(2));
    }

    @Test
    void returnsMeaningExamplesAndGrammarDetails() {
        ContentDetails word = contentQueryService.findBySlug("taberu").orElseThrow();
        ContentDetails grammar = contentQueryService.findBySlug("temo-ii").orElseThrow();

        assertThat(word.meanings()).extracting(ContentDetails.MeaningDetails::text)
                .containsExactly("\uba39\ub2e4");
        assertThat(word.pitchAccent()).isEqualTo("2");
        assertThat(word.examples()).hasSize(1);
        assertThat(grammar.grammar().pattern()).isEqualTo("\u301c\u3066\u3082\u3044\u3044");
        assertThat(grammar.examples()).hasSize(1);
    }

    @Test
    @Transactional
    void normalizesKanaWhitespaceAndKeepsExactDictionaryMatchesFirst() {
        saveWord("taberu-context", "\u98df\u3079\u308b\u3082\u306e", "\u305f\u3079\u308b\u3082\u306e", "\uba39\uc744 \uac83", true);
        saveWord("taberu-meaning", "\u98df\u7269", "\u3057\u3087\u304f\u3082\u3064", "\u98df\u3079\u308b \ub300\uc0c1", true);

        assertThat(contentQueryService.search(" \u98df\u3079\u308b ")).extracting(ContentSummary::slug)
                .startsWith("taberu");
        assertThat(contentQueryService.search("\u30bf\u30d9\u30eb")).extracting(ContentSummary::slug)
                .startsWith("taberu");
        assertThat(contentQueryService.search("\uba39\ub2e4")).extracting(ContentSummary::slug)
                .startsWith("taberu");
    }

    @Test
    @Transactional
    void searchesGrammarPatternsAndCombinesAllDatabaseFilters() {
        assertThat(contentQueryService.search("\uff5e\u3066\u3082\u3044\u3044")).extracting(ContentSummary::slug)
                .containsExactly("temo-ii");
        assertThat(contentQueryService.search("\uba39\ub2e4", ContentType.WORD, "N5", "daily-life"))
                .extracting(ContentSummary::slug)
                .containsExactly("taberu");
        assertThat(contentQueryService.search("\uba39\ub2e4", ContentType.GRAMMAR, "N5", "daily-life"))
                .isEmpty();
    }

    @Test
    @Transactional
    void paginatesInTheDatabaseAndExcludesPendingAndRejectedContent() {
        for (int index = 1; index <= 4; index++) {
            saveWord("page-word-" + index, "page" + index, "page" + index, "page search", true);
        }
        saveWord("pending-page-word", "pendingpage", "pendingpage", "page search", false);

        ContentItem rejected = new ContentItem("rejected-page-word", ContentType.WORD, "test", false);
        Word rejectedWord = new Word("rejectedpage", "rejectedpage", "noun", null);
        rejectedWord.addMeaning(new Meaning("ko", "page search", 1));
        rejected.attachWord(rejectedWord);
        rejected.reject("not ready for public search");
        contentItemRepository.saveAndFlush(rejected);

        var firstPage = contentQueryService.searchPage("page search", null, null, null, 0, 2);
        var secondPage = contentQueryService.searchPage("page search", null, null, null, 1, 2);

        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.contents()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.contents()).hasSize(2);
        assertThat(secondPage.hasPrevious()).isTrue();
        assertThat(contentQueryService.search("page")).extracting(ContentSummary::slug)
                .doesNotContain("pending-page-word", "rejected-page-word");
    }

    @Test
    @Transactional
    void preservesMultipleMeaningsLinkedExamplesAndUnlinkedExamples() {
        ContentItem item = new ContentItem("dictionary-multi", ContentType.WORD, "test", true);
        Word word = new Word("\u639b\u3051\u308b", "\u304b\u3051\u308b", "verb", "0");
        Meaning first = new Meaning("ko", "\uac78\ub2e4", 1);
        Meaning second = new Meaning("ko", "\ubd93\ub2e4", 2);
        word.addMeaning(first);
        word.addMeaning(second);
        item.attachWord(word);
        Example linked = new Example("\u96fb\u8a71\u3092\u639b\u3051\u307e\u3059", "\u3067\u3093\u308f\u3092\u304b\u3051\u307e\u3059", "\uc804\ud654\ub97c \uac81\ub2c8\ub2e4", 1);
        linked.setMeaning(first);
        item.addExample(linked);
        item.addExample(new Example("\u6c34\u3092\u639b\u3051\u307e\u3059", "\u307f\u305a\u3092\u304b\u3051\u307e\u3059", "\ubb3c\uc744 \ubd93\uc2b5\ub2c8\ub2e4", 2));
        contentItemRepository.saveAndFlush(item);

        ContentDetails details = contentQueryService.findBySlug("dictionary-multi").orElseThrow();

        assertThat(details.meanings()).extracting(ContentDetails.MeaningDetails::text)
                .containsExactly("\uac78\ub2e4", "\ubd93\ub2e4");
        assertThat(details.meaningGroups()).hasSize(2);
        assertThat(details.meaningGroups().get(0).examples()).singleElement()
                .extracting(ContentDetails.ExampleDetails::japaneseText).isEqualTo("\u96fb\u8a71\u3092\u639b\u3051\u307e\u3059");
        assertThat(details.meaningGroups().get(1).examples()).isEmpty();
        assertThat(details.unlinkedExamples()).singleElement()
                .extracting(ContentDetails.ExampleDetails::japaneseText).isEqualTo("\u6c34\u3092\u639b\u3051\u307e\u3059");
        assertThat(details.pitchAccentDisplay().downstepAfterMora()).isEqualTo(0);
        assertThat(details.pitchAccentDisplay().rawOnly()).isFalse();
        assertThat(contentQueryService.search("\uc804\ud654\ub97c \uac81\ub2c8\ub2e4")).extracting(ContentSummary::slug)
                .containsExactly("dictionary-multi");
    }

    private ContentItem saveWord(String slug, String expression, String reading, String meaningText, boolean published) {
        ContentItem item = new ContentItem(slug, ContentType.WORD, "test", published);
        Word word = new Word(expression, reading, "noun", null);
        word.addMeaning(new Meaning("ko", meaningText, 1));
        item.attachWord(word);
        return contentItemRepository.saveAndFlush(item);
    }
}
