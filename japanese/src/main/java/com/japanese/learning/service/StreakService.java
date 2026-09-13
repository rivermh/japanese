package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.learning.dto.StreakDay;
import com.japanese.learning.dto.StreakStatus;
import com.japanese.learning.entity.LearnerProfile;
import com.japanese.learning.entity.LearningStreak;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningStreakRepository;
import com.japanese.learning.repository.QuizAttemptRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StreakService {
    private final LearnerProfileRepository profileRepository; private final LearningStreakRepository streakRepository;
    private final StudyRecordRepository studyRecordRepository; private final QuizAttemptRepository quizAttemptRepository; private final ZoneId learningZone;
    public StreakService(LearnerProfileRepository profileRepository, LearningStreakRepository streakRepository, StudyRecordRepository studyRecordRepository, QuizAttemptRepository quizAttemptRepository, @Value("${japanese.learning.time-zone:Asia/Seoul}") String learningTimeZone) {
        this.profileRepository=profileRepository; this.streakRepository=streakRepository; this.studyRecordRepository=studyRecordRepository; this.quizAttemptRepository=quizAttemptRepository; learningZone=ZoneId.of(learningTimeZone);
    }
    @Transactional public void recordActivity(LearnerProfile profile) {
        LearningStreak streak=streakRepository.findByLearnerProfileId(profile.getId()).orElseGet(() -> new LearningStreak(profile));
        streak.recordActivity(LocalDate.now(learningZone));
        streakRepository.save(streak);
    }
    @Transactional(readOnly = true) public StreakStatus status(UserAccount account) {
        LearnerProfile profile=profileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow(); LocalDate today=LocalDate.now(learningZone); LocalDate startDate=today.minusDays(6); Instant startAt=startDate.atStartOfDay(learningZone).toInstant();
        Set<LocalDate> studiedDays=new LinkedHashSet<>();
        studyRecordRepository.findByLearnerProfileLearnerKeyAndStudiedAtGreaterThanEqualOrderByStudiedAtAsc(profile.getLearnerKey(), startAt).forEach(record -> studiedDays.add(record.getStudiedAt().atZone(learningZone).toLocalDate()));
        quizAttemptRepository.findStreakEligibleSince(profile.getLearnerKey(), startAt).forEach(attempt -> studiedDays.add(attempt.getAnsweredAt().atZone(learningZone).toLocalDate()));
        LearningStreak streak=streakRepository.findByLearnerProfileId(profile.getId()).orElse(null);
        return new StreakStatus(streak==null?0:streak.getCurrentStreak(), streak==null?0:streak.getLongestStreak(), studiedDays.contains(today), recentDays(startDate, studiedDays));
    }
    private List<StreakDay> recentDays(LocalDate startDate, Set<LocalDate> studiedDays) {
        return java.util.stream.IntStream.range(0, 7).mapToObj(offset -> { LocalDate date=startDate.plusDays(offset); return new StreakDay(date, studiedDays.contains(date)); }).toList();
    }
}
