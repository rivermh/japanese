package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.dto.AdminContentReleaseDryRunModels.Filter;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Example;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentSourceRepository;
import com.japanese.content.repository.LevelRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class ContentReleaseDryRunServiceTest {
    @Autowired ContentReleaseDryRunService dryRun;
    @Autowired ContentSourceRightsService rights;
    @Autowired ContentSourceRepository sources;
    @Autowired ContentItemRepository contents;
    @Autowired LevelRepository levels;
    @Autowired SampleContentDataLoader sample;

    private String allowed;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        allowed = "dry-run-" + UUID.randomUUID();
        ContentSource source = sources.save(new ContentSource(allowed, "Dry run source", "1", null, null, null, null));
        rights.reviewRights(source.getId(), ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "evidence", false, null);
        rights.reviewRights(source.getId(), ContentSourceRightsStatus.ALLOWED, "redistribution confirmed", false, null);
    }

    @Test
    void manualDetailsCoverEntireTargetSetBeyondSamplesWithoutChangingDigest() {
        for (int i = 0; i < 12; i++) saveWord("manual-" + i, "N5", false);
        ContentItem blocked = saveWord("blocked-manual", "N5", false);
        blocked.getWord().getMeanings().clear();
        blocked.getWord().addMeaning(new Meaning("en", "Only English", 0));
        contents.flush();
        var result = dryRun.run(new Filter(ContentType.WORD, "N5", null, false, allowed, null));
        assertThat(result.manualReviewSamples()).hasSize(10);
        assertThat(result.manualTargets()).hasSize(12);
        assertThat(result.manualTargets()).allSatisfy(t -> {
            assertThat(t.content().contentItemId()).isNotEqualTo(blocked.getId());
            assertThat(t.issues()).allMatch(i -> i.classification() == ContentReleaseIssueClassification.MANUAL_REVIEW);
        });
        assertThat(result.issueDetails().get("EXAMPLE_MISSING").message()).isNotBlank();
        assertThat(dryRun.executionSnapshot(contents.findAllById(result.targetIds()).stream()
                .sorted(java.util.Comparator.comparing(ContentItem::getId)).toList()).digest()).isEqualTo(result.digest());
    }

    @Test
    void appliesTypeAndJlptScopeBeforeEvaluatingTargets() {
        ContentItem n5 = saveWord("n5", "N5", true);
        saveWord("n4", "N4", false);
        saveGrammar("grammar", "N5");
        assertThat(sources.findBySourceRef(allowed).orElseThrow().getRightsStatus())
                .isEqualTo(ContentSourceRightsStatus.ALLOWED);

        var result = dryRun.run(new Filter(ContentType.WORD, "N5", null, false, allowed, ContentSourceRightsStatus.ALLOWED));

        assertThat(result.totalTargetCount()).isEqualTo(1);
        assertThat(result.targetIds()).containsExactly(n5.getId());
        assertThat(result.samples().get(0).contentItemId()).isEqualTo(n5.getId());
        assertThat(result.decisionCounts().releasable()).isEqualTo(1);
        assertThat(result.decisionCounts().manualReviewRequired() + result.decisionCounts().blocked())
                .isEqualTo(0);
    }

    @Test
    void aggregatesDecisionsIssuesAndRightsWithoutChangingContent() {
        ContentItem complete = saveWord("complete", "N5", true);
        ContentItem missing = saveWord("missing", "N5", false);
        missing.getWord().getMeanings().clear();
        missing.getWord().addMeaning(new Meaning("en", "English only", 0));
        ContentItem before = contents.findById(complete.getId()).orElseThrow();
        boolean published = before.isPublished();
        var result = dryRun.run(new Filter(ContentType.WORD, "N5", null, false, allowed, null));

        assertThat(result.totalTargetCount()).isEqualTo(2);
        assertThat(result.decisionCounts().releasable() + result.decisionCounts().manualReviewRequired()
                + result.decisionCounts().blocked()).isEqualTo(result.totalTargetCount());
        assertThat(result.decisionCounts().blocked()).isGreaterThanOrEqualTo(1);
        assertThat(result.issueCounts()).containsKey(ContentReleaseIssueCode.WORD_KOREAN_MEANING_MISSING.name());
        assertThat(result.sourceRightsCounts().get(ContentSourceRightsStatus.ALLOWED.name())).isEqualTo(2);
        assertThat(result.digest()).hasSize(64);
        assertThat(contents.findById(complete.getId()).orElseThrow().isPublished()).isEqualTo(published);
        assertThat(contents.findById(complete.getId()).orElseThrow().getReviewStatus()).isEqualTo(before.getReviewStatus());
    }

    @Test
    void digestAndSamplesAreDeterministicForSameState() {
        saveWord("stable", "N5", true);
        Filter filter = new Filter(ContentType.WORD, "N5", null, false, allowed, null);

        var first = dryRun.run(filter);
        var second = dryRun.run(filter);

        assertThat(second.digest()).isEqualTo(first.digest());
        assertThat(second.samples()).isEqualTo(first.samples());
        assertThat(second.blockedSamples()).isEqualTo(first.blockedSamples());
        assertThat(second.manualReviewSamples()).isEqualTo(first.manualReviewSamples());
    }

    @Test
    void targetChangesChangeDigest() {
        Filter filter = new Filter(ContentType.WORD, "N5", null, false, allowed, null);
        String before = dryRun.run(filter).digest();
        saveWord("added", "N5", true);
        assertThat(dryRun.run(filter).digest()).isNotEqualTo(before);
    }

    private ContentItem saveWord(String name, String levelCode, boolean withExample) {
        ContentItem item = new ContentItem("dry-" + name + "-" + UUID.randomUUID(), ContentType.WORD, allowed, false);
        Word word = new Word(name, "reading-" + name, "noun", null);
        word.addMeaning(new Meaning("ko", "한국어 뜻", 0));
        item.attachWord(word);
        item.addLevel(levels.findBySystemAndCode("JLPT", levelCode)
                .orElseGet(() -> levels.save(new com.japanese.content.entity.Level("JLPT", levelCode, levelCode))));
        if (withExample) item.addExample(new Example("Example", null, "예문", 0));
        return contents.saveAndFlush(item);
    }

    private ContentItem saveGrammar(String name, String levelCode) {
        ContentItem item = new ContentItem("dry-grammar-" + name + "-" + UUID.randomUUID(), ContentType.GRAMMAR, allowed, false);
        item.attachGrammar(new com.japanese.content.entity.Grammar(name, "A sufficiently complete explanation", null));
        item.addLevel(levels.findBySystemAndCode("JLPT", levelCode)
                .orElseGet(() -> levels.save(new com.japanese.content.entity.Level("JLPT", levelCode, levelCode))));
        return contents.saveAndFlush(item);
    }
}
