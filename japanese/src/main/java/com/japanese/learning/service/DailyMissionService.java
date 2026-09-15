package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.ContentType;
import com.japanese.learning.dto.DailyMission;
import com.japanese.learning.dto.MissionProgress;
import com.japanese.learning.entity.StudyActivityType;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read model over the immutable Today snapshot or today's regular StudyRecords. */
@Service
public class DailyMissionService {
    private final TodayStudySessionService sessions;
    private final LearningService learning;
    private final LearnerProfileRepository profiles;
    private final StudyRecordRepository records;
    private final LearningTime time;
    public DailyMissionService(TodayStudySessionService sessions, LearningService learning,
            LearnerProfileRepository profiles, StudyRecordRepository records, LearningTime time) {
        this.sessions=sessions; this.learning=learning; this.profiles=profiles; this.records=records; this.time=time;
    }

    @Transactional(readOnly = true)
    public DailyMission today(UserAccount account) {
        return today(account, time.now());
    }

    @Transactional(readOnly = true)
    DailyMission today(UserAccount account, Instant asOf) {
        var existing = sessions.findCurrent(account);
        if (existing.isPresent()) {
            var session = existing.get();
            return mission(asOf, true, session.completedCount() > 0, session.reviewCount(), session.completedReviewCount(),
                    session.newWordCount(), session.completedNewWordCount(), session.newGrammarCount(), session.completedNewGrammarCount());
        }
        String learnerKey = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        var plan = learning.todayPlan(account, asOf);
        var todayStart = time.dateAt(asOf).atStartOfDay(time.zone()).toInstant();
        int reviewDone = safe(records.countByLearnerAndStudiedAtGreaterThanEqualAndActivityType(
                learnerKey, todayStart, StudyActivityType.REVIEW));
        int wordsDone = safe(records.countByLearnerAndStudiedAtGreaterThanEqualAndActivityType(
                learnerKey, todayStart, StudyActivityType.NEW, ContentType.WORD));
        int grammarDone = safe(records.countByLearnerAndStudiedAtGreaterThanEqualAndActivityType(
                learnerKey, todayStart, StudyActivityType.NEW, ContentType.GRAMMAR));
        return mission(asOf, false, reviewDone + wordsDone + grammarDone > 0,
                reviewDone + plan.reviewCount(), reviewDone,
                wordsDone + plan.newWordCount(), wordsDone,
                grammarDone + plan.newGrammarCount(), grammarDone);
    }

    private DailyMission mission(Instant asOf, boolean snapshot, boolean started, int reviewTarget, int reviewDone,
            int wordTarget, int wordDone, int grammarTarget, int grammarDone) {
        var review = new MissionProgress("REVIEW", "복습", reviewTarget, Math.min(reviewDone, reviewTarget));
        var words = new MissionProgress("NEW_WORD", "새 단어", wordTarget, Math.min(wordDone, wordTarget));
        var grammar = new MissionProgress("NEW_GRAMMAR", "새 문법", grammarTarget, Math.min(grammarDone, grammarTarget));
        int total = review.target() + words.target() + grammar.target();
        int completed = review.completed() + words.completed() + grammar.completed();
        return new DailyMission(time.dateAt(asOf), snapshot, started, total > 0 && completed >= total,
                total, completed, review, words, grammar);
    }
    private int safe(long value) { return (int) Math.min(Integer.MAX_VALUE, Math.max(value, 0)); }
}
