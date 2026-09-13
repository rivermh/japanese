package com.japanese.account.repository;

import com.japanese.account.entity.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from EmailVerificationToken token where token.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
    Optional<EmailVerificationToken> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);
    @Modifying
    @Query("update EmailVerificationToken token set token.usedAt = :now where token.user.id = :userId and token.usedAt is null")
    int invalidateUnused(@Param("userId") Long userId, @Param("now") Instant now);
}
