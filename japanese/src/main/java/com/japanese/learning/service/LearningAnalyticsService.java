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
import com.japanese.learning.repository.LearningProgressRepository;
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
    private final QuizAttemptRepository quizRepository; private final LearningProgressRepository progressRepository; private final LearningService learningService; private final ZoneId learningZone;
    public LearningAnalyticsService(LearnerProfileRepository p, StudyRecordRepository s, QuizAttemptRepository q, LearningProgressRepository progressRepository, LearningService l, @org.springframework.beans.factory.annotation.Value("${japanese.learning.time-zone:Asia/Seoul}") String learningTimeZone) { profileRepository=p; studyRepository=s; quizRepository=q; this.progressRepository=progressRepository; learningService=l; learningZone=ZoneId.of(learningTimeZone); }
    @Transactional(readOnly = true) public LearningStatistics statistics(UserAccount account) {
        var profile = profileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow(); String key=profile.getLearnerKey();
        long correct=studyRepository.countByLearnerProfileLearnerKeyAndResult(key, StudyResult.CORRECT)+quizRepository.countByLearnerProfileLearnerKeyAndResult(key, StudyResult.CORRECT);
        long incorrect=studyRepository.countByLearnerProfileLearnerKeyAndResult(key, StudyResult.INCORRECT)+quizRepository.countByLearnerProfileLearnerKeyAndResult(key, StudyResult.INCORRECT);
        long total=correct+incorrect; long today=learningService.todayProgress(account).completed(); Instant sevenDaysAgo=Instant.now().minusSeconds(7*86400L);
        long recent=studyRepository.countByLearnerProfileLearnerKeyAndStudiedAtGreaterThanEqual(key, sevenDaysAgo)+quizRepository.countByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqual(key, sevenDaysAgo);
        return new LearningStatistics(total,correct,incorrect,total==0?0:(int)(correct*100/total),profile.getExperience(),profile.getLevel(),today,recent);
    }
    @Transactional(readOnly = true) public List<WeakContent> weaknesses(UserAccount account) {
        String key=profileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        Map<String, long[]> counts=new LinkedHashMap<>(); Map<String, ContentItem> items=new LinkedHashMap<>();
        for (StudyRecord record: studyRepository.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(key, PageRequest.of(0,200))) {
            ContentItem item=record.getContentItem(); long[] c=counts.computeIfAbsent(item.getSlug(), ignored->new long[2]); c[0]++; if(record.getResult()==StudyResult.INCORRECT)c[1]++; items.putIfAbsent(item.getSlug(),item);
        }
        return counts.entrySet().stream().filter(e->e.getValue()[1]>0).sorted((a,b)->Long.compare(b.getValue()[1],a.getValue()[1])).limit(10).map(e->{ContentItem i=items.get(e.getKey()); String title=i.getWord()==null?i.getGrammar().getPattern():i.getWord().getExpression(); return new WeakContent(i.getSlug(),i.getType(),title,e.getValue()[0],e.getValue()[1]);}).toList();
    }
    @Transactional(readOnly = true) public List<LevelStudyProgress> levelProgress(UserAccount account) {
        String key=profileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        return progressRepository.summarizeByJlptLevel(key, Instant.now());
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
