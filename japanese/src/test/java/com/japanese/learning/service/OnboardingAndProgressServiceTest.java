package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.dto.RegistrationRequest;
import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.account.service.AccountService;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.learning.dto.OnboardingRequest;
import com.japanese.learning.entity.LearningState;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.LearnerProfileRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class OnboardingAndProgressServiceTest {
    @Autowired SampleContentDataLoader samples;
    @Autowired AccountService accounts;
    @Autowired OnboardingService onboarding;
    @Autowired LearningService learning;
    @Autowired LearningProgressMapService progressMap;
    @Autowired UserAccountRepository users;
    @Autowired ContentItemRepository contents;
    @Autowired LevelRepository levels;
    @Autowired LearningProgressRepository progress;
    @Autowired LearnerProfileRepository profiles;

    @BeforeEach void setup() throws Exception { samples.run(); }

    @Test
    void onboardingWritesExistingPreferencesOnlyAndKeepsLearningDataEmpty() {
        var request = new RegistrationRequest();
        request.setLoginId("onboarding-" + UUID.randomUUID()); request.setDisplayName("온보딩"); request.setPassword("password-123");
        UserAccount account = accounts.register(request);

        assertThat(onboarding.status(account).required()).isTrue();
        onboarding.complete(account, new OnboardingRequest("UNKNOWN", "N5", List.of("daily-life"), "light", null, null, null));

        assertThat(onboarding.status(account).required()).isFalse();
        assertThat(learning.learningScope(account).levelCodes()).containsExactly("JLPT:N5");
        assertThat(learning.learningScope(account).categorySlugs()).containsExactly("daily-life");
        assertThat(learning.studyPreferences(account).dailyNewWordLimit()).isEqualTo(3);
        assertThat(learning.studyPreferences(account).dailyNewGrammarLimit()).isEqualTo(2);
        assertThat(learning.todayProgress(account).goal()).isEqualTo(5);
        assertThat(learning.overview(account).character().experience()).isZero();
        assertThat(progress.countByLearnerProfileLearnerKey(
                profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey())).isZero();
    }

    @Test
    void existingProfileWithoutTheNewFlagRemainsCompatible() {
        UserAccount existing = users.save(new UserAccount("legacy-" + UUID.randomUUID(), null, "hash", "기존", UserRole.USER));
        learning.overview(existing);
        assertThat(onboarding.status(existing).required()).isFalse();
    }

    @Test
    void progressMapUsesPublishedContentAndCurrentProgressStates() {
        UserAccount account = users.save(new UserAccount("map-" + UUID.randomUUID(), null, "hash", "진도", UserRole.USER));
        Level n5 = levels.findBySystemAndCode("JLPT", "N5").orElseThrow();
        for (LearningState state : LearningState.values()) {
            ContentItem item = new ContentItem("map-" + state.name().toLowerCase() + "-" + UUID.randomUUID(), ContentType.WORD, "test", true);
            item.addLevel(n5); item.attachWord(new Word(state.name(), state.name(), "명사", null));
            contents.saveAndFlush(item);
            learning.answer(account, item.getSlug(), StudyResult.CORRECT, "map-" + state, false);
            var itemProgress = progress.findByLearnerProfileLearnerKeyAndContentItemId(account.getLoginId(), item.getId()).orElseThrow();
            ReflectionTestUtils.setField(itemProgress, "learningState", state);
            progress.saveAndFlush(itemProgress);
        }
        ContentItem hidden = new ContentItem("map-hidden-" + UUID.randomUUID(), ContentType.WORD, "test", false);
        hidden.addLevel(n5); hidden.attachWord(new Word("hidden", "hidden", "명사", null)); contents.saveAndFlush(hidden);

        var word = progressMap.level(account, "N5").contentTypes().stream().filter(type -> type.type() == ContentType.WORD).findFirst().orElseThrow();
        assertThat(word.learning()).isEqualTo(1);
        assertThat(word.review()).isEqualTo(1);
        assertThat(word.mastered()).isEqualTo(1);
        assertThat(word.suspended()).isEqualTo(1);
        assertThat(word.total()).isEqualTo(word.unstarted() + word.learning() + word.review() + word.mastered() + word.suspended());
    }
}
