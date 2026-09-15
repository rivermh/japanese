package com.japanese.learning.service;
import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.ContentItem;
import com.japanese.learning.dto.LearningStatistics;
import com.japanese.learning.dto.DailyStudyCount;
import com.japanese.learning.dto.LevelStudyProgress;
import com.japanese.learning.dto.StudyHistoryEntry;
import com.japanese.learning.dto.WeakContent;
import com.japanese.learning.entity.StudyRecord;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.QuizAttemptRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningAnalyticsService {
    private final LearnerProfileRepository profileRepository; private final StudyRecordRepository studyRepository;
    private final QuizAttemptRepository quizRepository; private final LearningService learningService; private final ZoneId learningZone;
    private final DueReviewQueryService dueReviews; private final LearningTime time;
    private final WeaknessNoteService weaknessNotes;
    public LearningAnalyticsService(LearnerProfileRepository p, StudyRecordRepository s, QuizAttemptRepository q, LearningService l, WeaknessNoteService weaknessNotes, DueReviewQueryService dueReviews, LearningTime time, @org.springframework.beans.factory.annotation.Value("${japanese.learning.time-zone:Asia/Seoul}") String learningTimeZone) { profileRepository=p; studyRepository=s; quizRepository=q; learningService=l; this.weaknessNotes=weaknessNotes; this.dueReviews=dueReviews; this.time=time; learningZone=ZoneId.of(learningTimeZone); }
    @Transactional(readOnly = true) public LearningStatistics statistics(UserAccount account) {
        var profile = profileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow(); String key=profile.getLearnerKey();
        long correct=studyRepository.countByLearnerProfileLearnerKeyAndResult(key, StudyResult.CORRECT)+quizRepository.countByLearnerProfileLearnerKeyAndResult(key, StudyResult.CORRECT);
        long incorrect=studyRepository.countByLearnerProfileLearnerKeyAndResult(key, StudyResult.INCORRECT)+quizRepository.countByLearnerProfileLearnerKeyAndResult(key, StudyResult.INCORRECT);
        long total=correct+incorrect; long today=learningService.todayProgress(account).completed(); Instant sevenDaysAgo=Instant.now().minusSeconds(7*86400L);
        long recent=studyRepository.countByLearnerProfileLearnerKeyAndStudiedAtGreaterThanEqual(key, sevenDaysAgo)+quizRepository.countByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqual(key, sevenDaysAgo);
        return new LearningStatistics(total,correct,incorrect,total==0?0:(int)(correct*100/total),profile.getExperience(),profile.getLevel(),today,recent);
    }
    @Transactional(readOnly = true) public List<WeakContent> weaknesses(UserAccount account) {
        return weaknessNotes.notebook(account, 10).recent().stream().map(item -> new WeakContent(
                item.content().slug(), item.content().type(), item.content().title(),
                item.regularAttemptCount()+item.confirmationAttemptCount(),
                item.regularIncorrectCount()+item.confirmationIncorrectCount())).toList();
    }
    @Transactional(readOnly = true) public List<LevelStudyProgress> levelProgress(UserAccount account) {
        return dueReviews.summarizeByJlptLevel(account, time.now());
    }
    @Transactional(readOnly = true) public List<StudyHistoryEntry> recentErrors(UserAccount account, int size) {
        String key=profileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        return studyRepository.findByLearnerProfileLearnerKeyAndResultOrderByStudiedAtDesc(key, StudyResult.INCORRECT, PageRequest.of(0, Math.min(Math.max(size, 1), 20))).stream().map(record -> {
            ContentItem item=record.getContentItem();
            String title=item.getWord()==null?item.getGrammar().getPattern():item.getWord().getExpression();
            String reading=item.getWord()==null?null:item.getWord().getReading();
            return new StudyHistoryEntry(item.getSlug(), item.getType(), title, reading, record.getResult(), record.getStudiedAt());
        }).toList();
    }
    @Transactional(readOnly = true) public List<DailyStudyCount> weeklyActivity(UserAccount account) {
        String key=profileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        LocalDate firstDate=LocalDate.now(learningZone).minusDays(6);
        Map<LocalDate, Long> counts=new LinkedHashMap<>();
        for (int day=0; day<7; day++) counts.put(firstDate.plusDays(day), 0L);
        Instant startedAt=firstDate.atStartOfDay(learningZone).toInstant();
        studyRepository.findByLearnerProfileLearnerKeyAndStudiedAtGreaterThanEqualOrderByStudiedAtAsc(key, startedAt)
                .forEach(record -> addToDay(counts, record.getStudiedAt().atZone(learningZone).toLocalDate()));
        quizRepository.findByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqualOrderByAnsweredAtAsc(key, startedAt)
                .forEach(attempt -> addToDay(counts, attempt.getAnsweredAt().atZone(learningZone).toLocalDate()));
        return counts.entrySet().stream().map(entry -> new DailyStudyCount(entry.getKey(), entry.getValue())).toList();
    }
    private void addToDay(Map<LocalDate, Long> counts, LocalDate date) {
        if (counts.containsKey(date)) counts.computeIfPresent(date, (ignored, count) -> count + 1);
    }
}
