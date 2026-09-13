package com.japanese.account.service;

import com.japanese.account.dto.AccountDetails;
import com.japanese.account.entity.EmailVerificationToken;
import com.japanese.account.entity.PasswordResetToken;
import com.japanese.account.entity.UserAccount;
import com.japanese.account.mail.AccountMailSender;
import com.japanese.account.repository.EmailVerificationTokenRepository;
import com.japanese.account.repository.PasswordResetTokenRepository;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.learning.repository.LearnerProfileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountManagementService {
    public enum TokenResult { SUCCESS, ALREADY_VERIFIED, INVALID_OR_EXPIRED }
    public enum DeliveryResult { SENT, THROTTLED, NOT_AVAILABLE }
    private static final Duration VERIFICATION_LIFETIME = Duration.ofHours(24);
    private static final Duration RESET_LIFETIME = Duration.ofMinutes(30);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(AccountManagementService.class);

    private final UserAccountRepository accounts;
    private final LearnerProfileRepository profiles;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final AccountMailSender mailSender;
    private final String publicBaseUrl;

    public AccountManagementService(UserAccountRepository accounts, LearnerProfileRepository profiles,
            EmailVerificationTokenRepository verificationTokens, PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwordEncoder, AccountMailSender mailSender,
            @Value("${japanese.account.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.accounts = accounts; this.profiles = profiles; this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens; this.passwordEncoder = passwordEncoder; this.mailSender = mailSender;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public AccountDetails details(UserAccount account) { return AccountDetails.from(accounts.findById(account.getId()).orElseThrow()); }

    @Transactional
    public AccountDetails changeDisplayName(UserAccount current, String requestedName) {
        String displayName = validateDisplayName(requestedName);
        UserAccount account = accounts.findByIdForUpdate(current.getId()).orElseThrow();
        account.changeDisplayName(displayName);
        var profile = profiles.findByUserAccountLoginIdForUpdate(account.getLoginId()).orElseThrow();
        profile.changeDisplayName(displayName);
        return AccountDetails.from(account);
    }

    @Transactional
    public DeliveryResult requestEmailVerification(UserAccount current) {
        return issueVerification(accounts.findByIdForUpdate(current.getId()).orElseThrow());
    }

    @Transactional
    public DeliveryResult requestEmailVerification(String email) {
        var found = accounts.findByEmail(normalizeEmail(email));
        if (found.isEmpty()) return DeliveryResult.NOT_AVAILABLE;
        return issueVerification(accounts.findByIdForUpdate(found.get().getId()).orElseThrow());
    }

    private DeliveryResult issueVerification(UserAccount account) {
        if (account.getEmail() == null || account.isEmailVerified()) return DeliveryResult.NOT_AVAILABLE;
        Instant now = Instant.now();
        var latest = verificationTokens.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(account.getId());
        if (latest.isPresent() && latest.get().getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN))) return DeliveryResult.THROTTLED;
        verificationTokens.invalidateUnused(account.getId(), now);
        String rawToken = rawToken();
        var token = verificationTokens.save(new EmailVerificationToken(account, hash(rawToken), now, now.plus(VERIFICATION_LIFETIME)));
        try {
            mailSender.sendEmailVerification(account.getEmail(), account.getDisplayName(), publicBaseUrl + "/verify-email?token=" + rawToken);
            return DeliveryResult.SENT;
        } catch (RuntimeException exception) {
            token.use(now);
            log.error("Email verification delivery failed for account {} ({})", account.getId(), exception.getClass().getSimpleName());
            return DeliveryResult.NOT_AVAILABLE;
        }
    }

    @Transactional
    public TokenResult verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return TokenResult.INVALID_OR_EXPIRED;
        var found = verificationTokens.findByTokenHashForUpdate(hash(rawToken));
        if (found.isEmpty()) return TokenResult.INVALID_OR_EXPIRED;
        var token = found.get();
        if (token.getUser().isEmailVerified()) return TokenResult.ALREADY_VERIFIED;
        Instant now = Instant.now();
        if (!token.isUsable(now)) return TokenResult.INVALID_OR_EXPIRED;
        token.use(now);
        token.getUser().verifyEmail();
        verificationTokens.invalidateUnused(token.getUser().getId(), now);
        return TokenResult.SUCCESS;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        var found = accounts.findByEmail(normalizeEmail(email));
        if (found.isEmpty()) return;
        UserAccount account = accounts.findByIdForUpdate(found.get().getId()).orElseThrow();
        Instant now = Instant.now();
        var latest = resetTokens.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(account.getId());
        if (latest.isPresent() && latest.get().getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN))) return;
        resetTokens.invalidateUnused(account.getId(), now);
        String rawToken = rawToken();
        var token = resetTokens.save(new PasswordResetToken(account, hash(rawToken), now, now.plus(RESET_LIFETIME)));
        try {
            mailSender.sendPasswordReset(account.getEmail(), account.getDisplayName(), publicBaseUrl + "/reset-password?token=" + rawToken);
        } catch (RuntimeException exception) {
            token.use(now);
            log.error("Password reset delivery failed for account {} ({})", account.getId(), exception.getClass().getSimpleName());
        }
    }

    @Transactional
    public boolean passwordResetTokenUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return false;
        return resetTokens.findByTokenHashForUpdate(hash(rawToken)).map(token -> token.isUsable(Instant.now())).orElse(false);
    }

    @Transactional
    public TokenResult resetPassword(String rawToken, String password, String confirmation) {
        validatePassword(password, confirmation);
        if (rawToken == null || rawToken.isBlank()) return TokenResult.INVALID_OR_EXPIRED;
        var found = resetTokens.findByTokenHashForUpdate(hash(rawToken));
        if (found.isEmpty()) return TokenResult.INVALID_OR_EXPIRED;
        var token = found.get(); Instant now = Instant.now();
        if (!token.isUsable(now)) return TokenResult.INVALID_OR_EXPIRED;
        token.use(now);
        token.getUser().changePasswordHash(passwordEncoder.encode(password));
        resetTokens.invalidateUnused(token.getUser().getId(), now);
        return TokenResult.SUCCESS;
    }

    private static String validateDisplayName(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException("표시 이름을 입력하세요.");
        if (result.length() > 80) throw new IllegalArgumentException("표시 이름은 80자 이하여야 합니다.");
        return result;
    }
    private static void validatePassword(String password, String confirmation) {
        if (password == null || password.length() < 8 || password.length() > 72) throw new IllegalArgumentException("비밀번호는 8~72자여야 합니다.");
        if (!password.equals(confirmation)) throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
    }
    private static String normalizeEmail(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
    private static String rawToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
