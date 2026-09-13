package com.japanese.account.repository;

import com.japanese.account.entity.UserAccount;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByLoginId(String loginId);
    Optional<UserAccount> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account where account.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
