package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.TodayStudySessionRepository;
import com.japanese.learning.repository.TodayStudySessionItemRepository;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.entity.LearningProgress;
import com.japanese.learning.entity.LearningState;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.ZoneId;
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
class TodayStudySessionServiceTest {
    @Autowired private SampleContentDataLoader sample;
    @Autowired private TodayStudySessionService sessions;
    @Autowired private LearningService learning;
    @Autowired private LearningHistoryService history;
    @Autowired private UserAccountRepository accounts;
    @Autowired private LearnerProfileRepository profiles;
    @Autowired private StudyRecordRepository records;
    @Autowired private LearningProgressRepository progress;
    @Autowired private ContentItemRepository contents;
    @Autowired private TodayStudySessionRepository sessionRepository;
    @Autowired private TodayStudySessionItemRepository sessionItems;
    private UserAccount account;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        account = accounts.saveAndFlush(new UserAccount("today-session-" + UUID.randomUUID(), null, "hash", "오늘 세션", UserRole.USER));
        learning.overview(account);
    }

    @Test
    void resumesTheFixedPlanAndCompletesEachCardOnlyOnce() {
        var started = sessions.startOrResume(account);
        assertThat(started.totalCount()).isGreaterThan(1);
        String firstSlug = started.currentSlug();
        String learnerKey = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();

        var afterFirst = sessions.complete(account, firstSlug, StudyResult.CORRECT);
        assertThat(afterFirst.completedCount()).isEqualTo(1);
        assertThat(afterFirst.currentSlug()).isNotEqualTo(firstSlug);
        assertThat(records.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(learnerKey)).hasSize(1);

        learning.updateNewContentLimits(account, 0, 0);
        var resumed = sessions.startOrResume(account);
        assertThat(resumed.sessionKey()).isEqualTo(started.sessionKey());
        assertThat(resumed.totalCount()).isEqualTo(started.totalCount());
        assertThat(resumed.completedCount()).isEqualTo(1);

        sessions.complete(account, firstSlug, StudyResult.INCORRECT);
        assertThat(records.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(learnerKey)).hasSize(1);
        assertThat(learning.overview(account).character().experience()).isEqualTo(10);

        while (!resumed.completed()) {
            resumed = sessions.complete(account, resumed.currentSlug(), StudyResult.CORRECT);
        }
        assertThat(resumed.completedCount()).isEqualTo(resumed.totalCount());
        var report = history.day(account, LocalDate.now(ZoneId.of("Asia/Seoul")));
        assertThat(report.regularGoalCompleted()).isEqualTo(resumed.totalCount());
        assertThat(report.earnedExperience()).isEqualTo(resumed.totalCount() * 10);
    }

    @Test
    void eligibilityChangesDoNotRebuildAnExistingTodaySnapshot() {
        var item = contents.findBySlugAndPublishedTrue("taberu").orElseThrow();
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        LearningProgress due = new LearningProgress(profile, item, StudyResult.CORRECT);
        ReflectionTestUtils.setField(due, "nextReviewAt", Instant.now().minusSeconds(60));
        progress.saveAndFlush(due);

        var started = sessions.startOrResume(account);
        int originalTotal = started.totalCount();
        String originalCurrent = started.currentSlug();
        ReflectionTestUtils.setField(due, "learningState", LearningState.SUSPENDED);
        progress.saveAndFlush(due);

        var resumed = sessions.startOrResume(account);
        assertThat(resumed.sessionKey()).isEqualTo(started.sessionKey());
        assertThat(resumed.totalCount()).isEqualTo(originalTotal);
        assertThat(resumed.currentSlug()).isEqualTo(originalCurrent);
    }

    @Test
    void withdrawnFutureItemIsSkippedWithoutReplacingOrReorderingRemainingCards() {
        var started = sessions.startOrResume(account);
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        var entity = sessionRepository.findByLearnerProfileIdAndSessionDate(
                profile.getId(), LocalDate.now(ZoneId.of("Asia/Seoul"))).orElseThrow();
        var originalItems = sessionItems.findItems(entity.getId());
        assertThat(originalItems).hasSizeGreaterThan(2);
        var withdrawn = originalItems.get(1).getContentItem();
        withdrawn.unpublishApproved();
        contents.saveAndFlush(withdrawn);

        var resumed = sessions.startOrResume(account);
        assertThat(resumed.sessionKey()).isEqualTo(started.sessionKey());
        assertThat(resumed.totalCount()).isEqualTo(started.totalCount() - 1);
        assertThat(resumed.currentSlug()).isEqualTo(originalItems.get(0).getContentItem().getSlug());
        assertThat(sessionItems.findItems(entity.getId())).hasSameSizeAs(originalItems);

        var afterFirst = sessions.complete(account, resumed.currentSlug(), StudyResult.CORRECT);
        assertThat(afterFirst.currentSlug()).isEqualTo(originalItems.get(2).getContentItem().getSlug());
        assertThat(afterFirst.currentSlug()).isNotEqualTo(withdrawn.getSlug());
    }

    @Test
    void withdrawnCurrentItemDoesNotRecordAnAnswerAndReloadContinuesAtNextSnapshotItem() {
        var started = sessions.startOrResume(account);
        String withdrawnSlug = started.currentSlug();
        var withdrawn = contents.findBySlugAndPublishedTrue(withdrawnSlug).orElseThrow();
        long recordsBefore = records.count();
        long progressBefore = progress.count();
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        var entity = sessionRepository.findByLearnerProfileIdAndSessionDate(
                profile.getId(), LocalDate.now(ZoneId.of("Asia/Seoul"))).orElseThrow();
        int snapshotSize = sessionItems.findItems(entity.getId()).size();
        withdrawn.unpublishApproved();
        contents.saveAndFlush(withdrawn);

        var advanced = sessions.complete(account, withdrawnSlug, StudyResult.CORRECT);
        assertThat(advanced.currentSlug()).isNotEqualTo(withdrawnSlug);
        assertThat(records.count()).isEqualTo(recordsBefore);
        assertThat(progress.count()).isEqualTo(progressBefore);
        assertThat(sessionItems.findItems(entity.getId())).hasSize(snapshotSize);

        var reloaded = sessions.startOrResume(account);
        assertThat(reloaded.sessionKey()).isEqualTo(started.sessionKey());
        assertThat(reloaded.currentSlug()).isEqualTo(advanced.currentSlug());
        assertThat(reloaded.totalCount()).isEqualTo(started.totalCount() - 1);
    }
}
