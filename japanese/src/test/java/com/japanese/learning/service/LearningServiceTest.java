package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.config.SampleContentDataLoader;
import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.learning.dto.StudyAnswer;
import com.japanese.learning.dto.StudyOverview;
import com.japanese.learning.dto.DailyLearningProgress;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.entity.LearnerProfile;
import com.japanese.learning.entity.LearningProgress;
import com.japanese.learning.entity.RelearningTarget;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.service.BookmarkService;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import com.japanese.learning.repository.LearnerStudyPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("sample")
class LearningServiceTest {

    @Autowired
    private SampleContentDataLoader sampleContentDataLoader;

    @Autowired
    private LearningService learningService;
    @Autowired
    private LearningAnalyticsService learningAnalyticsService;
    @Autowired
    private StreakService streakService;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private LearnerProfileRepository learnerProfileRepository;
    @Autowired private LearningProgressRepository learningProgressRepository;
    @Autowired private ContentItemRepository contentItemRepository;
    @Autowired private StudyRecordRepository studyRecordRepository;
    @Autowired private LearnerStudyPreferenceRepository learnerStudyPreferenceRepository;
    @Autowired private BookmarkService bookmarkService;
    private UserAccount account;

    @BeforeEach
    void seedSampleContent() throws Exception {
        sampleContentDataLoader.run();
        String loginId = "learning-test-" + UUID.randomUUID();
        account = userAccountRepository.save(new UserAccount(
                loginId, null, "not-used-in-service-test", "테스트 학습자", UserRole.USER));
    }

