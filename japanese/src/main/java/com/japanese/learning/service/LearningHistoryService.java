package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.ContentItem;
import com.japanese.learning.dto.DailyContentActivity;
import com.japanese.learning.dto.DailyLearningReport;
import com.japanese.learning.dto.LearningActivityDay;
import com.japanese.learning.dto.MonthlyLearningActivity;
import com.japanese.learning.entity.QuizAttempt;
import com.japanese.learning.entity.StudyActivityType;
import com.japanese.learning.entity.StudyRecord;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.QuizAttemptRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningHistoryService {

    private final LearnerProfileRepository profileRepository;
    private final StudyRecordRepository studyRecordRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final LearningProgressRepository progressRepository;
    private final LearningService learningService;
    private final StreakService streakService;
    private final ZoneId learningZone;

    public LearningHistoryService(
            LearnerProfileRepository profileRepository,
            StudyRecordRepository studyRecordRepository,
            QuizAttemptRepository quizAttemptRepository,
            LearningProgressRepository progressRepository,
            LearningService learningService,
            StreakService streakService,
            @Value("${japanese.learning.time-zone:Asia/Seoul}") String learningTimeZone
    ) {
        this.profileRepository = profileRepository;
        this.studyRecordRepository = studyRecordRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.progressRepository = progressRepository;
        this.learningService = learningService;
        this.streakService = streakService;
        this.learningZone = ZoneId.of(learningTimeZone);
    }

    @Transactional(readOnly = true)
    public MonthlyLearningActivity month(UserAccount account, YearMonth month) {
        String learnerKey = learnerKey(account);
        Instant start = month.atDay(1).atStartOfDay(learningZone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(learningZone).toInstant();
        Map<LocalDate, Counts> counts = new LinkedHashMap<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            counts.put(month.atDay(day), new Counts());
        }
        studyRecordRepository.findByLearnerProfileLearnerKeyAndStudiedAtGreaterThanEqualAndStudiedAtLessThanOrderByStudiedAtAsc(
                        learnerKey, start, end)
                .forEach(record -> addRecord(counts.get(record.getStudiedAt().atZone(learningZone).toLocalDate()), record));
        quizAttemptRepository.findByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThanOrderByAnsweredAtAsc(
                        learnerKey, start, end)
                .forEach(attempt -> addQuiz(counts.get(attempt.getAnsweredAt().atZone(learningZone).toLocalDate()), attempt));
        return new MonthlyLearningActivity(month, counts.entrySet().stream()
                .map(entry -> entry.getValue().toDay(entry.getKey()))
                .toList());
    }

    @Transactional(readOnly = true)
    public DailyLearningReport day(UserAccount account, LocalDate date) {
        String learnerKey = learnerKey(account);
        Instant start = date.atStartOfDay(learningZone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(learningZone).toInstant();
        List<StudyRecord> records = studyRecordRepository
                .findByLearnerProfileLearnerKeyAndStudiedAtGreaterThanEqualAndStudiedAtLessThanOrderByStudiedAtAsc(
                        learnerKey, start, end);
        List<QuizAttempt> quizzes = quizAttemptRepository
                .findByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThanOrderByAnsweredAtAsc(
                        learnerKey, start, end);
        Counts counts = new Counts();
        List<DailyContentActivity> activities = new ArrayList<>();
        for (StudyRecord record : records) {
            addRecord(counts, record);
            ContentItem item = record.getContentItem();
            activities.add(new DailyContentActivity(
                    item.getSlug(), item.getType(),
                    item.getWord() == null ? item.getGrammar().getPattern() : item.getWord().getExpression(),
                    item.getWord() == null ? null : item.getWord().getReading(),
                    normalizedActivityType(record), record.getResult(), record.getStudiedAt()));
        }
        quizzes.forEach(quiz -> addQuiz(counts, quiz));
        LocalDate today = LocalDate.now(learningZone);
        int goal = learningService.todayProgress(account).goal();
        int regularGoalCompleted = date.equals(today)
                ? (int) learningService.todayProgress(account).completed()
                : counts.newWordCount + counts.newGrammarCount + counts.reviewCount;
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(learningZone).toInstant();
        Instant dayAfterTomorrow = today.plusDays(2).atStartOfDay(learningZone).toInstant();
        Instant upcomingEnd = today.plusDays(8).atStartOfDay(learningZone).toInstant();
        int dueNow = (int) Math.min(Integer.MAX_VALUE,
                progressRepository.countByLearnerProfileLearnerKeyAndNextReviewAtLessThanEqual(learnerKey, Instant.now()));
        int tomorrow = (int) Math.min(Integer.MAX_VALUE,
                progressRepository.countByLearnerProfileLearnerKeyAndNextReviewAtGreaterThanEqualAndNextReviewAtLessThan(
                        learnerKey, tomorrowStart, dayAfterTomorrow));
        int upcoming = (int) Math.min(Integer.MAX_VALUE,
                progressRepository.countByLearnerProfileLearnerKeyAndNextReviewAtGreaterThanEqualAndNextReviewAtLessThan(
                        learnerKey, dayAfterTomorrow, upcomingEnd));
        return new DailyLearningReport(date, counts.newWordCount, counts.newGrammarCount, counts.reviewCount,
                counts.retrainCount, counts.correctCount, counts.incorrectCount, regularGoalCompleted, goal, dueNow,
                counts.experience, streakService.status(account).currentStreak(), tomorrow, upcoming,
                counts.quizCount, counts.quizCorrectCount, counts.quizIncorrectCount, counts.quizExperience, activities);
    }

    private String learnerKey(UserAccount account) {
        return profileRepository.findByUserAccountLoginId(account.getLoginId())
                .orElseThrow(() -> new java.util.NoSuchElementException("Learner profile not found"))
                .getLearnerKey();
    }

    private void addRecord(Counts counts, StudyRecord record) {
        StudyActivityType type = normalizedActivityType(record);
        if (type == StudyActivityType.NEW) {
            if (record.getContentItem().getType() == com.japanese.content.entity.ContentType.WORD) counts.newWordCount++;
            else counts.newGrammarCount++;
        } else if (type == StudyActivityType.REVIEW) {
            counts.reviewCount++;
        } else {
            counts.retrainCount++;
        }
        if (record.getResult() == StudyResult.CORRECT) counts.correctCount++;
        else counts.incorrectCount++;
        if (type != StudyActivityType.RETRAIN) {
            counts.experience += record.getResult() == StudyResult.CORRECT ? 10 : 2;
        }
    }

    private void addQuiz(Counts counts, QuizAttempt attempt) {
        counts.quizCount++;
        if (attempt.getResult() == StudyResult.CORRECT) counts.quizCorrectCount++;
        else counts.quizIncorrectCount++;
        counts.quizExperience += attempt.getEarnedExperience();
    }

    private StudyActivityType normalizedActivityType(StudyRecord record) {
        return record.getActivityType() == null ? StudyActivityType.REVIEW : record.getActivityType();
    }

    private static final class Counts {
        private int newWordCount;
        private int newGrammarCount;
        private int reviewCount;
        private int retrainCount;
        private int correctCount;
        private int incorrectCount;
        private int experience;
        private int quizCount;
        private int quizCorrectCount;
        private int quizIncorrectCount;
        private int quizExperience;

        private LearningActivityDay toDay(LocalDate date) {
            return new LearningActivityDay(date, newWordCount + newGrammarCount, reviewCount, retrainCount,
                    quizCount, experience + quizExperience);
        }
    }
}
