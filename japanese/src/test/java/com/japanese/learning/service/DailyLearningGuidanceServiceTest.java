package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.entity.LearningProgress;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("sample")
class DailyLearningGuidanceServiceTest {
    @Autowired private SampleContentDataLoader sample;
    @Autowired private DailyMissionService missions;
    @Autowired private LearningGuidanceService guidance;
    @Autowired private ReminderPreferenceService reminders;
    @Autowired private TodayStudySessionService sessions;
    @Autowired private LearningService learning;
    @Autowired private UserAccountRepository accounts;
    @Autowired private LearnerProfileRepository profiles;
    @Autowired private LearningProgressRepository progress;
    @Autowired private ContentItemRepository content;
    private UserAccount account;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        account = accounts.saveAndFlush(new UserAccount(
                "daily-guidance-" + UUID.randomUUID(), null, "hash", "Daily learner", UserRole.USER));
        learning.overview(account);
        learning.updateNewContentLimits(account, 1, 1);
    }

    @Test
    void missionUsesFixedTodaySnapshotAndDuplicateCompletionCannotIncreaseProgress() {
        var before = missions.today(account);
        assertThat(before.sessionSnapshot()).isFalse();
        assertThat(before.completedCount()).isZero();
        assertThat(before.total()).isEqualTo(2);
        assertThat(guidance.status(account).nextAction().type()).isEqualTo("START_TODAY");

        var started = sessions.startOrResume(account);
        var afterFirst = sessions.complete(account, started.currentSlug(), StudyResult.CORRECT);
        var partial = missions.today(account);
        assertThat(partial.sessionSnapshot()).isTrue();
        assertThat(partial.total()).isEqualTo(started.totalCount());
        assertThat(partial.completedCount()).isEqualTo(1);
        assertThat(partial.review().target() + partial.newWords().target() + partial.newGrammar().target())
                .isEqualTo(started.totalCount());
        assertThat(partial.newWords().completed() + partial.newGrammar().completed()).isEqualTo(1);
        assertThat(guidance.status(account).nextAction().type()).isEqualTo("CONTINUE_TODAY");

        sessions.complete(account, started.currentSlug(), StudyResult.INCORRECT);
        assertThat(missions.today(account).completedCount()).isEqualTo(1);
        assertThat(missions.today(account)).isEqualTo(partial);

        while (!afterFirst.completed()) {
            afterFirst = sessions.complete(account, afterFirst.currentSlug(), StudyResult.CORRECT);
        }
        var complete = missions.today(account);
        assertThat(complete.completed()).isTrue();
        assertThat(complete.completedCount()).isEqualTo(complete.total());
        assertThat(complete.newWords().completed()).isEqualTo(1);
        assertThat(complete.newGrammar().completed()).isEqualTo(1);
        assertThat(guidance.status(account).nextAction().type()).isEqualTo("TODAY_COMPLETE");
        assertThat(guidance.status(account).reminder().type()).isEqualTo("COMPLETE");
    }

    @Test
    void retrainingAndQuizNeverChangeRegularMissionProgress() {
        learning.answer(account, "taberu", StudyResult.CORRECT, "regular", false);
        var regular = missions.today(account);
        int experience = learning.overview(account).character().experience();

        learning.answer(account, "taberu", StudyResult.CORRECT, "retrain", true);
        learning.recordQuizAnswer(account, 98_765L, true);

        assertThat(missions.today(account)).isEqualTo(regular);
        assertThat(learning.todayProgress(account).completed()).isEqualTo(1);
        assertThat(learning.overview(account).character().experience()).isEqualTo(experience + 10);
    }

    @Test
    void dueReviewIsTheFirstActionAndUsesTheDailyPlanLimit() {
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        ContentItem item = content.findBySlugAndPublishedTrue("server").orElseThrow();
        LearningProgress due = progress.save(new LearningProgress(profile, item, StudyResult.CORRECT));
        ReflectionTestUtils.setField(due, "nextReviewAt", Instant.now().minusSeconds(60));
        progress.saveAndFlush(due);
        reminders.update(account, true, LocalTime.MIDNIGHT);

        var status = guidance.status(account);
        assertThat(status.mission().review().target()).isEqualTo(1);
        assertThat(status.nextAction().type()).isEqualTo("START_REVIEW");
        assertThat(status.reminder().reviewScheduled()).isTrue();
        assertThat(status.reminder().type()).isEqualTo("REVIEW_AVAILABLE");

        var started = sessions.startOrResume(account);
        assertThat(started.currentActivityType().name()).isEqualTo("REVIEW");
        sessions.complete(account, started.currentSlug(), StudyResult.CORRECT);
        assertThat(missions.today(account).review().completed()).isEqualTo(1);
    }

    @Test
    void completedIncorrectMissionSuggestsExistingWeaknessReview() {
        learning.updateNewContentLimits(account, 1, 0);
        var started = sessions.startOrResume(account);
        sessions.complete(account, started.currentSlug(), StudyResult.INCORRECT);

        var status = guidance.status(account);
        assertThat(status.mission().completed()).isTrue();
        assertThat(status.nextAction().type()).isEqualTo("REVIEW_WEAKNESS");
    }

    @Test
    void reminderPreferenceSupportsLegacyDefaultsDisableAndPreferredTime() {
        var defaultValue = reminders.preference(account);
        assertThat(defaultValue.enabled()).isTrue();
        assertThat(defaultValue.preferredTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(defaultValue.timeZone()).isEqualTo("Asia/Seoul");

        reminders.update(account, false, LocalTime.of(8, 15));
        var status = guidance.status(account);
        assertThat(status.reminder().visible()).isFalse();
        assertThat(status.reminder().type()).isEqualTo("DISABLED");
        assertThat(status.reminder().preference().preferredTime()).isEqualTo(LocalTime.of(8, 15));
    }
}
