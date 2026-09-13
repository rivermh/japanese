package com.japanese.learning.service;

import com.japanese.account.entity.*;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.learning.character.*;
import com.japanese.learning.entity.*;
import com.japanese.learning.repository.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sample")
@Transactional
class HaruGrowthV2Test {
    @Autowired LearningService learning;
    @Autowired TodayStudySessionService today;
    @Autowired DailyMissionService missions;
    @Autowired UserAccountRepository accounts;
    @Autowired LearnerProfileRepository profiles;
    @Autowired HaruAssetResolver assets;
    @Autowired MockMvc mvc;

    private UserAccount account(int experience) {
        var account = accounts.saveAndFlush(new UserAccount("haru-" + UUID.randomUUID(), null, "hash", "Haru", UserRole.USER));
        learning.overview(account);
        profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow().addExperience(experience);
        return account;
    }

    @Test void thresholdsAndLegacyNullProfilesKeepDomainAndAssetStagesSeparate() {
        int[] exp = {0, 299, 300, 301, 999, 1000, 2499, 2500, 4000};
        int[] stages = {1, 1, 2, 2, 2, 3, 3, 4, 4};
        for (int i = 0; i < exp.length; i++) {
            var status = learning.overview(account(exp[i])).character();
            assertThat(status.stageNumber()).isEqualTo(stages[i]);
            assertThat(status.growthNoticePending()).isFalse();
            assertThat(status.growthPresentationPending()).isFalse();
            assertThat(status.assetStage()).isEqualTo(Math.min(stages[i], 3));
            assertThat(status.fallbackUsed()).isEqualTo(stages[i] == 4);
            assertThat(status.finalStage()).isEqualTo(stages[i] == 4);
            if (status.finalStage()) assertThat(status.experienceToNextStage()).isZero();
        }
    }

    @Test void todayThresholdIsPersistedOnceAndDuplicateCompletionCannotReplayIt() {
        var account = account(290);
        learning.updateNewContentLimits(account, 1, 0);
        var session = today.startOrResume(account);
        var completed = today.complete(account, session.currentSlug(), StudyResult.CORRECT);
        assertThat(completed.completed()).isTrue();
        var status = learning.overview(account).character();
        assertThat(status.experience()).isEqualTo(300);
        assertThat(status.growthPresentationPending()).isTrue();
        assertThat(status.pendingGrowthStageKey()).isEqualTo("apprentice");
        assertThat(learning.claimGrowthPresentation(account, "apprentice")).isTrue();
        assertThat(learning.claimGrowthPresentation(account, "apprentice")).isFalse();
        today.complete(account, session.currentSlug(), StudyResult.CORRECT);
        assertThat(learning.overview(account).character().experience()).isEqualTo(300);
        assertThat(learning.overview(account).character().growthPresentationPending()).isFalse();
        assertThat(learning.overview(account).character().growthNoticePending()).isTrue();
        learning.acknowledgeGrowth(account, "young");
        assertThat(learning.overview(account).character().growthNoticePending()).isTrue();
        learning.acknowledgeGrowth(account, "apprentice");
        assertThat(learning.overview(account).character().growthNoticePending()).isFalse();
    }

    @Test void missionCompletionDoesNotImplyGrowthAndQuizRetainsItsOwnExpPolicy() {
        var account = account(0);
        learning.updateNewContentLimits(account, 1, 0);
        var session = today.startOrResume(account);
        today.complete(account, session.currentSlug(), StudyResult.CORRECT);
        assertThat(missions.today(account).completed()).isTrue();
        assertThat(learning.overview(account).character().growthNoticePending()).isFalse();
        var quizAccount = account(990);
        var missionBefore = missions.today(quizAccount);
        learning.recordQuizSessionAnswer(quizAccount, 1L, true);
        assertThat(learning.overview(quizAccount).character().stageNumber()).isEqualTo(3);
        assertThat(learning.overview(quizAccount).character().growthPresentationPending()).isTrue();
        assertThat(missions.today(quizAccount)).isEqualTo(missionBefore);
    }

    @Test void staleClaimAndAcknowledgementCannotConsumeANewerGrowth() {
        var account = account(290);
        learning.recordQuizSessionAnswer(account, 1L, true);
        assertThat(learning.claimGrowthPresentation(account, "apprentice")).isTrue();
        profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow().addExperience(690);
        learning.recordQuizSessionAnswer(account, 2L, true);
        learning.acknowledgeGrowth(account, "apprentice");
        assertThat(learning.claimGrowthPresentation(account, "apprentice")).isFalse();
        assertThat(learning.claimGrowthPresentation(account, "confident")).isTrue();
    }

    @Test void apiUsesCurrentUserAndCsrfAndDoesNotConsumeOnRead() throws Exception {
        var account = account(290);
        var other = account(0);
        learning.recordQuizSessionAnswer(account, 1L, true);
        mvc.perform(get("/api/v1/characters/current")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/characters/current").with(user(account.getLoginId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stageKey").value("apprentice"))
                .andExpect(jsonPath("$.growthPresentationPending").value(true));
        mvc.perform(post("/api/v1/characters/growth/present").param("stageKey", "apprentice")
                .with(user(account.getLoginId()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/characters/growth/present").param("stageKey", "apprentice")
                .param("userId", account.getId().toString()).with(user(other.getLoginId())).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.claimed").value(false));
        for (boolean claimed : new boolean[]{true, false}) {
            mvc.perform(post("/api/v1/characters/growth/present").param("stageKey", "apprentice")
                    .with(user(account.getLoginId())).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.claimed").value(claimed));
        }
        mvc.perform(post("/api/v1/characters/growth/acknowledge").param("stageKey", "apprentice")
                .with(user(other.getLoginId())).with(csrf())).andExpect(status().isOk());
        assertThat(learning.overview(account).character().growthNoticePending()).isTrue();
    }

    @Test void assetResolverEnablesBlinkOnlyWhenAllFramesExist() {
        var loader = org.mockito.Mockito.mock(org.springframework.core.io.ResourceLoader.class);
        org.mockito.Mockito.when(loader.getResource(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
            String path = call.getArgument(0);
            var resource = org.mockito.Mockito.mock(org.springframework.core.io.Resource.class);
            org.mockito.Mockito.when(resource.exists()).thenReturn(path.endsWith("haru-stage-3.png") || path.contains("stage-3-blink-"));
            return resource;
        });
        var resolver = new HaruAssetResolver(loader);
        assertThat(resolver.resolve(CharacterGrowthStage.RELIABLE).stage()).isEqualTo(3);
        assertThat(resolver.resolve(CharacterGrowthStage.RELIABLE).supportsBlink()).isTrue();
        assertThat(resolver.resolve(CharacterGrowthStage.YOUNG).path()).isNull();
        assertThat(assets.resolve(CharacterGrowthStage.YOUNG).supportsBlink()).isFalse();
    }
}
