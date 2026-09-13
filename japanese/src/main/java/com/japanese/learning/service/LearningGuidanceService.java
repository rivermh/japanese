package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.learning.dto.*;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.QuizAttemptRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One deterministic status payload shared by the web Home and future clients. */
@Service
public class LearningGuidanceService {
    private final DailyMissionService missions;
    private final ReminderPreferenceService reminderPreferences;
    private final LearningService learning;
    private final WeaknessNoteService weaknesses;
    private final StreakService streaks;
    private final LearnerProfileRepository profiles;
    private final StudyRecordRepository records;
    private final QuizAttemptRepository quizzes;
    private final LearningProgressRepository progress;
    private final LearningTime time;
    public LearningGuidanceService(DailyMissionService missions, ReminderPreferenceService reminderPreferences,
            LearningService learning, WeaknessNoteService weaknesses, StreakService streaks,
            LearnerProfileRepository profiles, StudyRecordRepository records, QuizAttemptRepository quizzes,
            LearningProgressRepository progress, LearningTime time) {
        this.missions=missions; this.reminderPreferences=reminderPreferences; this.learning=learning;
        this.weaknesses=weaknesses; this.streaks=streaks; this.profiles=profiles; this.records=records;
        this.quizzes=quizzes; this.progress=progress; this.time=time;
    }

    @Transactional(readOnly = true)
    public LearningHomeStatus status(UserAccount account) {
        DailyMission mission = missions.today(account);
        StreakStatus streak = streaks.status(account);
        ReminderPreference preference = reminderPreferences.preference(account);
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        int weaknessesAvailable = needsWeaknessCheck(mission) ? weaknesses.notebook(account, 1).availableForFocusedReview() : 0;
        DailyLearningProgress daily = learning.todayProgress(account);
        NextLearningAction next = nextAction(mission, weaknessesAvailable, daily.completed());
        Instant lastStudy = latest(
                records.findFirstByLearnerProfileLearnerKeyOrderByStudiedAtDesc(profile.getLearnerKey()).map(value -> value.getStudiedAt()).orElse(null),
                quizzes.findFirstByLearnerProfileLearnerKeyOrderByAnsweredAtDesc(profile.getLearnerKey()).map(value -> value.getAnsweredAt()).orElse(null));
        Instant nextReview = progress.findNextReviewAt(profile.getLearnerKey());
        LearningReminder reminder = reminder(mission, streak, preference, lastStudy, nextReview, daily.remaining() == 0);
        return new LearningHomeStatus(time.today(), mission, next, reminder);
    }

    private boolean needsWeaknessCheck(DailyMission mission) { return mission.total() == 0 || mission.completed(); }

    private NextLearningAction nextAction(DailyMission mission, int weakCount, long regularCompletedToday) {
        if (mission.sessionSnapshot() && !mission.completed())
            return action("CONTINUE_TODAY", "오늘의 학습 이어하기", mission.total() - mission.completedCount() + "개를 차분히 이어가면 됩니다.", "/today", "이어하기");
        if (!mission.sessionSnapshot() && mission.review().remaining() > 0)
            return action("START_REVIEW", "오늘 복습부터 시작하기", "오늘 복습할 " + mission.review().remaining() + "개가 준비되어 있습니다.", "/today", "복습 시작");
        if (!mission.completed() && mission.total() > 0)
            return action("START_TODAY", mission.started() ? "오늘의 학습 이어가기" : "오늘의 학습 시작하기", "설정한 분량 안에서 오늘 학습을 진행합니다.", "/today", mission.started() ? "이어하기" : "시작하기");
        if (weakCount > 0)
            return action("REVIEW_WEAKNESS", "헷갈린 표현 다시 보기", "최근 오답 근거가 있는 표현을 부담 없이 다시 볼 수 있어요.", "/weaknesses", "약점 보기");
        if (mission.completed())
            return action("TODAY_COMPLETE", "오늘 학습 완료", "오늘 계획을 모두 마쳤어요. 학습 기록을 확인할 수 있습니다.", "/history", "기록 보기");
        if (regularCompletedToday > 0)
            return action("OPTIONAL_STUDY", "오늘 학습을 잘 마쳤어요", "원한다면 자유 학습으로 한 표현을 더 살펴볼 수 있어요.", "/study", "자유 학습");
        return action("NO_CONTENT", "학습할 표현을 찾아보세요", "사전에서 관심 있는 단어나 문법을 먼저 살펴볼 수 있어요.", "/dictionary", "사전 보기");
    }

    private LearningReminder reminder(DailyMission mission, StreakStatus streak, ReminderPreference preference,
            Instant lastStudy, Instant nextReview, boolean dailyGoalReached) {
        LocalDate lastStudyDate = lastStudy == null ? null : time.dateAt(lastStudy);
        boolean learningNeeded = mission.total() > mission.completedCount();
        boolean reviewScheduled = mission.review().remaining() > 0;
        boolean sufficient = mission.completed() || dailyGoalReached;
        if (!preference.enabled()) return reminder("DISABLED", null, false, learningNeeded, reviewScheduled, mission, sufficient, streak, lastStudyDate, nextReview, preference);
        if (mission.completed()) return reminder("COMPLETE", "오늘 목표를 모두 완료했어요.", true, learningNeeded, reviewScheduled, mission, sufficient, streak, lastStudyDate, nextReview, preference);
        if (time.localTime().isBefore(preference.preferredTime())) return reminder("SCHEDULED", null, false, learningNeeded, reviewScheduled, mission, sufficient, streak, lastStudyDate, nextReview, preference);
        if (reviewScheduled) return reminder("REVIEW_AVAILABLE", "오늘 복습할 카드 " + mission.review().remaining() + "개가 준비되어 있어요.", true, learningNeeded, reviewScheduled, mission, sufficient, streak, lastStudyDate, nextReview, preference);
        if (!mission.started() && mission.total() > 0) return reminder("NOT_STARTED", "오늘 학습을 아직 시작하지 않았어요.", true, learningNeeded, reviewScheduled, mission, sufficient, streak, lastStudyDate, nextReview, preference);
        if (!streak.studiedToday() && streak.currentStreak() > 0) return reminder("STREAK_OPPORTUNITY", "오늘 조금만 학습하면 연속 기록을 이어갈 수 있어요.", true, learningNeeded, reviewScheduled, mission, sufficient, streak, lastStudyDate, nextReview, preference);
        return reminder("IN_PROGRESS", learningNeeded ? "오늘 계획을 차분히 이어가고 있어요." : null, learningNeeded, learningNeeded, reviewScheduled, mission, sufficient, streak, lastStudyDate, nextReview, preference);
    }

    private LearningReminder reminder(String type, String message, boolean visible, boolean learningNeeded,
            boolean reviewScheduled, DailyMission mission, boolean sufficient, StreakStatus streak,
            LocalDate lastStudyDate, Instant nextReview, ReminderPreference preference) {
        return new LearningReminder(type, message, visible, learningNeeded, reviewScheduled, mission.completed(),
                sufficient, streak.currentStreak(), lastStudyDate, nextReview, preference);
    }

    private NextLearningAction action(String type, String title, String description, String target, String label) {
        return new NextLearningAction(type, title, description, target, label);
    }
    private Instant latest(Instant first, Instant second) {
        if (first == null) return second; if (second == null) return first; return first.isAfter(second) ? first : second;
    }
}
