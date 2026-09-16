package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Example;
import com.japanese.content.entity.Grammar;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentSourceRepository;
import com.japanese.content.repository.LevelRepository;
import java.util.List;
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
class ContentReleaseGateServiceTest {

    @Autowired ContentReleaseGateService gate;
    @Autowired ContentSourceRightsService rights;
    @Autowired ContentSourceRepository sources;
    @Autowired ContentItemRepository contents;
    @Autowired LevelRepository levels;
    @Autowired SampleContentDataLoader sample;

    private String allowedSource;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        allowedSource = "release-gate-" + UUID.randomUUID();
        ContentSource source = sources.save(new ContentSource(
                allowedSource, "Release gate source", "1", null, null, null, null));
        rights.reviewRights(source.getId(), ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,
                "Rights evidence collected", false, null);
        rights.reviewRights(source.getId(), ContentSourceRightsStatus.ALLOWED,
                "Redistribution allowed", false, null);
    }

    @Test
    void completeWordIsReleasableWithoutPitchAudioOrExampleReading() {
        ContentReleaseGateService.Result result = gate.evaluate(save(validWord("complete-word", allowedSource, true)));

        assertThat(result.decision()).isEqualTo(ContentReleaseDecision.RELEASABLE);
        assertThat(result.blockers()).isEmpty();
        assertThat(result.manualReview()).isEmpty();
    }

    @Test
    void wordCoreFieldsAndCriticalParsingDamageAreBlockers() {
        assertBlocked(wordWithoutEntity(), ContentReleaseIssueCode.WORD_ENTITY_MISSING);
        assertBlocked(word(" ", "reading", "noun", true), ContentReleaseIssueCode.WORD_EXPRESSION_MISSING);
        assertBlocked(word("word", " ", "noun", true), ContentReleaseIssueCode.WORD_READING_MISSING);
        assertBlocked(word(" ", "reading", "noun", true), ContentReleaseIssueCode.WORD_SEARCH_KEY_MISSING);

        ContentItem noKoreanMeaning = word("meaning", "reading", "noun", true);
        noKoreanMeaning.getWord().getMeanings().clear();
        noKoreanMeaning.getWord().addMeaning(new Meaning("en", "meaning", 0));
        assertBlocked(noKoreanMeaning, ContentReleaseIssueCode.WORD_KOREAN_MEANING_MISSING);

        ContentItem noLevel = validWord("no-level", allowedSource, true);
        noLevel.getLevels().clear();
        assertBlocked(noLevel, ContentReleaseIssueCode.JLPT_LEVEL_MISSING);
        assertBlocked(word("broken\ufffd", "reading", "noun", true),
                ContentReleaseIssueCode.CONTENT_PARSING_DAMAGE);
    }

    @Test
    void sourceRightsMustBeAllowed() {
        for (ContentSourceRightsStatus status : List.of(
                ContentSourceRightsStatus.UNKNOWN,
                ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,
                ContentSourceRightsStatus.BLOCKED)) {
            String ref = sourceAt(status);
            ContentReleaseGateService.Result result = gate.evaluate(save(validWord("rights-" + status, ref, true)));
            assertThat(result.decision()).isEqualTo(ContentReleaseDecision.BLOCKED);
            assertThat(codes(result.blockers())).contains(ContentReleaseIssueCode.SOURCE_RIGHTS_NOT_ALLOWED);
        }
    }

    @Test
    void wordReviewHintsAreManualAndPitchOrAudioAbsenceIsNotAnIssue() {
        ContentItem noExample = save(validWord("no-example", allowedSource, false));
        assertManual(gate.evaluate(noExample), ContentReleaseIssueCode.EXAMPLE_MISSING);

        ContentItem noTranslation = validWord("no-translation", allowedSource, false);
        noTranslation.addExample(new Example("Example", null, " ", 0));
        assertManual(gate.evaluate(save(noTranslation)), ContentReleaseIssueCode.EXAMPLE_TRANSLATION_MISSING);

        ContentItem noPos = word("no-pos", "reading", " ", true);
        assertManual(gate.evaluate(save(noPos)), ContentReleaseIssueCode.WORD_PART_OF_SPEECH_MISSING);

        ContentItem longReading = word("long-reading", "a".repeat(51), "noun", true);
        assertManual(gate.evaluate(save(longReading)), ContentReleaseIssueCode.WORD_READING_TOO_LONG);

        ContentItem markup = word("markup<", "reading", "noun", true);
        assertManual(gate.evaluate(save(markup)), ContentReleaseIssueCode.MARKUP_SUSPECTED);
    }

    @Test
    void duplicateWordIsManualReviewRatherThanBlocked() {
        save(word("duplicate-word", "duplicate-reading", "noun", true));
        ContentReleaseGateService.Result result = gate.evaluate(
                save(word("duplicate-word", "duplicate-reading", "noun", true)));

        assertManual(result, ContentReleaseIssueCode.DUPLICATE_CANDIDATE);
        assertThat(result.blockers()).isEmpty();
    }

    @Test
    void completeGrammarIsReleasableWithoutOptionalCurationDataOrConnection() {
        ContentReleaseGateService.Result result = gate.evaluate(save(validGrammar("complete-grammar", allowedSource, true)));

        assertThat(result.decision()).isEqualTo(ContentReleaseDecision.RELEASABLE);
        assertThat(result.blockers()).isEmpty();
        assertThat(result.manualReview()).isEmpty();
    }

    @Test
    void grammarCoreFieldsLevelAndSourceRightsAreBlockers() {
        assertBlocked(grammarWithoutEntity(), ContentReleaseIssueCode.GRAMMAR_ENTITY_MISSING);
        assertBlocked(grammar(" ", "This explanation is valid", true),
                ContentReleaseIssueCode.GRAMMAR_PATTERN_MISSING);
        assertBlocked(grammar("pattern", " ", true),
                ContentReleaseIssueCode.GRAMMAR_EXPLANATION_MISSING);

        ContentItem noLevel = validGrammar("grammar-no-level", allowedSource, true);
        noLevel.getLevels().clear();
        assertBlocked(noLevel, ContentReleaseIssueCode.JLPT_LEVEL_MISSING);

        ContentItem unknown = validGrammar("grammar-unknown", sourceAt(ContentSourceRightsStatus.UNKNOWN), true);
        assertBlocked(unknown, ContentReleaseIssueCode.SOURCE_RIGHTS_NOT_ALLOWED);
    }

    @Test
    void grammarReviewHintsAreManualRatherThanBlockers() {
        assertManual(gate.evaluate(save(validGrammar("grammar-no-example", allowedSource, false))),
                ContentReleaseIssueCode.EXAMPLE_MISSING);

        ContentItem noTranslation = validGrammar("grammar-no-translation", allowedSource, false);
        noTranslation.addExample(new Example("Example", null, " ", 0));
        assertManual(gate.evaluate(save(noTranslation)), ContentReleaseIssueCode.EXAMPLE_TRANSLATION_MISSING);

        ContentItem shortDescription = grammar("short-pattern", "short", true);
        assertManual(gate.evaluate(save(shortDescription)), ContentReleaseIssueCode.GRAMMAR_DESCRIPTION_TOO_SHORT);

        ContentItem markup = grammar("markup<", "This explanation is valid", true);
        assertManual(gate.evaluate(save(markup)), ContentReleaseIssueCode.MARKUP_SUSPECTED);
    }

    @Test
    void duplicateGrammarIsManualReviewRatherThanBlocked() {
        save(grammar("duplicate-pattern", "A sufficiently complete grammar explanation", true));
        ContentReleaseGateService.Result result = gate.evaluate(
                save(grammar("duplicate-pattern", "A sufficiently complete grammar explanation", true)));

        assertManual(result, ContentReleaseIssueCode.DUPLICATE_CANDIDATE);
        assertThat(result.blockers()).isEmpty();
    }

    private ContentItem validWord(String slug, String sourceRef, boolean example) {
        ContentItem item = new ContentItem(slug + "-" + UUID.randomUUID(), ContentType.WORD, sourceRef, false);
        Word word = new Word("word-" + slug, "reading-" + slug, "noun", null);
        word.addMeaning(new Meaning("ko", "Korean meaning", 0));
        item.attachWord(word);
        addN5(item);
        if (example) item.addExample(new Example("Example sentence", null, "Example translation", 0));
        return item;
    }

    private ContentItem validGrammar(String slug, String sourceRef, boolean example) {
        ContentItem item = new ContentItem(slug + "-" + UUID.randomUUID(), ContentType.GRAMMAR, sourceRef, false);
        item.attachGrammar(new Grammar("pattern-" + slug, "A sufficiently complete grammar explanation", null));
        addN5(item);
        if (example) item.addExample(new Example("Example sentence", null, "Example translation", 0));
        return item;
    }

    private ContentItem word(String expression, String reading, String partOfSpeech, boolean example) {
        ContentItem item = validWord("direct-" + UUID.randomUUID(), allowedSource, example);
        Word word = new Word(expression, reading, partOfSpeech, null);
        word.addMeaning(new Meaning("ko", "Korean meaning", 0));
        item.replaceWord(word);
        return item;
    }

    private ContentItem grammar(String pattern, String explanation, boolean example) {
        ContentItem item = validGrammar("direct-" + UUID.randomUUID(), allowedSource, example);
        item.replaceGrammar(new Grammar(pattern, explanation, null));
        return item;
    }

    private ContentItem wordWithoutEntity() {
        ContentItem item = new ContentItem("word-no-entity-" + UUID.randomUUID(), ContentType.WORD, allowedSource, false);
        addN5(item);
        return item;
    }

    private ContentItem grammarWithoutEntity() {
        ContentItem item = new ContentItem("grammar-no-entity-" + UUID.randomUUID(), ContentType.GRAMMAR, allowedSource, false);
        addN5(item);
        return item;
    }

    private void addN5(ContentItem item) {
        item.addLevel(levels.findBySystemAndCode("JLPT", "N5").orElseThrow());
    }

    private ContentItem save(ContentItem item) {
        return contents.saveAndFlush(item);
    }

    private String sourceAt(ContentSourceRightsStatus status) {
        String ref = "release-source-" + status + "-" + UUID.randomUUID();
        ContentSource source = sources.save(new ContentSource(ref, ref, "1", null, null, null, null));
        if (status != ContentSourceRightsStatus.UNKNOWN) {
            rights.reviewRights(source.getId(), ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,
                    "Manual review", false, null);
        }
        if (status == ContentSourceRightsStatus.BLOCKED) {
            rights.reviewRights(source.getId(), ContentSourceRightsStatus.BLOCKED,
                    "Redistribution blocked", false, null);
        }
        return ref;
    }

    private void assertBlocked(ContentItem item, ContentReleaseIssueCode code) {
        ContentReleaseGateService.Result result = item.getId() == null
                ? gate.evaluate(item, new ContentQualityAuditService.Audit(List.of()))
                : gate.evaluate(item);
        assertThat(result.decision()).isEqualTo(ContentReleaseDecision.BLOCKED);
        assertThat(codes(result.blockers())).contains(code);
    }

    private void assertManual(ContentReleaseGateService.Result result, ContentReleaseIssueCode code) {
        assertThat(result.decision()).isEqualTo(ContentReleaseDecision.MANUAL_REVIEW_REQUIRED);
        assertThat(codes(result.manualReview())).contains(code);
    }

    private List<ContentReleaseIssueCode> codes(List<ContentReleaseGateService.Issue> issues) {
        return issues.stream().map(ContentReleaseGateService.Issue::code).toList();
    }
}
