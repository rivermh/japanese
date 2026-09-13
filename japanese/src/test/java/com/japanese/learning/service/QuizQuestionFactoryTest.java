package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.entity.*;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.*;
import com.japanese.content.repository.*;
import com.japanese.content.service.GrammarCurationService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class QuizQuestionFactoryTest {
    @Autowired private SampleContentDataLoader sample;
    @Autowired private UserAccountRepository accounts;
    @Autowired private ContentItemRepository contents;
    @Autowired private GrammarCurationService curation;
    @Autowired private GrammarConfirmationQuestionRepository confirmationQuestions;
    @Autowired private LearningService learning;
    @Autowired private QuizQuestionFactory factory;
    @Autowired private QuizAnswerEvaluator evaluator;
    private UserAccount account;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        if (contents.findBySlug("quiz-grammar-second").isEmpty()) {
            ContentItem grammar = new ContentItem("quiz-grammar-second", ContentType.GRAMMAR, "test", true);
            grammar.attachGrammar(new Grammar("〜ながら", "두 동작을 동시에 할 때 사용합니다.", "동사 ます형 + ながら"));
            contents.saveAndFlush(grammar);
        }
        account = accounts.saveAndFlush(new UserAccount("quiz-factory-" + UUID.randomUUID(), null, "hash", "Quiz", UserRole.USER));
        learning.overview(account);
    }

    @Test
    void buildsReliableWordAndGrammarTypesWithUniqueDeterministicChoices() {
        var first = factory.create(account, "stable-seed", 20);
        var second = factory.create(account, "stable-seed", 20);

        assertThat(first.stream().map(value -> value.type())).contains(
                com.japanese.learning.entity.QuizQuestionType.WORD_JAPANESE_TO_MEANING,
                com.japanese.learning.entity.QuizQuestionType.WORD_MEANING_TO_JAPANESE,
                com.japanese.learning.entity.QuizQuestionType.WORD_READING_CHOICE,
                com.japanese.learning.entity.QuizQuestionType.WORD_READING_INPUT,
                com.japanese.learning.entity.QuizQuestionType.GRAMMAR_PATTERN_TO_MEANING,
                com.japanese.learning.entity.QuizQuestionType.GRAMMAR_MEANING_TO_PATTERN);
        assertThat(first.stream().map(value -> value.content().getId() + ":" + value.type()))
                .containsExactlyElementsOf(second.stream().map(value -> value.content().getId() + ":" + value.type()).toList());
        first.forEach(question -> assertThat(question.choices().stream()
                .map(evaluator::normalizeText).distinct().count()).isEqualTo(question.choices().size()));
    }

    @Test
    void fallsBackToReadingInputWhenDistractorsAreUnavailableAndExcludesUnpublishedContent() {
        ContentItem only = new ContentItem("quiz-only-reading", ContentType.WORD, "test", true);
        Word word = new Word("唯一", "ゆいいつ", "명사", null);
        word.addMeaning(new Meaning("en", "unique", 1));
        only.attachWord(word);
        contents.saveAndFlush(only);
        ContentItem pending = new ContentItem("quiz-pending", ContentType.WORD, "test", false);
        Word pendingWord = new Word("未公開", "みこうかい", "명사", null);
        pendingWord.addMeaning(new Meaning("ko", "미공개", 1)); pending.attachWord(pendingWord);
        contents.saveAndFlush(pending);

        var generated = factory.create(account, "fallback", 20);
        assertThat(generated).noneMatch(value -> value.content().getSlug().equals("quiz-pending"));
        assertThat(generated).anyMatch(value -> value.content().getSlug().equals("quiz-only-reading")
                && value.type() == com.japanese.learning.entity.QuizQuestionType.WORD_READING_INPUT);
    }

    @Test
    void reusesOnlyApprovedPublishedGrammarConfirmationQuestions() {
        var approved = curation.saveQuestion("temo-ii", GrammarConfirmationType.CONTEXT_GAP,
                "빈칸을 고르세요.", "ここで写真を撮って＿＿。", "허가 표현입니다.", "quiz:approved",
                List.of(new GrammarCurationService.ChoiceDraft("もいい", true), new GrammarCurationService.ChoiceDraft("はいけない", false)));
        approved.approveForPublication(); confirmationQuestions.saveAndFlush(approved);
        curation.saveQuestion("temo-ii", GrammarConfirmationType.CONTEXT_GAP,
                "노출되면 안 됩니다.", "PENDING CONTEXT", "pending", "quiz:pending",
                List.of(new GrammarCurationService.ChoiceDraft("A", true), new GrammarCurationService.ChoiceDraft("B", false)));

        var generated = factory.create(account, "confirmation", 20);
        assertThat(generated).anyMatch(value -> value.type() == com.japanese.learning.entity.QuizQuestionType.GRAMMAR_CONTEXT_CHOICE
                && value.prompt().contains("빈칸을 고르세요"));
        assertThat(generated).noneMatch(value -> value.prompt().contains("PENDING CONTEXT"));
    }
}
