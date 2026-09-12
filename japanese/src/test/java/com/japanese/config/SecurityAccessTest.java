package com.japanese.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("sample")
class SecurityAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void redirectsAnonymousUsersFromPersonalStatistics() throws Exception {
        mockMvc.perform(get("/statistics")).andExpect(status().is3xxRedirection());
    }

    @Test
    void blocksGeneralUsersFromContentReview() throws Exception {
        mockMvc.perform(get("/review").with(user("regular-user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void redirectsAnonymousQuizAnswersToLogin() throws Exception {
        mockMvc.perform(post("/quiz/1/answer").with(csrf()).param("answer", "answer"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void redirectsAnonymousUsersFromOnboardingAndProgress() throws Exception {
        mockMvc.perform(get("/onboarding")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/progress")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/v1/progress/jlpt")).andExpect(status().is3xxRedirection());
    }
}