    @Test
    void recordsAnswerAndAddsExperienceToLearner() {
        StudyOverview before = learningService.overview(account);

        StudyAnswer answer = learningService.answer(account, "taberu", StudyResult.CORRECT);

        assertThat(answer.earnedExperience()).isEqualTo(10);
        assertThat(answer.overview().totalAnswers()).isEqualTo(1);
        assertThat(answer.overview().correctAnswers()).isEqualTo(1);
        assertThat(answer.overview().character().experience()).isEqualTo(before.character().experience() + 10);
        assertThat(answer.overview().character().level()).isEqualTo(1);
        assertThat(answer.overview().character().stageKey()).isEqualTo("young");
        assertThat(answer.overview().character().characterKey()).isEqualTo("haru");
        assertThat(answer.overview().character().experienceToNextStage()).isEqualTo(290);
        assertThat(answer.overview().character().growthNoticePending()).isFalse();
        assertThat(learningService.recentHistory(account, 5))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.slug()).isEqualTo("taberu");
                    assertThat(entry.result()).isEqualTo(StudyResult.CORRECT);
                });
        DailyLearningProgress today = learningService.todayProgress(account);
        assertThat(today.completed()).isEqualTo(1);
        assertThat(today.correctAnswers()).isEqualTo(1);
        assertThat(today.goal()).isEqualTo(10);
        assertThat(learningAnalyticsService.levelProgress(account))
                .singleElement()
                .satisfies(progress -> {
                    assertThat(progress.levelCode()).isEqualTo("N5");
                    assertThat(progress.learnedContentCount()).isEqualTo(1);
                    assertThat(progress.dueReviewCount()).isEqualTo(0);
                });
        assertThat(learningAnalyticsService.weeklyActivity(account))
                .filteredOn(day -> day.date().equals(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))))
                .singleElement()
                .extracting(day -> day.count())
                .isEqualTo(1L);
        assertThat(streakService.status(account))
                .satisfies(streak -> {
                    assertThat(streak.currentStreak()).isEqualTo(1);
                    assertThat(streak.longestStreak()).isEqualTo(1);
                    assertThat(streak.studiedToday()).isTrue();
                });
    }

    @Test
    void returnsOnlyPublishedContentAsStudyCards() {
        assertThat(learningService.cards(account, null, null, false))
                .extracting(value -> value.slug())
                .containsExactly("taberu", "temo-ii", "server");
    }

    @Test
    void appliesLearningScopeAndExcludesAlreadyLearnedCards() {
        learningService.updateLearningScope(account, List.of("JLPT:N5"), List.of("daily-life"));

        assertThat(learningService.todayPlan(account).items())
                .extracting(value -> value.slug())
                .containsExactly("taberu", "temo-ii");

        learningService.answer(account, "taberu", StudyResult.CORRECT);

        assertThat(learningService.cards(account, "N5", null, false))
                .extracting(value -> value.slug())
                .containsExactly("temo-ii");
        assertThat(learningService.todayPlan(account).items())
                .extracting(value -> value.slug())
                .containsExactly("temo-ii");
    }

    @Test
    void growsCharacterAndKeepsSelectionPerUser() {
        UserAccount growthAccount = userAccountRepository.findByLoginId("character-growth-test")
                .orElseGet(() -> userAccountRepository.save(new UserAccount(
                        "character-growth-test", null, "not-used-in-service-test", "성장 테스트", UserRole.USER)));

        for (int index = 0; index < 30; index++) {
            learningService.recordQuizAnswer(growthAccount, 10_000L + index, true);
        }

        assertThat(learningService.overview(growthAccount).character())
                .satisfies(character -> {
                    assertThat(character.experience()).isEqualTo(300);
                    assertThat(character.stageKey()).isEqualTo("apprentice");
                    assertThat(character.growthNoticePending()).isTrue();
                });
        learningService.updateCharacter(growthAccount, "mio");
        learningService.acknowledgeGrowth(growthAccount);
        assertThat(learningService.overview(growthAccount).character())
                .satisfies(character -> {
                    assertThat(character.characterKey()).isEqualTo("mio");
                    assertThat(character.growthNoticePending()).isFalse();
                });
    }

    @Test
    void quizActivityDoesNotConsumeTheDailyContentGoal() {
        learningService.recordQuizAnswer(account, 42_000L, true);

        assertThat(learningService.todayProgress(account).completed()).isZero();
        assertThat(learningService.todayProgress(account).remaining()).isEqualTo(10);
        assertThat(learningService.recentQuizHistory(account, 5)).hasSize(1);
    }

    @Test
    void duplicateAnswerInTheSameSessionIsAppliedOnlyOnce() {
        StudyAnswer first = learningService.answer(account, "taberu", StudyResult.CORRECT, "today-session-1", false);
        StudyAnswer duplicate = learningService.answer(account, "taberu", StudyResult.INCORRECT, "today-session-1", false);

        assertThat(first.earnedExperience()).isEqualTo(10);
        assertThat(duplicate.earnedExperience()).isZero();
        assertThat(duplicate.result()).isEqualTo(StudyResult.CORRECT);
        assertThat(learningService.overview(account).character().experience()).isEqualTo(10);
        assertThat(learningService.todayProgress(account).completed()).isEqualTo(1);
        String learnerKey = learnerProfileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        assertThat(studyRecordRepository.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(learnerKey)).hasSize(1);
    }

    @Test
    void aNewSessionCanReviewTheSameContentNormally() {
        learningService.answer(account, "taberu", StudyResult.CORRECT, "session-day-one", false);
        StudyAnswer nextSession = learningService.answer(account, "taberu", StudyResult.INCORRECT, "session-day-two", false);

        assertThat(nextSession.earnedExperience()).isEqualTo(2);
        assertThat(learningService.todayProgress(account).completed()).isEqualTo(2);
        String learnerKey = learnerProfileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        assertThat(studyRecordRepository.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(learnerKey)).hasSize(2);
    }

    @Test
    void limitsTodayPlanToTheDailyGoalAndReportsReviewBacklog() {
        learningService.updateDailyGoal(account, 2);
        LearnerProfile profile = learnerProfileRepository.findByUserAccountLoginId(account.getLoginId()).orElseGet(() ->
                learnerProfileRepository.save(new LearnerProfile(account, 2, "haru")));
        for (String slug : List.of("taberu", "temo-ii", "server")) {
            ContentItem item = contentItemRepository.findBySlugAndPublishedTrue(slug).orElseThrow();
            LearningProgress progress = learningProgressRepository.save(new LearningProgress(profile, item, StudyResult.CORRECT));
            ReflectionTestUtils.setField(progress, "nextReviewAt", Instant.now().minusSeconds(60));
            learningProgressRepository.saveAndFlush(progress);
        }

        var plan = learningService.todayPlan(account);

        assertThat(plan.reviewCount()).isEqualTo(2);
        assertThat(plan.totalCount()).isEqualTo(2);
        assertThat(plan.totalDueReviewCount()).isEqualTo(3);
        assertThat(plan.remainingDueReviewCount()).isEqualTo(1);
        assertThat(plan.newWordCount() + plan.newGrammarCount()).isZero();
    }

    @Test
    void appliesPerTypeNewContentLimitsAndKeepsDefaultsForExistingPreferences() {
        assertThat(learningService.studyPreferences(account).dailyNewWordLimit()).isEqualTo(5);
        assertThat(learningService.studyPreferences(account).dailyNewGrammarLimit()).isEqualTo(5);

        learningService.updateNewContentLimits(account, 1, 0);

        assertThat(learningService.todayPlan(account).items())
                .extracting(value -> value.slug())
                .containsExactly("taberu");
    }

    @Test
    void readsDefaultPreferencesForLegacyProfileWithoutWritingInReadOnlyFlow() {
        var legacy = userAccountRepository.saveAndFlush(new UserAccount("legacy-preferences-" + UUID.randomUUID(), null,
                "hash", "기존 사용자", UserRole.USER));
        learningService.overview(legacy);
        var profile = learnerProfileRepository.findByUserAccountLoginId(legacy.getLoginId()).orElseThrow();
        assertThat(learnerStudyPreferenceRepository.findByLearnerProfileId(profile.getId())).isEmpty();

        var values = learningService.studyPreferences(legacy);

        assertThat(values.dailyNewWordLimit()).isEqualTo(5);
        assertThat(values.dailyNewGrammarLimit()).isEqualTo(5);
        assertThat(learnerStudyPreferenceRepository.findByLearnerProfileId(profile.getId())).isEmpty();
    }

    @Test
    void bookmarkAndWeaknessRetrainingUpdatesSrsWithoutFarmingExpOrDailyGoal() {
        learningService.answer(account, "taberu", StudyResult.CORRECT, "regular-bookmark", false);
        bookmarkService.toggle(account, "taberu");
        assertThat(learningService.relearningCards(account, RelearningTarget.BOOKMARKS))
                .extracting(value -> value.slug()).containsExactly("taberu");

        int beforeExperience = learningService.overview(account).character().experience();
        learningService.answer(account, "taberu", StudyResult.CORRECT, "bookmark-retrain", true);
        StudyAnswer duplicateRetrain = learningService.answer(account, "taberu", StudyResult.CORRECT, "bookmark-retrain", true);

        assertThat(duplicateRetrain.earnedExperience()).isZero();
        assertThat(learningService.overview(account).character().experience()).isEqualTo(beforeExperience);
        assertThat(learningService.todayProgress(account).completed()).isEqualTo(1);

        learningService.answer(account, "temo-ii", StudyResult.INCORRECT, "regular-weakness", false);
        assertThat(learningService.relearningCards(account, RelearningTarget.WEAKNESSES))
                .extracting(value -> value.slug()).contains("temo-ii");
        learningService.answer(account, "temo-ii", StudyResult.CORRECT, "weakness-retrain", true);
        assertThat(learningService.todayProgress(account).completed()).isEqualTo(2);
        assertThat(learningService.overview(account).character().experience()).isEqualTo(beforeExperience + 2);
    }
}
