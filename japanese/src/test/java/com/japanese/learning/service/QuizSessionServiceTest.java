package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.entity.*;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.*;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.entity.*;
import com.japanese.learning.repository.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class QuizSessionServiceTest {
    @Autowired private SampleContentDataLoader sample;
    @Autowired private UserAccountRepository accounts;
    @Autowired private ContentItemRepository contents;
    @Autowired private LearningService learning;
    @Autowired private DailyMissionService missions;
    @Autowired private StreakService streaks;
    @Autowired private QuizSessionService quizzes;
    @Autowired private QuizSessionRepository sessionRepository;
    @Autowired private QuizSessionItemRepository itemRepository;
    @Autowired private QuizAttemptRepository attempts;
    @Autowired private StudyRecordRepository records;
    @Autowired private LearningProgressRepository progress;
    @Autowired private LearnerProfileRepository profiles;
    private UserAccount account;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        if (contents.findBySlug("quiz-session-grammar").isEmpty()) {
            ContentItem grammar = new ContentItem("quiz-session-grammar", ContentType.GRAMMAR, "test", true);
            grammar.attachGrammar(new Grammar("〜ながら", "두 동작을 동시에 할 때 사용합니다.", "동사 ます형 + ながら"));
            contents.saveAndFlush(grammar);
        }
        account = accounts.saveAndFlush(new UserAccount("quiz-session-" + UUID.randomUUID(), null, "hash", "Quiz", UserRole.USER));
        learning.overview(account);
    }

    @Test
    void resumesAnswersIdempotentlyAndKeepsRegularLearningStateIsolated() {
        var missionBefore = missions.today(account);
        var streakBefore = streaks.status(account);
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        long studyBefore = records.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(profile.getLearnerKey()).size();
        long progressBefore = progress.countByLearnerProfileLearnerKey(profile.getLearnerKey());

        var started = quizzes.startOrResume(account, QuizMode.QUICK, 5);
        assertThat(quizzes.startOrResume(account, QuizMode.QUICK, 5).sessionId()).isEqualTo(started.sessionId());
        QuizSession entity = sessionRepository.findOwned(profile.getId(), started.sessionId()).orElseThrow();
        QuizSessionItem first = itemRepository.findBySessionIdOrderByPosition(entity.getId()).get(0);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> quizzes.answer(
                account, started.sessionId(), first.getId(), "not-an-offered-choice")))
                .isInstanceOf(QuizRequestException.class);
        var firstAnswer = quizzes.answer(account, started.sessionId(), first.getId(), first.getCorrectAnswer());
        int experienceAfterFirst = learning.overview(account).character().experience();
        var duplicate = quizzes.answer(account, started.sessionId(), first.getId(), "tampered retry");
        assertThat(duplicate.feedback()).isEqualTo(firstAnswer.feedback());
        assertThat(learning.overview(account).character().experience()).isEqualTo(experienceAfterFirst);

        while (!quizzes.get(account, started.sessionId()).completed()) {
            var view = quizzes.get(account, started.sessionId());
            QuizSessionItem current = itemRepository.findById(view.currentQuestion().itemId()).orElseThrow();
            String wrong = current.getQuestionType().isInput() ? "wrong" : current.getChoicesJson().contains(current.getCorrectAnswer())
                    ? choicesOtherThan(current) : "wrong";
            quizzes.answer(account, started.sessionId(), current.getId(), wrong);
        }

        var result = quizzes.result(account, started.sessionId());
        assertThat(result.totalQuestions()).isEqualTo(5);
        assertThat(result.correctCount()).isEqualTo(1);
        assertThat(result.incorrectCount()).isEqualTo(4);
        assertThat(result.earnedExperience()).isEqualTo(18);
        assertThat(attempts.findByLearnerProfileLearnerKeyOrderByAnsweredAtDesc(profile.getLearnerKey(), org.springframework.data.domain.PageRequest.of(0, 20))).hasSize(5);
        assertThat(records.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(profile.getLearnerKey())).hasSize((int) studyBefore);
        assertThat(progress.countByLearnerProfileLearnerKey(profile.getLearnerKey())).isEqualTo(progressBefore);
        assertThat(missions.today(account)).isEqualTo(missionBefore);
        assertThat(streaks.status(account)).isEqualTo(streakBefore);

        var restarted = quizzes.restart(account, started.sessionId());
        assertThat(restarted.sessionId()).isNotEqualTo(started.sessionId());
        assertThat(restarted.answeredCount()).isZero();
    }

    @Test
    void anotherLearnerCannotReadOrAnswerTheSession() {
        var started = quizzes.startOrResume(account, QuizMode.QUICK, 5);
        UserAccount other = accounts.saveAndFlush(new UserAccount("quiz-other-" + UUID.randomUUID(), null, "hash", "Other", UserRole.USER));
        learning.overview(other);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> quizzes.get(other, started.sessionId())))
                .isInstanceOf(QuizSessionNotFoundException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> quizzes.answer(other, started.sessionId(),
                started.currentQuestion().itemId(), "answer"))).isInstanceOf(QuizSessionNotFoundException.class);
    }

    private String choicesOtherThan(QuizSessionItem item) {
        try {
            List<String> choices = new tools.jackson.databind.ObjectMapper().readValue(
                    item.getChoicesJson(), new tools.jackson.core.type.TypeReference<>() { });
            return choices.stream().filter(value -> !value.equals(item.getCorrectAnswer())).findFirst().orElse("wrong");
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
}
