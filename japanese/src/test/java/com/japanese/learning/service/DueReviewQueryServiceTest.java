package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.entity.Category;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.CategoryRepository;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.learning.entity.LearnerProfile;
import com.japanese.learning.entity.LearningProgress;
import com.japanese.learning.entity.LearningState;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class DueReviewQueryServiceTest {
    @Autowired private DueReviewQueryService dueReviews;
    @Autowired private LearningService learning;
    @Autowired private LearningHistoryService history;
    @Autowired private WeeklyLearningReportService weekly;
    @Autowired private LearningGuidanceService guidance;
    @Autowired private LearningAnalyticsService analytics;
    @Autowired private LearningTime time;
    @Autowired private UserAccountRepository accounts;
    @Autowired private LearnerProfileRepository profiles;
    @Autowired private LearningProgressRepository progress;
    @Autowired private ContentItemRepository contents;
    @Autowired private LevelRepository levels;
    @Autowired private CategoryRepository categories;
    private UserAccount account;
    private LearnerProfile profile;
    private Level n1;
    private Level n5;
    private Category grammar;
    private Category daily;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        account = accounts.saveAndFlush(new UserAccount("due-" + suffix, null, "hash", "Due", UserRole.USER));
        learning.overview(account);
        profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        n1 = levels.findBySystemAndCode("JLPT", "N1").orElseGet(() -> levels.save(new Level("JLPT", "N1", "N1")));
        n5 = levels.findBySystemAndCode("JLPT", "N5").orElseGet(() -> levels.save(new Level("JLPT", "N5", "N5")));
        grammar = categories.findBySlug("due-grammar").orElseGet(() -> categories.save(new Category("due-grammar", "Grammar")));
        daily = categories.findBySlug("due-daily").orElseGet(() -> categories.save(new Category("due-daily", "Daily")));
    }

    @Test
    void sharesPublishedActiveScopedEligibilityAcrossUserFacingReadModels() {
        Instant asOf = Instant.ofEpochMilli(time.now().toEpochMilli());
        LearningProgress legacy = item("eligible-legacy", n1, grammar, true, null, asOf);
        LearningProgress mastered = item("eligible-mastered", n1, grammar, true, LearningState.MASTERED, asOf.minusSeconds(1));
        item("future", n1, grammar, true, LearningState.REVIEW, asOf.plusSeconds(3_600));
        item("suspended", n1, grammar, true, LearningState.SUSPENDED, asOf.minusSeconds(3_600));
        item("unpublished", n1, grammar, false, LearningState.REVIEW, asOf.minusSeconds(3_600));
        item("outside-level", n5, grammar, true, LearningState.REVIEW, asOf.minusSeconds(3_600));
        item("outside-category", n1, daily, true, LearningState.REVIEW, asOf.minusSeconds(3_600));
        progress.flush();
        learning.updateLearningScope(account, List.of("JLPT:N1"), List.of("due-grammar"));

        DueReviewCriteria criteria = dueReviews.currentScope(account, asOf);
        var found = dueReviews.findDue(criteria, null, PageRequest.of(0, 20));

        assertThat(dueReviews.countDue(criteria)).isEqualTo(2);
        assertThat(found).extracting(value -> value.getContentItem().getId())
                .containsExactly(mastered.getContentItem().getId(), legacy.getContentItem().getId());
        assertThat(dueReviews.findNextScheduledAt(criteria)).isEqualTo(mastered.getNextReviewAt());
        assertThat(learning.todayPlan(account).totalDueReviewCount()).isEqualTo(2);
        assertThat(learning.todayPlan(account).reviewCount()).isEqualTo(2);
        assertThat(learning.overview(account).dueReviewCount()).isEqualTo(2);
        assertThat(guidance.status(account).mission().review().target()).isEqualTo(2);
        assertThat(history.day(account, time.today()).remainingDueReviewCount()).isEqualTo(2);
        assertThat(weekly.report(account).recommendations())
                .anyMatch(value -> value.href().equals("/study?reviewOnly=true") && value.reason().contains("2"));

        var n1Progress = analytics.levelProgress(account).stream()
                .filter(value -> value.levelCode().equals("N1")).findFirst().orElseThrow();
        assertThat(n1Progress.dueReviewCount()).isEqualTo(3);
    }

    @Test
    void usesOneAsOfForInclusiveDueAndExclusiveFutureWindows() {
        Instant asOf = Instant.ofEpochMilli(time.now().toEpochMilli());
        item("boundary", n1, grammar, true, LearningState.REVIEW, asOf);
        item("future-window", n1, grammar, true, LearningState.REVIEW, asOf.plusSeconds(60));
        progress.flush();
        learning.updateLearningScope(account, List.of("JLPT:N1"), List.of("due-grammar"));
        DueReviewCriteria criteria = dueReviews.currentScope(account, asOf);

        assertThat(dueReviews.countDue(criteria)).isEqualTo(1);
        assertThat(dueReviews.findDue(criteria, null, PageRequest.of(0, 20))).hasSize(1);
        assertThat(dueReviews.countScheduledBetween(criteria, asOf.plusSeconds(1), asOf.plusSeconds(120)))
                .isEqualTo(1);
    }

    private LearningProgress item(String prefix, Level level, Category category, boolean published,
            LearningState state, Instant nextReviewAt) {
        ContentItem content = new ContentItem(prefix + "-" + UUID.randomUUID(), ContentType.WORD, "test", published);
        Word word = new Word(prefix, "reading", "noun", null);
        word.addMeaning(new Meaning("ko", prefix, 1));
        content.attachWord(word);
        content.addLevel(level);
        content.addCategory(category);
        contents.saveAndFlush(content);
        LearningProgress value = new LearningProgress(profile, content, StudyResult.CORRECT);
        ReflectionTestUtils.setField(value, "learningState", state);
        ReflectionTestUtils.setField(value, "nextReviewAt", nextReviewAt);
        return progress.save(value);
    }
}
