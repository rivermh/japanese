package com.japanese.learning.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.japanese.account.entity.*;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.dto.QuizQuestionDetails;
import com.japanese.content.entity.*;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.repository.*;
import com.japanese.learning.service.LearningService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sample")
@Transactional
class QuizSessionApiControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private SampleContentDataLoader sample;
    @Autowired private UserAccountRepository accounts;
    @Autowired private ContentItemRepository contents;
    @Autowired private LearningService learning;
    @Autowired private QuizAttemptRepository attempts;
    @Autowired private LearnerProfileRepository profiles;
    private UserAccount account;
    private UserAccount other;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        if (contents.findBySlug("quiz-api-grammar").isEmpty()) {
            ContentItem grammar = new ContentItem("quiz-api-grammar", ContentType.GRAMMAR, "test", true);
            grammar.attachGrammar(new Grammar("〜ながら", "두 동작을 동시에 할 때 사용합니다.", "동사 ます형 + ながら"));
            contents.saveAndFlush(grammar);
        }
        account = account("owner");
        other = account("other");
        learning.overview(account);
        learning.overview(other);
    }

    @Test
    void createsReadsAndAnswersWithoutLeakingTheAnswerKey() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/quizzes").with(user(account.getLoginId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(5))
                .andExpect(jsonPath("$.currentQuestion.itemId").isNumber())
                .andExpect(jsonPath("$.currentQuestion.correctAnswer").doesNotExist())
                .andExpect(content().string(not(containsString("correctAnswer"))))
                .andReturn();
        JsonNode session = json.readTree(created.getResponse().getContentAsString());
        String sessionId = session.get("sessionId").asText();
        long itemId = session.at("/currentQuestion/itemId").asLong();
        JsonNode choices = session.at("/currentQuestion/choices");
        String answer = choices.isArray() && !choices.isEmpty() ? choices.get(0).asText() : "たべる";
        String body = json.writeValueAsString(Map.of("itemId", itemId, "answer", answer));

        mvc.perform(get("/api/v1/quizzes/{id}", sessionId).with(user(account.getLoginId())))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("correctAnswer"))));
        mvc.perform(post("/api/v1/quizzes/{id}/answers", sessionId).with(user(account.getLoginId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.feedback.correctAnswer").isString());
        mvc.perform(post("/api/v1/quizzes/{id}/answers", sessionId).with(user(account.getLoginId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        String learnerKey = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        assertThat(attempts.findByLearnerProfileLearnerKeyOrderByAnsweredAtDesc(
                learnerKey, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(1);
        mvc.perform(get("/api/v1/quizzes/{id}", sessionId).with(user(other.getLoginId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousAndMissingCsrfRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/quizzes").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/quizzes/not-owned"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/v1/quizzes").with(user(account.getLoginId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyQuestionJsonAlsoHidesServerSideAnswers() throws Exception {
        String serialized = json.writeValueAsString(new QuizQuestionDetails(1L, 1L, "N5", "word", "label",
                "instruction", "prompt", "prompt ko", List.of("보기"), "비밀 정답", "비밀 뜻", "설명"));
        assertThat(serialized).doesNotContain("answerJapanese", "answerKorean", "비밀 정답", "비밀 뜻");
    }

    private UserAccount account(String prefix) {
        return accounts.saveAndFlush(new UserAccount(prefix + "-quiz-api-" + UUID.randomUUID(), null, "hash", prefix, UserRole.USER));
    }

}
