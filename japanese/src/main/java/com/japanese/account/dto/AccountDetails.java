package com.japanese.account.dto;

import com.japanese.account.entity.UserAccount;
import java.time.Instant;

public record AccountDetails(String loginId, String email, String displayName, boolean emailVerified, Instant joinedAt) {
    public static AccountDetails from(UserAccount account) {
        return new AccountDetails(account.getLoginId(), account.getEmail(), account.getDisplayName(), account.isEmailVerified(), account.getJoinedAt());
    }
}
