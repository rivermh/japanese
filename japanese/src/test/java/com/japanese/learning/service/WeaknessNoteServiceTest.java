package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.GrammarConfirmationType;
import com.japanese.content.repository.GrammarConfirmationQuestionRepository;
import com.japanese.content.service.GrammarCurationService;
import com.japanese.content.service.GrammarLearningService;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("sample")
class WeaknessNoteServiceTest {
    @Autowired private SampleContentDataLoader sample;
    @Autowired private UserAccountRepository accounts;
    @Autowired private LearningService learning;
    @Autowired private WeaknessNoteService weaknessNotes;
    @Autowired private WeaknessReviewSessionService sessions;
    @Autowired private StudyRecordRepository records;
    @Autowired private LearnerProfileRepository profiles;
    @Autowired private LearningProgressRepository progresses;
    @Autowired private GrammarCurationService curation;
    @Autowired private GrammarLearningService grammarLearning;
    @Autowired private GrammarConfirmationQuestionRepository questions;
    private UserAccount account;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        account=accounts.saveAndFlush(new UserAccount("weakness-" + UUID.randomUUID(), null, "hash", "약점 사용자", UserRole.USER));
        learning.overview(account);
    }

    @Test
    void separatesCurrentFrequentAndImprovingWeaknessesFromActualAttempts() {
        learning.answer(account, "taberu", StudyResult.INCORRECT, "taberu-wrong-1", false);
        learning.answer(account, "taberu", StudyResult.INCORRECT, "taberu-wrong-2", false);

        learning.answer(account, "temo-ii", StudyResult.INCORRECT, "grammar-wrong-1", false);
        learning.answer(account, "temo-ii", StudyResult.INCORRECT, "grammar-wrong-2", false);
        learning.answer(account, "temo-ii", StudyResult.CORRECT, "grammar-correct-1", true);
        learning.answer(account, "temo-ii", StudyResult.CORRECT, "grammar-correct-2", true);

        var notebook=weaknessNotes.notebook(account);
        assertThat(notebook.recent()).extracting(item -> item.content().slug()).contains("taberu");
        assertThat(notebook.frequent()).extracting(item -> item.content().slug()).contains("taberu");
        assertThat(notebook.consecutive()).extracting(item -> item.content().slug()).contains("taberu");
        assertThat(notebook.improving()).extracting(item -> item.content().slug()).contains("temo-ii");
        assertThat(notebook.recent().stream().filter(item -> item.content().slug().equals("taberu")).findFirst().orElseThrow())
                .satisfies(item -> { assertThat(item.regularIncorrectCount()).isEqualTo(2); assertThat(item.consecutiveIncorrectCount()).isEqualTo(2); });
    }

    @Test
    void includesGrammarConfirmationErrorsWithoutChangingSrsAndKeepsUsersIsolated() {
        var question=curation.saveQuestion("temo-ii", GrammarConfirmationType.CONTEXT_GAP, "Choose", "context", "Reviewed", "test:weakness",
                List.of(new GrammarCurationService.ChoiceDraft("A", true), new GrammarCurationService.ChoiceDraft("B", false)));
        question.approveForPublication(); questions.saveAndFlush(question);
        var publicQuestion=grammarLearning.confirmationQuestions("temo-ii").get(0);
        grammarLearning.answer(account, publicQuestion.id(), publicQuestion.choices().get(1).id());

        assertThat(weaknessNotes.notebook(account).recent()).anySatisfy(item -> {
            assertThat(item.content().slug()).isEqualTo("temo-ii");
            assertThat(item.confirmationIncorrectCount()).isEqualTo(1);
        });
        UserAccount other=accounts.saveAndFlush(new UserAccount("weakness-other-" + UUID.randomUUID(), null, "hash", "다른 사용자", UserRole.USER));
        learning.overview(other);
        assertThat(weaknessNotes.notebook(other).recent()).isEmpty();
        assertThat(progresses.countByLearnerProfileLearnerKey(learnerKey(other))).isZero();
    }

    @Test
    void focusedRetrainingResumesAndDoesNotFarmExperienceOrDailyGoal() {
        learning.answer(account, "taberu", StudyResult.INCORRECT, "weakness-session-origin", false);
        String learnerKey=profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        int experienceBefore=learning.overview(account).character().experience();
        long goalBefore=learning.todayProgress(account).completed();
        long recordBefore=records.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(learnerKey).size();
        var started=sessions.startOrResume(account);
        assertThat(started.currentSlug()).isEqualTo("taberu");
        var completed=sessions.complete(account, started.currentSlug(), StudyResult.CORRECT);
        sessions.complete(account, "taberu", StudyResult.INCORRECT);

        assertThat(records.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(learnerKey)).hasSize((int) recordBefore + 1);
        assertThat(learning.overview(account).character().experience()).isEqualTo(experienceBefore);
        assertThat(learning.todayProgress(account).completed()).isEqualTo(goalBefore);
        assertThat(progresses.countByLearnerProfileLearnerKey(learnerKey)).isEqualTo(1);
        assertThat(completed.completedCount()).isEqualTo(completed.totalCount());
    }

    private String learnerKey(UserAccount value) {
        return profiles.findByUserAccountLoginId(value.getLoginId()).orElseThrow().getLearnerKey();
    }
}
