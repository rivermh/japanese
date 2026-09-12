package com.japanese.learning.repository;

import com.japanese.learning.entity.LearnerProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, Long> {

    Optional<LearnerProfile> findByLearnerKey(String learnerKey);
    Optional<LearnerProfile> findByUserAccountLoginId(String loginId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from LearnerProfile profile where profile.userAccount.loginId = :loginId")
    Optional<LearnerProfile> findByUserAccountLoginIdForUpdate(@Param("loginId") String loginId);
}
