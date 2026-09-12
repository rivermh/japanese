package com.japanese.content.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.GrammarConfirmationType;
import com.japanese.content.service.GrammarCurationService;
import com.japanese.content.repository.GrammarConfirmationQuestionRepository;
import com.japanese.learning.service.LearningService;
import java.util.List;
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
class GrammarLearningApiControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private SampleContentDataLoader sample;
    @Autowired private GrammarCurationService curation;
    @Autowired private GrammarConfirmationQuestionRepository questions;
    @Autowired private UserAccountRepository accounts;
    @Autowired private LearningService learningService;
    private Long questionId;
    private Long correctChoiceId;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        var question = curation.saveQuestion("temo-ii", GrammarConfirmationType.CONTEXT_GAP,
                "Pick one", "写真を撮っても＿＿。", "Reviewed note", "editorial:test",
                List.of(new GrammarCurationService.ChoiceDraft("いいです", true), new GrammarCurationService.ChoiceDraft("だめです", false)));
        question.approveForPublication();
        questions.saveAndFlush(question);
        questionId = question.getId();
        correctChoiceId = question.getChoices().get(0).getId();
        var account = accounts.saveAndFlush(new UserAccount("grammar-api", "grammar-api@example.test", "hash", "Grammar API", UserRole.USER));
        learningService.overview(account);
    }

    @Test
    void publicReadAndAuthenticatedAnswerUseTheSameReviewedQuestion() throws Exception {
        mvc.perform(get("/api/v1/grammars/temo-ii/learning"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("confirmationAvailable")));
        mvc.perform(get("/api/v1/grammars/temo-ii/confirmation"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Pick one")));
        mvc.perform(post("/api/v1/grammars/confirmations/{id}/answer", questionId).with(user("grammar-api")).with(csrf())
                        .param("choiceId", String.valueOf(correctChoiceId)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"correct\":true")));
    }

    @Test
    void anonymousUserCannotSubmitOrReadPersonalGrammarWeaknesses() throws Exception {
        mvc.perform(post("/api/v1/grammars/confirmations/{id}/answer", questionId).with(csrf())
                        .param("choiceId", String.valueOf(correctChoiceId))).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/grammars/weaknesses")).andExpect(status().is3xxRedirection());
    }

    @Test
    void grammarDetailAndConfirmationPagesRenderWithoutChangingLearningState() throws Exception {
        mvc.perform(get("/contents/temo-ii"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("id=\"main-content\"")));
        mvc.perform(get("/grammars/temo-ii/confirm").with(user("grammar-api")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Pick one")));
        mvc.perform(post("/grammars/confirmations/{id}/answer", questionId).with(user("grammar-api")).with(csrf())
                        .param("choiceId", String.valueOf(correctChoiceId)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("EXP와 오늘 목표는 증가하지 않습니다")));
    }
}
