package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Grammar;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class LearningOrganizationServiceTest {

    @Autowired private SampleContentDataLoader sampleContentDataLoader;
    @Autowired private UserAccountRepository accountRepository;
    @Autowired private ContentItemRepository contentRepository;
    @Autowired private LearnerProfileRepository profileRepository;
    @Autowired private LearningProgressRepository progressRepository;
    @Autowired private LearningService learningService;
    @Autowired private StudyQueueService queueService;
    @Autowired private StudyCollectionService collectionService;
    @Autowired private LearningHistoryService historyService;

    @BeforeEach
    void seed() throws Exception {
        sampleContentDataLoader.run();
    }

    @Test
    void queueDoesNotCreateLearningDataAndExplicitQueueOverridesScopeForNewContent() {
        UserAccount account = account("queue-owner");
        learningService.overview(account);
        learningService.updateLearningScope(account, List.of("JLPT:N5"), List.of("daily-life"));
        int experienceBefore = learningService.overview(account).character().experience();
        String key = profileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        Long serverId = contentRepository.findBySlugAndPublishedTrue("server").orElseThrow().getId();

        assertThat(queueService.add(account, "server").queued()).isTrue();
        assertThat(queueService.list(account)).extracting(item -> item.content().slug()).containsExactly("server");
        assertThat(progressRepository.findByLearnerProfileLearnerKeyAndContentItemId(key, serverId)).isEmpty();
        assertThat(learningService.overview(account).character().experience()).isEqualTo(experienceBefore);
        assertThat(learningService.todayProgress(account).completed()).isZero();

        var plan = learningService.todayPlan(account);
        assertThat(plan.items()).extracting(item -> item.slug()).startsWith("server");
        assertThat(plan.newWordCount()).isLessThanOrEqualTo(5);

        learningService.answer(account, "server", StudyResult.CORRECT, "queued-server", false);
        assertThat(queueService.list(account)).isEmpty();
        assertThat(progressRepository.findByLearnerProfileLearnerKeyAndContentItemId(key, serverId)).isPresent();
    }

    @Test
    void queuedWordAndGrammarAreBalancedBeforeGeneralCandidates() {
        UserAccount account = account("balanced-queue-" + java.util.UUID.randomUUID());
        learningService.overview(account);
        learningService.updateDailyGoal(account, 2);
        learningService.updateNewContentLimits(account, 2, 2);
        ContentItem word = word("queued-word-" + java.util.UUID.randomUUID());
        ContentItem grammar = grammar("queued-grammar-" + java.util.UUID.randomUUID());
        contentRepository.saveAndFlush(word);
        contentRepository.saveAndFlush(grammar);
        queueService.add(account, word.getSlug());
        queueService.add(account, grammar.getSlug());

        var plan = learningService.todayPlan(account);

        assertThat(plan.newWordCount()).isEqualTo(1);
        assertThat(plan.newGrammarCount()).isEqualTo(1);
        assertThat(plan.items()).extracting(item -> item.slug())
                .containsExactly(word.getSlug(), grammar.getSlug());
    }

    @Test
    void queueIsPrivateAndAlreadyLearningContentIsNotQueuedAgain() {
        UserAccount owner = account("queue-private-owner");
        UserAccount other = account("queue-private-other");
        queueService.add(owner, "taberu");

        assertThat(queueService.list(other)).isEmpty();
        learningService.answer(owner, "taberu", StudyResult.CORRECT, "queue-learned", false);
        assertThat(queueService.status(owner, "taberu").canAdd()).isFalse();
        assertThat(queueService.add(owner, "taberu").queued()).isFalse();
    }

    @Test
    void collectionSeparatesOrganizationFromBookmarksAndUsesRetrainingRules() {
        UserAccount owner = account("collection-owner");
        UserAccount other = account("collection-other");
        var first = collectionService.create(owner, "Interview");
        var second = collectionService.create(owner, "Travel");
        collectionService.addContent(owner, first.id(), "taberu");
        collectionService.addContent(owner, second.id(), "taberu");

        assertThat(collectionService.details(owner, first.id()).contents()).extracting(item -> item.slug())
                .containsExactly("taberu");
        assertThat(collectionService.details(owner, second.id()).contents()).extracting(item -> item.slug())
                .containsExactly("taberu");
        assertThatThrownBy(() -> collectionService.details(other, first.id()))
                .isInstanceOf(java.util.NoSuchElementException.class);

        int experienceBefore = learningService.overview(owner).character().experience();
        long dailyBefore = learningService.todayProgress(owner).completed();
        learningService.answer(owner, "taberu", StudyResult.CORRECT, "collection-session", true);
        assertThat(learningService.overview(owner).character().experience()).isEqualTo(experienceBefore);
        assertThat(learningService.todayProgress(owner).completed()).isEqualTo(dailyBefore);
    }

    @Test
    void dailyReportAndMonthUsePersistedRecordsInsteadOfHttpSessionState() {
        UserAccount account = account("history-owner");
        learningService.answer(account, "taberu", StudyResult.CORRECT, "history-new", false);
        learningService.answer(account, "taberu", StudyResult.CORRECT, "history-new", false);
        learningService.answer(account, "taberu", StudyResult.INCORRECT, "history-retrain", true);
        learningService.recordQuizAnswer(account, 1L, true);

        var report = historyService.day(account, LocalDate.now());
        assertThat(report.newWordCount()).isEqualTo(1);
        assertThat(report.retrainCount()).isEqualTo(1);
        assertThat(report.quizAttemptCount()).isEqualTo(1);
        assertThat(report.earnedExperience()).isEqualTo(10);
        assertThat(report.quizExperience()).isEqualTo(10);
        assertThat(report.activities()).hasSize(2);

        var month = historyService.month(account, YearMonth.now());
        assertThat(month.days()).filteredOn(day -> day.date().equals(LocalDate.now()))
                .singleElement().satisfies(day -> assertThat(day.totalActivityCount()).isEqualTo(3));
    }

    private UserAccount account(String loginId) {
        return accountRepository.save(new UserAccount(loginId, loginId + "@example.test", "hash", loginId, UserRole.USER));
    }

    private ContentItem word(String slug) {
        ContentItem item = new ContentItem(slug, ContentType.WORD, "test", true);
        Word word = new Word(slug, slug, "noun", null);
        word.addMeaning(new Meaning("ko", slug, 1));
        item.attachWord(word);
        return item;
    }

    private ContentItem grammar(String slug) {
        ContentItem item = new ContentItem(slug, ContentType.GRAMMAR, "test", true);
        item.attachGrammar(new Grammar(slug, slug, null));
        return item;
    }
}
