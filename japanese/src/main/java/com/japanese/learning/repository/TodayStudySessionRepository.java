package com.japanese.learning.repository;

import com.japanese.learning.entity.TodayStudySession;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface TodayStudySessionRepository extends JpaRepository<TodayStudySession, Long> {
    Optional<TodayStudySession> findByLearnerProfileIdAndSessionDate(Long learnerProfileId, LocalDate sessionDate);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from TodayStudySession session where session.learnerProfile.id=:profileId and session.sessionDate=:date")
    Optional<TodayStudySession> findForUpdate(Long profileId, LocalDate date);
}
