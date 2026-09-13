package com.japanese.learning.repository;

import com.japanese.learning.entity.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {
    Optional<QuizSession> findFirstByLearnerProfileIdAndModeAndStateOrderByIdDesc(Long profileId, QuizMode mode, QuizSessionState state);

    @Query("select session from QuizSession session where session.learnerProfile.id=:profileId and session.publicId=:publicId")
    Optional<QuizSession> findOwned(@Param("profileId") Long profileId, @Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from QuizSession session where session.learnerProfile.id=:profileId and session.publicId=:publicId")
    Optional<QuizSession> findOwnedForUpdate(@Param("profileId") Long profileId, @Param("publicId") String publicId);
}
