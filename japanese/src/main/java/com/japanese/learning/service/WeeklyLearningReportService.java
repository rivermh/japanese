package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.ContentType;
import com.japanese.learning.dto.WeaknessNoteItem;
import com.japanese.learning.dto.WeeklyLearningDay;
import com.japanese.learning.dto.WeeklyLearningReport;
import com.japanese.learning.dto.WeeklyRecommendation;
import com.japanese.learning.dto.WeeklyStudyAggregate;
import com.japanese.learning.entity.StudyActivityType;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.GrammarConfirmationAttemptRepository;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.QuizAttemptRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Seven-day read model built from range aggregates, not loaded attempt histories. */
@Service
public class WeeklyLearningReportService {
    private final LearnerProfileRepository profiles;
    private final StudyRecordRepository records;
    private final QuizAttemptRepository quizzes;
    private final GrammarConfirmationAttemptRepository confirmations;
    private final DueReviewQueryService dueReviews;
    private final LearningTime time;
    private final WeaknessNoteService weaknesses;
    private final StreakService streaks;
    private final ZoneId zone;

    public WeeklyLearningReportService(LearnerProfileRepository profiles, StudyRecordRepository records,
            QuizAttemptRepository quizzes, GrammarConfirmationAttemptRepository confirmations,
            WeaknessNoteService weaknesses, StreakService streaks, DueReviewQueryService dueReviews,
            LearningTime time, @Value("${japanese.learning.time-zone:Asia/Seoul}") String timeZone) {
        this.profiles=profiles; this.records=records; this.quizzes=quizzes; this.confirmations=confirmations;
        this.weaknesses=weaknesses; this.streaks=streaks; this.dueReviews=dueReviews;
        this.time=time; this.zone=ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public WeeklyLearningReport report(UserAccount account) {
        Instant asOf = time.now();
        LocalDate endDate=time.dateAt(asOf);
        LocalDate startDate=endDate.minusDays(6);
        Instant start=startDate.atStartOfDay(zone).toInstant();
        Instant end=endDate.plusDays(1).atStartOfDay(zone).toInstant();
        Instant previousStart=start.minusSeconds(7 * 86_400L);
        String learnerKey=profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        WeeklyStudyAggregate current=study(learnerKey, start, end);
        WeeklyStudyAggregate previous=study(learnerKey, previousStart, start);
        var currentQuiz=quizzes.summarizeWeeklyQuiz(learnerKey, start, end, StudyResult.CORRECT, StudyResult.INCORRECT);
        var previousQuiz=quizzes.summarizeWeeklyQuiz(learnerKey, previousStart, start, StudyResult.CORRECT, StudyResult.INCORRECT);
        var currentConfirmation=confirmations.summarizeWeeklyConfirmation(learnerKey, start, end, StudyResult.CORRECT, StudyResult.INCORRECT);
        var previousConfirmation=confirmations.summarizeWeeklyConfirmation(learnerKey, previousStart, start, StudyResult.CORRECT, StudyResult.INCORRECT);
        List<WeeklyLearningDay> daily=new ArrayList<>();
        int learningDays=0, previousLearningDays=0;
        for (int offset=0; offset<7; offset++) {
            LocalDate day=startDate.plusDays(offset);
            WeeklyStudyAggregate aggregate=study(learnerKey, day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant());
            daily.add(new WeeklyLearningDay(day, aggregate.regularContentCount(), aggregate.retrainCount(), aggregate.regularExperience()));
            if (aggregate.regularContentCount()+aggregate.retrainCount()>0) learningDays++;
            LocalDate prior=day.minusDays(7);
            WeeklyStudyAggregate previousDay=study(learnerKey, prior.atStartOfDay(zone).toInstant(), prior.plusDays(1).atStartOfDay(zone).toInstant());
            if (previousDay.regularContentCount()+previousDay.retrainCount()>0) previousLearningDays++;
        }
        var weeklyWeaknesses=weaknesses.notebookForDays(account, 7, 10);
        List<WeaknessNoteItem> currentWeaknesses=weeklyWeaknesses.recent();
        List<WeaknessNoteItem> weakWords=filter(currentWeaknesses, ContentType.WORD);
        List<WeaknessNoteItem> weakGrammar=filter(currentWeaknesses, ContentType.GRAMMAR);
        long due=dueReviews.countDue(dueReviews.currentScope(account, asOf));
        return new WeeklyLearningReport(startDate, endDate, current, previous, currentQuiz, previousQuiz,
                currentConfirmation, previousConfirmation, learningDays, previousLearningDays,
                streaks.status(account).currentStreak(), List.copyOf(daily),
                records.summarizeNewlyStartedByJlptLevel(learnerKey, start, end, StudyActivityType.NEW),
                weakWords, weakGrammar, weeklyWeaknesses.improving(),
                recommendations(due, currentWeaknesses, learningDays, current));
    }

    private WeeklyStudyAggregate study(String learnerKey, Instant start, Instant end) {
        return records.summarizeWeeklyStudy(learnerKey, start, end, StudyActivityType.NEW, StudyActivityType.REVIEW,
                StudyActivityType.RETRAIN, ContentType.WORD, ContentType.GRAMMAR, StudyResult.CORRECT,
                StudyResult.INCORRECT, LearningService.CORRECT_EXP, LearningService.INCORRECT_EXP);
    }
    private List<WeaknessNoteItem> filter(List<WeaknessNoteItem> values, ContentType type) {
        return values.stream().filter(item -> item.content().type()==type)
                .sorted(Comparator.comparing(WeaknessNoteItem::consecutiveIncorrectCount).reversed()
                        .thenComparing(item -> item.regularIncorrectCount()+item.confirmationIncorrectCount(), Comparator.reverseOrder()))
                .limit(5).toList();
    }
    private List<WeeklyRecommendation> recommendations(long due, List<WeaknessNoteItem> weak, int learningDays, WeeklyStudyAggregate current) {
        List<WeeklyRecommendation> values=new ArrayList<>();
        if (due>0) values.add(new WeeklyRecommendation("복습부터 처리하기", "현재 처리할 복습 카드가 " + due + "개 있습니다.", "/study?reviewOnly=true", "복습 보기"));
        if (!weak.isEmpty()) values.add(new WeeklyRecommendation("이번 주 오답 다시 보기", "최근 오답 근거가 있는 콘텐츠를 집중 재학습으로 이어갈 수 있습니다.", "/weaknesses", "약점 노트"));
        if (learningDays<3) values.add(new WeeklyRecommendation("학습일 늘리기", "최근 7일 중 학습한 날이 " + learningDays + "일입니다.", "/today", "오늘의 학습"));
        else if (current.newWordCount()+current.newGrammarCount()==0) values.add(new WeeklyRecommendation("새 콘텐츠 한 번 시작하기", "이번 주 새로 시작한 콘텐츠가 없습니다.", "/today", "오늘의 학습"));
        else values.add(new WeeklyRecommendation("다음 학습 이어가기", "현재 흐름을 유지하면 복습 주기가 자연스럽게 이어집니다.", "/today", "오늘의 학습"));
        return values.stream().limit(3).toList();
    }
}
