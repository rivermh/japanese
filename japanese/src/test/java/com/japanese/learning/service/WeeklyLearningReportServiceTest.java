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
import com.japanese.learning.repository.GrammarConfirmationAttemptRepository;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.QuizAttemptRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("sample")
class WeeklyLearningReportServiceTest {
    @Autowired private SampleContentDataLoader sample;
    @Autowired private UserAccountRepository accounts;
    @Autowired private LearnerProfileRepository profiles;
    @Autowired private LearningService learning;
    @Autowired private WeeklyLearningReportService reports;
    @Autowired private StudyRecordRepository records;
    @Autowired private QuizAttemptRepository quizzes;
    @Autowired private GrammarConfirmationAttemptRepository confirmations;
    @Autowired private GrammarCurationService curation;
    @Autowired private GrammarLearningService grammarLearning;
    @Autowired private GrammarConfirmationQuestionRepository questions;
    private UserAccount account;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        account=accounts.saveAndFlush(new UserAccount("weekly-" + UUID.randomUUID(), null, "hash", "주간 사용자", UserRole.USER));
        learning.overview(account);
    }

    @Test
    void separatesSevenDayRegularRetrainingQuizAndConfirmationAndComparesPreviousWeek() {
        String key=learnerKey(account);
        LocalDate firstDay=LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(6);
        learning.answer(account, "taberu", StudyResult.CORRECT, "weekly-boundary", false);
        var boundary=records.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(key).get(0);
        ReflectionTestUtils.setField(boundary, "studiedAt", firstDay.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
        records.saveAndFlush(boundary);

        learning.answer(account, "server", StudyResult.CORRECT, "previous-week", false);
        var previous=records.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(key).get(0);
        ReflectionTestUtils.setField(previous, "studiedAt", firstDay.minusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
        records.saveAndFlush(previous);

        learning.answer(account, "temo-ii", StudyResult.INCORRECT, "weekly-new-grammar", false);
        learning.answer(account, "temo-ii", StudyResult.CORRECT, "weekly-review", false);
        learning.answer(account, "temo-ii", StudyResult.CORRECT, "weekly-retrain", true);
        learning.recordQuizAnswer(account, 101L, true);

        var question=curation.saveQuestion("temo-ii", GrammarConfirmationType.CONTEXT_GAP, "Choose", "context", "Reviewed", "test:weekly",
                List.of(new GrammarCurationService.ChoiceDraft("A", true), new GrammarCurationService.ChoiceDraft("B", false)));
        question.approveForPublication(); questions.saveAndFlush(question);
        var confirmation=grammarLearning.confirmationQuestions("temo-ii").get(0);
        grammarLearning.answer(account, confirmation.id(), confirmation.choices().get(1).id());

        var report=reports.report(account);
        assertThat(report.currentStudy().newWordCount()).isEqualTo(1);
        assertThat(report.currentStudy().newGrammarCount()).isEqualTo(1);
        assertThat(report.currentStudy().reviewCount()).isEqualTo(1);
        assertThat(report.currentStudy().retrainCount()).isEqualTo(1);
        assertThat(report.currentStudy().regularCorrectCount()).isEqualTo(2);
        assertThat(report.currentStudy().regularIncorrectCount()).isEqualTo(1);
        assertThat(report.previousStudy().newWordCount()).isEqualTo(1);
        assertThat(report.currentQuiz().attemptCount()).isEqualTo(1);
        assertThat(report.currentQuiz().experience()).isEqualTo(10);
        assertThat(report.currentConfirmation().attemptCount()).isEqualTo(1);
        assertThat(report.currentConfirmation().incorrectCount()).isEqualTo(1);
        assertThat(report.dailyActivity()).hasSize(7);
        assertThat(report.dailyActivity().get(0).regularCount()).isEqualTo(1);
        assertThat(report.jlptProgressChanges()).anySatisfy(change -> {
            assertThat(change.levelCode()).isEqualTo("N5");
            assertThat(change.newlyStartedCount()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    void isEmptyForAnotherUserAndDoesNotReadTheirWeeklyRecords() {
        learning.answer(account, "taberu", StudyResult.INCORRECT, "weekly-private", false);
        UserAccount other=accounts.saveAndFlush(new UserAccount("weekly-other-" + UUID.randomUUID(), null, "hash", "다른 사용자", UserRole.USER));
        learning.overview(other);

        var report=reports.report(other);

        assertThat(report.currentStudy().regularContentCount()).isZero();
        assertThat(report.currentQuiz().attemptCount()).isZero();
        assertThat(report.currentConfirmation().attemptCount()).isZero();
        assertThat(report.weakWords()).isEmpty();
        assertThat(report.weakGrammar()).isEmpty();
    }

    private String learnerKey(UserAccount value) {
        return profiles.findByUserAccountLoginId(value.getLoginId()).orElseThrow().getLearnerKey();
    }
}
