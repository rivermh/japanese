package com.japanese.account.service;

import com.japanese.account.entity.UserAccount;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final AccountService accountService;
    public CurrentUserService(AccountService accountService) { this.accountService = accountService; }
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
    public UserAccount currentAccount() {
        if (!isAuthenticated()) throw new IllegalStateException("로그인이 필요합니다.");
        return accountService.account(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
