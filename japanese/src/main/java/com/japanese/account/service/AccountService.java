package com.japanese.account.service;

import com.japanese.account.dto.RegistrationRequest;
import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.learning.entity.LearnerProfile;
import com.japanese.learning.repository.LearnerProfileRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements UserDetailsService {
    private final UserAccountRepository accountRepository;
    private final LearnerProfileRepository learnerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final int defaultDailyGoal;

    public AccountService(UserAccountRepository accountRepository,
                          LearnerProfileRepository learnerProfileRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${japanese.learning.daily-goal:10}") int defaultDailyGoal) {
        this.accountRepository = accountRepository;
        this.learnerProfileRepository = learnerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultDailyGoal = Math.max(defaultDailyGoal, 1);
    }

    @Transactional
    public UserAccount register(RegistrationRequest request) {
        String loginId = request.getLoginId().trim().toLowerCase(Locale.ROOT);
        String email = request.getEmail() == null || request.getEmail().isBlank()
                ? null : request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (accountRepository.existsByLoginId(loginId)) {
            throw new IllegalArgumentException("이미 사용 중인 로그인 ID입니다.");
        }
        if (email != null && accountRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        UserAccount account = accountRepository.save(new UserAccount(loginId, email,
                passwordEncoder.encode(request.getPassword()), request.getDisplayName().trim(), UserRole.USER));
        LearnerProfile profile = new LearnerProfile(account, defaultDailyGoal, "haru");
        profile.requireOnboarding();
        learnerProfileRepository.save(profile);
        return account;
    }

    @Transactional(readOnly = true)
    public UserAccount account(String loginId) {
        return accountRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccount account = account(username);
        return User.withUsername(account.getLoginId()).password(account.getPasswordHash())
                .roles(account.getRole().name()).build();
    }
}
