package com.japanese.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.dto.RegistrationRequest;
import com.japanese.account.entity.EmailVerificationToken;
import com.japanese.account.entity.PasswordResetToken;
import com.japanese.account.entity.UserAccount;
import com.japanese.account.mail.DevelopmentAccountMailSender;
import com.japanese.account.repository.EmailVerificationTokenRepository;
import com.japanese.account.repository.PasswordResetTokenRepository;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.learning.repository.LearnerProfileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class AccountManagementServiceTest {
    @Autowired AccountService registrations;
    @Autowired AccountManagementService service;
    @Autowired UserAccountRepository users;
    @Autowired LearnerProfileRepository profiles;
    @Autowired EmailVerificationTokenRepository verificationTokens;
    @Autowired PasswordResetTokenRepository resetTokens;
    @Autowired DevelopmentAccountMailSender mailbox;
    @Autowired PasswordEncoder encoder;

    @Test void verifiesOneSafeTokenAndDoesNotChangeAnotherUser() {
        UserAccount owner = account("verify-owner"); UserAccount other = account("verify-other");
        assertThat(service.requestEmailVerification(owner)).isEqualTo(AccountManagementService.DeliveryResult.SENT);
        String raw = token(mailbox.latest(owner.getEmail()).url());
        assertThat(service.verifyEmail(raw)).isEqualTo(AccountManagementService.TokenResult.SUCCESS);
        assertThat(users.findById(owner.getId()).orElseThrow().isEmailVerified()).isTrue();
        assertThat(users.findById(other.getId()).orElseThrow().isEmailVerified()).isFalse();
        assertThat(service.verifyEmail(raw)).isEqualTo(AccountManagementService.TokenResult.ALREADY_VERIFIED);
    }

    @Test void rejectsInvalidExpiredAndUsedVerificationTokensAndThrottlesResend() {
        UserAccount account = account("verify-edge"); Instant now = Instant.now();
        verificationTokens.save(new EmailVerificationToken(account, hash("expired"), now.minusSeconds(120), now.minusSeconds(1)));
        var used = verificationTokens.save(new EmailVerificationToken(account, hash("used"), now.minusSeconds(120), now.plusSeconds(300))); used.use(now.minusSeconds(30));
        assertThat(service.verifyEmail("wrong")).isEqualTo(AccountManagementService.TokenResult.INVALID_OR_EXPIRED);
        assertThat(service.verifyEmail("expired")).isEqualTo(AccountManagementService.TokenResult.INVALID_OR_EXPIRED);
        assertThat(service.verifyEmail("used")).isEqualTo(AccountManagementService.TokenResult.INVALID_OR_EXPIRED);
        assertThat(service.requestEmailVerification(account)).isEqualTo(AccountManagementService.DeliveryResult.SENT);
        assertThat(service.requestEmailVerification(account)).isEqualTo(AccountManagementService.DeliveryResult.THROTTLED);
    }

    @Test void treatsLegacyNullVerificationStateAsVerified() {
        UserAccount account = account("legacy-verified");
        ReflectionTestUtils.setField(account, "emailVerified", null);
        users.saveAndFlush(account);
        assertThat(account.isEmailVerified()).isTrue();
        assertThat(service.requestEmailVerification(account)).isEqualTo(AccountManagementService.DeliveryResult.NOT_AVAILABLE);
    }

    @Test void resetsPasswordOnceAndInvalidatesOldPassword() {
        UserAccount account = account("reset-normal"); String oldHash = account.getPasswordHash();
        service.requestPasswordReset(account.getEmail());
        String raw = token(mailbox.latest(account.getEmail()).url());
        assertThat(service.resetPassword(raw, "new-password-123", "new-password-123")).isEqualTo(AccountManagementService.TokenResult.SUCCESS);
        UserAccount changed = users.findById(account.getId()).orElseThrow();
        assertThat(encoder.matches("new-password-123", changed.getPasswordHash())).isTrue();
        assertThat(encoder.matches("old-password-123", changed.getPasswordHash())).isFalse();
        assertThat(changed.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(service.resetPassword(raw, "another-password", "another-password")).isEqualTo(AccountManagementService.TokenResult.INVALID_OR_EXPIRED);
    }

    @Test void ignoresUnknownResetEmailAndRejectsInvalidExpiredAndUsedTokens() {
        service.requestPasswordReset("missing@example.test");
        UserAccount account = account("reset-edge"); Instant now = Instant.now();
        resetTokens.save(new PasswordResetToken(account, hash("expired-reset"), now.minusSeconds(120), now.minusSeconds(1)));
        var used = resetTokens.save(new PasswordResetToken(account, hash("used-reset"), now.minusSeconds(120), now.plusSeconds(300))); used.use(now);
        assertThat(service.resetPassword("wrong", "valid-password", "valid-password")).isEqualTo(AccountManagementService.TokenResult.INVALID_OR_EXPIRED);
        assertThat(service.resetPassword("expired-reset", "valid-password", "valid-password")).isEqualTo(AccountManagementService.TokenResult.INVALID_OR_EXPIRED);
        assertThat(service.resetPassword("used-reset", "valid-password", "valid-password")).isEqualTo(AccountManagementService.TokenResult.INVALID_OR_EXPIRED);
    }

    @Test void changesDisplayNameOnAccountAndItsOwnProfileOnly() {
        UserAccount account = account("name-owner"); UserAccount other = account("name-other");
        service.changeDisplayName(account, "  새 이름  ");
        assertThat(users.findById(account.getId()).orElseThrow().getDisplayName()).isEqualTo("새 이름");
        assertThat(profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getDisplayName()).isEqualTo("새 이름");
        assertThat(users.findById(other.getId()).orElseThrow().getDisplayName()).isNotEqualTo("새 이름");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.changeDisplayName(account, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    private UserAccount account(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var request = new RegistrationRequest(); request.setLoginId(prefix + "-" + suffix);
        request.setEmail(prefix + "-" + suffix + "@example.test"); request.setDisplayName(prefix); request.setPassword("old-password-123");
        return registrations.register(request);
    }
    private static String token(String url) { return url.substring(url.indexOf("token=") + 6); }
    private static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
}
