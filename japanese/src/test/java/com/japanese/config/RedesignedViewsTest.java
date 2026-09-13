package com.japanese.config;

import com.japanese.account.dto.RegistrationRequest;
import com.japanese.account.entity.UserAccount;
import com.japanese.account.service.AccountService;
import com.japanese.learning.service.LearningService;
import com.japanese.learning.service.StreakService;
import com.japanese.learning.service.OnboardingService;
import com.japanese.learning.dto.OnboardingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sample")
@Transactional
class RedesignedViewsTest {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired LearningService learning;
    @Autowired StreakService streak;
    @Autowired OnboardingService onboarding;

    private UserAccount register() {
        var request = new RegistrationRequest();
        request.setLoginId("ui-regression");
        request.setDisplayName("화면 검증");
        request.setPassword("test-password-only");
        UserAccount account = accounts.register(request);
        onboarding.complete(account, new OnboardingRequest("UNKNOWN", "N5", java.util.List.of(), "normal", null, null, null));
        return account;
    }

    @Test
    void rendersPublicPagesAndSharedNavigation() throws Exception {
        for (String path : new String[]{"/", "/?keyword=食べる", "/dictionary?keyword=食べる", "/contents/taberu", "/contents/temo-ii", "/categories", "/login", "/signup", "/quiz"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"main-content\"")))
                    .andExpect(content().string(containsString("class=\"app-header")));
        }
        mvc.perform(get("/images/characters/haru/animation.json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"ambientDelayMs\"")))
                .andExpect(content().string(containsString("\"goal-complete\"")))
                .andExpect(content().string(containsString("celebrate")))
                .andExpect(jsonPath("$.stages['stage-1'].states.study.assetStatus").value("motion-only"))
                .andExpect(jsonPath("$.stages['stage-1'].states.blink.assetStatus").value("required"));
    }

    @Test
    void servesInstalledStageAssetsAndOmitsUnavailableBlinkFrames() throws Exception {
        mvc.perform(get("/images/characters/haru/animation.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.stages['stage-1'].states.blink.frames").doesNotExist())
                .andExpect(jsonPath("$.stages['stage-4'].idle.asset").value("haru-stage-3.png"));
        for (int n = 1; n <= 3; n++) {
            mvc.perform(get("/images/characters/haru/haru-stage-" + n + ".png"))
                    .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        }
    }

    @Test
    void rendersPersonalScreensAndFullLessonBodyWithoutAwardingExperience() throws Exception {
        var account = register();
        for (String path : new String[]{"/", "/today", "/study", "/study/relearn/bookmarks", "/study/relearn/weaknesses", "/weaknesses", "/report/weekly", "/my-learning", "/bookmarks", "/study-queue", "/collections", "/history", "/statistics", "/progress", "/settings"}) {
            mvc.perform(get(path).with(user(account.getLoginId())))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("id=\"main-content\"")));
        }
        mvc.perform(get("/api/v1/study/preferences").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dailyNewWordLimit")));
        mvc.perform(get("/api/v1/study/today/session").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sessionKey")));
        mvc.perform(post("/api/v1/study/preferences").with(user(account.getLoginId())).with(csrf())
                        .param("dailyNewWordLimit", "2").param("dailyNewGrammarLimit", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"dailyNewWordLimit\":2")));
        mvc.perform(get("/api/v1/study/relearn/BOOKMARKS").with(user(account.getLoginId())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/weaknesses").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("availableForFocusedReview")));
        mvc.perform(get("/api/v1/reports/weekly").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("currentStudy")));
        mvc.perform(get("/api/v1/progress/jlpt").with(user(account.getLoginId())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("levels")));
        mvc.perform(get("/today").with(user(account.getLoginId())))
                .andExpect(content().string(containsString("毎朝、朝ご飯を食べます。")))
                .andExpect(content().string(containsString("TODAY'S SESSION")));
        assertThat(learning.overview(account).character().experience()).isZero();
    }

    @Test
    void redirectsNewLearnerToOnboardingUntilPlanIsCompleted() throws Exception {
        var request = new RegistrationRequest();
        request.setLoginId("new-onboarding"); request.setDisplayName("새 학습자"); request.setPassword("test-password-only");
        var account = accounts.register(request);
        mvc.perform(get("/").with(user(account.getLoginId()))).andExpect(redirectedUrl("/onboarding"));
        mvc.perform(get("/onboarding").with(user(account.getLoginId())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("내 학습 플랜 만들기")));
    }

    @Test
    void completesDailyLearningAndRendersUpdatedViewsWithoutQuiz() throws Exception {
        var account = register();
        learning.updateDailyGoal(account, 1);
        var session = new MockHttpSession();
        String slug = currentTodaySlug(account, session);
        mvc.perform(post("/today/" + slug + "/complete").session(session)
                .with(user(account.getLoginId())).with(csrf()).param("result", "CORRECT"))
                .andExpect(redirectedUrl("/today"));
        mvc.perform(get("/today").session(session).with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SESSION COMPLETE")))
                .andExpect(content().string(containsString("10 EXP")));
        mvc.perform(get("/").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-state=\"goal-complete\"")))
                .andExpect(content().string(containsString("data-animation-manifest=\"/images/characters/haru/animation.json\"")))
                .andExpect(content().string(containsString("Daily Mission")))
                .andExpect(content().string(containsString("오늘 학습 완료")));
        mvc.perform(get("/statistics").with(user(account.getLoginId()))).andExpect(status().isOk());
        assertThat(learning.overview(account).character().experience()).isEqualTo(10);
        assertThat(streak.status(account).currentStreak()).isEqualTo(1);
        assertThat(learning.recentQuizHistory(account, 10)).isEmpty();
    }

    @Test
    void resumesFocusedWeaknessSessionWithoutAddingExperienceOrRegularGoal() throws Exception {
        var account = register();
        learning.answer(account, "taberu", com.japanese.learning.entity.StudyResult.INCORRECT, "weakness-web-origin", false);
        int experienceBefore = learning.overview(account).character().experience();
        long goalBefore = learning.todayProgress(account).completed();

        mvc.perform(post("/weaknesses/session/start").with(user(account.getLoginId())).with(csrf()))
                .andExpect(redirectedUrl("/weaknesses/session"));
        MvcResult page = mvc.perform(get("/weaknesses/session").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FOCUSED RETRAIN"))).andReturn();
        Matcher matcher = Pattern.compile("/weaknesses/session/([^/]+)/complete").matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        String slug = matcher.group(1);
        mvc.perform(post("/weaknesses/session/" + slug + "/complete").with(user(account.getLoginId())).with(csrf()).param("result", "CORRECT"))
                .andExpect(redirectedUrl("/weaknesses/session"));
        mvc.perform(post("/weaknesses/session/" + slug + "/complete").with(user(account.getLoginId())).with(csrf()).param("result", "INCORRECT"))
                .andExpect(redirectedUrl("/weaknesses/session"));
        mvc.perform(get("/weaknesses/session").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FOCUSED RETRAIN COMPLETE")));
        assertThat(learning.overview(account).character().experience()).isEqualTo(experienceBefore);
        assertThat(learning.todayProgress(account).completed()).isEqualTo(goalBefore);
    }

    private String currentTodaySlug(UserAccount account, MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/today").session(session).with(user(account.getLoginId())))
                .andExpect(status().isOk()).andReturn();
        Matcher matcher = Pattern.compile("/today/([^/]+)/complete").matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void showsHappyReactionOnceAfterLearningBeforeGoalCompletion() throws Exception {
        var account = register();
        var session = new MockHttpSession();
        mvc.perform(post("/today/taberu/complete").session(session)
                        .with(user(account.getLoginId())).with(csrf()).param("result", "CORRECT"))
                .andExpect(redirectedUrl("/today"));
        mvc.perform(get("/").session(session).with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-state=\"happy\"")));
        mvc.perform(get("/").session(session).with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-state=\"idle\"")));
    }

    @Test
    void dictionarySearchAndDetailStayReadOnlyAndExposeLearningState() throws Exception {
        var account = register();
        int experienceBefore = learning.overview(account).character().experience();

        mvc.perform(get("/").param("keyword", "タベル"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("食べる")));
        mvc.perform(get("/contents/taberu").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("새 콘텐츠")));
        mvc.perform(get("/api/v1/contents").param("keyword", "먹다"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("taberu")));
        mvc.perform(get("/api/v1/contents/taberu"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pitchAccentDisplay")));
        mvc.perform(get("/api/v1/study/contents/taberu/status").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"state\":\"NEW\"")));

        assertThat(learning.overview(account).character().experience()).isEqualTo(experienceBefore);
        assertThat(learning.recentHistory(account, 10)).isEmpty();

        learning.answer(account, "taberu", com.japanese.learning.entity.StudyResult.CORRECT, "dictionary-status", false);
        mvc.perform(get("/api/v1/study/contents/taberu/status").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"state\":\"REVIEW\"")));
    }
}
