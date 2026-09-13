package com.japanese.account.controller;

import com.japanese.account.dto.*;
import com.japanese.account.service.AccountManagementService;
import com.japanese.account.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account")
public class AccountApiController {
    private static final Map<String, String> GENERIC_MAIL_RESPONSE = Map.of("message", "요청을 확인했습니다. 해당하는 계정이 있으면 안내 메일을 보냅니다.");
    private final CurrentUserService currentUser;
    private final AccountManagementService accounts;
    public AccountApiController(CurrentUserService currentUser, AccountManagementService accounts) { this.currentUser = currentUser; this.accounts = accounts; }

    @GetMapping public AccountDetails account() { return accounts.details(currentUser.currentAccount()); }
    @PostMapping("/display-name") public AccountDetails displayName(@Valid @RequestBody DisplayNameRequest request) {
        return accounts.changeDisplayName(currentUser.currentAccount(), request.displayName());
    }
    @PostMapping("/email-verification/resend") public ResponseEntity<?> resendCurrent() {
        var result = accounts.requestEmailVerification(currentUser.currentAccount());
        return result == AccountManagementService.DeliveryResult.THROTTLED
                ? ResponseEntity.status(429).body(Map.of("message", "잠시 후 다시 시도해 주세요."))
                : ResponseEntity.accepted().body(Map.of("message", "인증 메일 요청을 확인했습니다."));
    }
    @PostMapping("/email-verification/request") public ResponseEntity<?> requestVerification(@Valid @RequestBody EmailRequest request) {
        accounts.requestEmailVerification(request.email());
        return ResponseEntity.accepted().body(GENERIC_MAIL_RESPONSE);
    }
    @PostMapping("/email-verification/confirm") public ResponseEntity<?> confirmVerification(@Valid @RequestBody TokenRequest request) {
        var result = accounts.verifyEmail(request.getToken());
        return result == AccountManagementService.TokenResult.INVALID_OR_EXPIRED
                ? ResponseEntity.badRequest().body(Map.of("message", "인증 링크가 잘못되었거나 만료되었습니다."))
                : ResponseEntity.ok(Map.of("verified", true));
    }
    @PostMapping("/password-reset/request") public ResponseEntity<?> requestReset(@Valid @RequestBody EmailRequest request) {
        accounts.requestPasswordReset(request.email());
        return ResponseEntity.accepted().body(GENERIC_MAIL_RESPONSE);
    }
    @PostMapping("/password-reset/complete") public ResponseEntity<?> completeReset(@Valid @RequestBody PasswordResetRequest request) {
        try {
            var result = accounts.resetPassword(request.getToken(), request.getPassword(), request.getPasswordConfirmation());
            return result == AccountManagementService.TokenResult.SUCCESS
                    ? ResponseEntity.ok(Map.of("changed", true))
                    : ResponseEntity.badRequest().body(Map.of("message", "재설정 링크가 잘못되었거나 만료되었습니다."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }
}
