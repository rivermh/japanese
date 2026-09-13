package com.japanese.learning.repository;

import com.japanese.learning.entity.WeaknessReviewSession;
import com.japanese.learning.entity.WeaknessReviewSessionState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WeaknessReviewSessionRepository extends JpaRepository<WeaknessReviewSession, Long> {
    Optional<WeaknessReviewSession> findFirstByLearnerProfileIdAndStateOrderByIdDesc(Long learnerProfileId, WeaknessReviewSessionState state);
    Optional<WeaknessReviewSession> findFirstByLearnerProfileIdOrderByIdDesc(Long learnerProfileId);
    @Query("select session from WeaknessReviewSession session where session.learnerProfile.id=:profileId and session.id=:sessionId")
    Optional<WeaknessReviewSession> findOwned(Long profileId, Long sessionId);
}
