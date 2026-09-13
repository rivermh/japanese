package com.japanese.learning.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.service.LearningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sample")
class LearningStatusApiControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private SampleContentDataLoader sample;
    @Autowired private UserAccountRepository accounts;
    @Autowired private LearningService learning;
    private UserAccount first;
    private UserAccount second;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        first = account("first");
        second = account("second");
        learning.overview(first);
        learning.overview(second);
        learning.updateNewContentLimits(first, 1, 1);
        learning.updateNewContentLimits(second, 1, 1);
    }

    @Test
    void statusUsesOnlyTheAuthenticatedLearnerAndDoesNotExposeIdentifiers() throws Exception {
        learning.answer(first, "taberu", StudyResult.CORRECT, "first-only", false);

        mvc.perform(get("/api/v1/home/learning-status").with(user(first.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mission.completedCount").value(1))
                .andExpect(content().string(not(containsString(first.getLoginId()))));
        mvc.perform(get("/api/v1/home/learning-status").with(user(second.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mission.completedCount").value(0));
    }

    @Test
    void authenticatedLearnerCanUpdateOnlyTheirOwnReminderPreference() throws Exception {
        mvc.perform(post("/api/v1/reminders/preferences")
                        .with(user(first.getLoginId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"preferredTime\":\"08:15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.preferredTime").value("08:15:00"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"));

        mvc.perform(get("/api/v1/home/learning-status").with(user(second.getLoginId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminder.preference.enabled").value(true));
    }

    @Test
    void anonymousAccessAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/v1/home/learning-status"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/v1/reminders/preferences")
                        .with(user(first.getLoginId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"preferredTime\":\"19:00\"}"))
                .andExpect(status().isForbidden());
    }

    private UserAccount account(String prefix) {
        return accounts.saveAndFlush(new UserAccount(
                prefix + "-learning-api-" + UUID.randomUUID(), null, "hash", prefix, UserRole.USER));
    }
}
