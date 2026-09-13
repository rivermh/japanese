package com.japanese.learning.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.japanese.account.entity.*;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.*;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.entity.*;
import com.japanese.learning.repository.*;
import com.japanese.learning.service.*;
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
class QuizSessionWebTest {
    @Autowired private MockMvc mvc;
    @Autowired private SampleContentDataLoader sample;
    @Autowired private UserAccountRepository accounts;
    @Autowired private ContentItemRepository contents;
    @Autowired private LearningService learning;
    @Autowired private QuizSessionService quizzes;
    @Autowired private QuizSessionRepository sessionRepository;
    @Autowired private QuizSessionItemRepository itemRepository;
    @Autowired private LearnerProfileRepository profiles;
    private UserAccount account;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        if (contents.findBySlug("quiz-web-grammar").isEmpty()) {
            ContentItem grammar = new ContentItem("quiz-web-grammar", ContentType.GRAMMAR, "test", true);
            grammar.attachGrammar(new Grammar("〜ながら", "두 동작을 동시에 할 때 사용합니다.", "동사 ます형 + ながら"));
            contents.saveAndFlush(grammar);
        }
        account = accounts.saveAndFlush(new UserAccount("quiz-web-" + UUID.randomUUID(), null, "hash", "Quiz", UserRole.USER));
        learning.overview(account);
    }

    @Test
    void rendersQuestionFeedbackAndPersistentResult() throws Exception {
        var started = quizzes.startOrResume(account, QuizMode.QUICK, 5);
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        var entity = sessionRepository.findOwned(profile.getId(), started.sessionId()).orElseThrow();
        var first = itemRepository.findBySessionIdOrderByPosition(entity.getId()).get(0);

        mvc.perform(get("/quiz/session/{id}", started.sessionId()).with(user(account.getLoginId())))
                .andExpect(status().isOk()).andExpect(view().name("quiz-session"))
                .andExpect(content().string(containsString("빠른 퀴즈 진행")))
                .andExpect(content().string(not(containsString("name=\"correctAnswer\""))));
        mvc.perform(post("/quiz/session/{id}/answer", started.sessionId())
                        .with(user(account.getLoginId())).with(csrf())
                        .param("itemId", first.getId().toString()).param("answer", first.getCorrectAnswer()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("좋아요, 정확히 기억했어요.")));

        while (!quizzes.get(account, started.sessionId()).completed()) {
            var current = quizzes.get(account, started.sessionId()).currentQuestion();
            var item = itemRepository.findById(current.itemId()).orElseThrow();
            quizzes.answer(account, started.sessionId(), item.getId(), item.getCorrectAnswer());
        }
        mvc.perform(get("/quiz/session/{id}/result", started.sessionId()).with(user(account.getLoginId())))
                .andExpect(status().isOk()).andExpect(view().name("quiz-session-result"))
                .andExpect(content().string(containsString("빠른 퀴즈 완료")))
                .andExpect(content().string(containsString("Quiz EXP")));
    }

    @Test
    void personalSessionPageRequiresAuthenticationAndOwnership() throws Exception {
        var started = quizzes.startOrResume(account, QuizMode.QUICK, 5);
        mvc.perform(get("/quiz/session/{id}", started.sessionId())).andExpect(status().is3xxRedirection());
        UserAccount other = accounts.saveAndFlush(new UserAccount("quiz-web-other-" + UUID.randomUUID(), null, "hash", "Other", UserRole.USER));
        learning.overview(other);
        mvc.perform(get("/quiz/session/{id}", started.sessionId()).with(user(other.getLoginId())))
                .andExpect(status().isNotFound());
    }
}
