package com.japanese.content.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.japanese.account.dto.RegistrationRequest;
import com.japanese.account.entity.UserAccount;
import com.japanese.account.service.AccountService;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.ContentType;
import com.japanese.content.service.BookmarkService;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.dto.OnboardingRequest;
import com.japanese.learning.entity.StudyActivityType;
import com.japanese.learning.entity.TodayStudySession;
import com.japanese.learning.entity.TodayStudySessionItem;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.TodayStudySessionItemRepository;
import com.japanese.learning.repository.TodayStudySessionRepository;
import com.japanese.learning.service.OnboardingService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sample")
@Transactional
class WordRubyPresentationWebTest {
    @Autowired private SampleContentDataLoader samples;
    @Autowired private MockMvc mvc;
    @Autowired private AccountService accounts;
    @Autowired private OnboardingService onboarding;
    @Autowired private BookmarkService bookmarks;
    @Autowired private ContentItemRepository contents;
    @Autowired private LearnerProfileRepository profiles;
    @Autowired private TodayStudySessionRepository todaySessions;
    @Autowired private TodayStudySessionItemRepository todayItems;
    @Autowired private WordHeadwordPresentation presentation;

    @BeforeEach
    void seed() throws Exception {
        samples.run();
    }

    @Test
    void presentationRulesUseOnlyMeaningfulStoredReading() {
        assertThat(presentation.shouldRenderRuby("食べる", "たべる")).isTrue();
        assertThat(presentation.shouldRenderRuby("たべる", "たべる")).isFalse();
        assertThat(presentation.shouldRenderRuby("食べる", null)).isFalse();
        assertThat(presentation.shouldRenderRuby("食べる", " ")).isFalse();
        assertThat(presentation.shouldRenderRuby(ContentType.WORD, "食べる", "たべる")).isTrue();
        assertThat(presentation.shouldRenderRuby(ContentType.GRAMMAR, "〜てもいい", "てもいい")).isFalse();
    }

    @Test
    void grammarDetailDoesNotRenderVocabularyRuby() throws Exception {
        mvc.perform(get("/contents/temo-ii"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("〜てもいい")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("headword-ruby"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<ruby"))));
    }

    @Test
    void detailTodayAndFreeStudyExposeRubyWithoutSentenceFurigana() throws Exception {
        mvc.perform(get("/contents/taberu"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<ruby class=\"headword-ruby\">")))
                .andExpect(content().string(containsString("<rt>たべる</rt>")))
                .andExpect(content().string(containsString("class=\"example-reading\"")));

        UserAccount account = account();
        bookmarks.toggle(account, "taberu");
        mvc.perform(get("/study/relearn/bookmarks").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<ruby class=\"headword-ruby\">")))
                .andExpect(content().string(containsString("<rt>たべる</rt>")));

        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        var contentItem = contents.findBySlugAndPublishedTrue("taberu").orElseThrow();
        var session = todaySessions.save(new TodayStudySession(profile,
                LocalDate.now(ZoneId.of("Asia/Seoul")), "ruby-" + UUID.randomUUID(), 0, 1, 0));
        todayItems.save(new TodayStudySessionItem(session, contentItem, 0, StudyActivityType.NEW));
        mvc.perform(get("/today").with(user(account.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<ruby class=\"headword-ruby\">")))
                .andExpect(content().string(containsString("<rt>たべる</rt>")));
    }

    private UserAccount account() {
        RegistrationRequest request = new RegistrationRequest();
        String login = "ruby-" + UUID.randomUUID().toString().substring(0, 8);
        request.setLoginId(login);
        request.setEmail(login + "@example.test");
        request.setDisplayName(login);
        request.setPassword("ruby-password-123");
        UserAccount account = accounts.register(request);
        onboarding.complete(account, new OnboardingRequest("UNKNOWN", "N5", List.of(), "normal", null, null, null));
        return account;
    }
}
