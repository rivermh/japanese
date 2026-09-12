package com.japanese.learning.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.japanese.JapaneseApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = JapaneseApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("sample")
class PersonalLearningAccessTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void personalQueueCollectionsAndHistoryRequireAuthentication() throws Exception {
        mvc.perform(get("/study-queue")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/collections")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/history")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/api/v1/study/queue")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/api/v1/collections")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/api/v1/history/month").param("month", "2026-09"))
                .andExpect(redirectedUrl("/login"));
    }
}
