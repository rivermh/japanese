package com.japanese.account.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.japanese.account.dto.RegistrationRequest;
import com.japanese.account.entity.UserAccount;
import com.japanese.account.mail.DevelopmentAccountMailSender;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.account.service.AccountService;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.dto.OnboardingRequest;
import com.japanese.learning.service.OnboardingService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sample")
@Transactional
class AccountOperationsWebTest {
    @Autowired MockMvc mvc;
    @Autowired AccountService registrations;
    @Autowired UserAccountRepository users;
    @Autowired LearnerProfileRepository profiles;
    @Autowired DevelopmentAccountMailSender mailbox;
    @Autowired PasswordEncoder encoder;
    @Autowired OnboardingService onboarding;

    @Test void signupSendsVerificationAndPublicConfirmationWorks() throws Exception {
        String id = "signup-" + shortId(); String email = id + "@example.test";
        mvc.perform(post("/signup").with(csrf()).param("loginId", id).param("email", email)
                        .param("displayName", "가입 사용자").param("password", "password-123"))
                .andExpect(redirectedUrl("/email-verification"));
        String raw = token(mailbox.latest(email).url());
        mvc.perform(get("/verify-email").param("token", raw)).andExpect(status().isOk()).andExpect(content().string(containsString("이메일 인증을 완료했습니다")));
        assert users.findByLoginId(id).orElseThrow().isEmailVerified();
    }

    @Test void resetRequestHasSameExternalResponseAndApiNeverExposesSecrets() throws Exception {
        UserAccount account = account("api-reset");
        String request = "{\"email\":\"" + account.getEmail() + "\"}";
        String missing = "{\"email\":\"missing-" + shortId() + "@example.test\"}";
        String existingBody = mvc.perform(post("/api/v1/account/password-reset/request").with(csrf()).contentType("application/json").content(request))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String missingBody = mvc.perform(post("/api/v1/account/password-reset/request").with(csrf()).contentType("application/json").content(missing))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(existingBody).isEqualTo(missingBody).doesNotContain("token", "password", account.getPasswordHash());
        mvc.perform(get("/api/v1/account").with(user(account.getLoginId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value(account.getDisplayName()))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void changesOwnDisplayNameInApiSettingsAndHomeAndBlocksAnonymousUser() throws Exception {
        UserAccount account = account("display-api");
        mvc.perform(post("/api/v1/account/display-name").with(user(account.getLoginId())).with(csrf())
                        .contentType("application/json").content("{\"displayName\":\"새 표시 이름\",\"userId\":999999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("새 표시 이름"));
        mvc.perform(get("/settings").with(user(account.getLoginId()))).andExpect(content().string(containsString("새 표시 이름")));
        mvc.perform(get("/").with(user(account.getLoginId()))).andExpect(content().string(containsString("새 표시 이름")));
        mvc.perform(post("/api/v1/account/display-name").with(csrf()).contentType("application/json").content("{\"displayName\":\"침입\"}"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/v1/account/display-name").with(user(account.getLoginId())).with(csrf())
                        .contentType("application/json").content("{\"displayName\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void newPasswordAuthenticatesAndOldPasswordDoesNot() throws Exception {
        UserAccount account = account("login-reset");
        mvc.perform(post("/forgot-password").with(csrf()).param("email", account.getEmail())).andExpect(redirectedUrl("/forgot-password"));
        String raw = token(mailbox.latest(account.getEmail()).url());
        mvc.perform(post("/reset-password").with(csrf()).param("token", raw).param("password", "new-password-456").param("passwordConfirmation", "new-password-456"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/login").with(csrf()).param("username", account.getLoginId()).param("password", "old-password-123"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).param("username", account.getLoginId()).param("password", "new-password-456"))
                .andExpect(redirectedUrl("/"));
        org.assertj.core.api.Assertions.assertThat(encoder.matches("new-password-456", users.findById(account.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void invalidResetApiDoesNotEchoPasswordOrToken() throws Exception {
        String body = mvc.perform(post("/api/v1/account/password-reset/complete").with(csrf()).contentType("application/json")
                        .content("{\"token\":\"secret-token-value\",\"password\":\"secret-password-value\",\"passwordConfirmation\":\"different-password\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("secret-token-value", "secret-password-value");
    }

    @Test void accountChangesRequireAuthenticationAndExistingCsrfPolicy() throws Exception {
        mvc.perform(get("/api/v1/account")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/v1/account/email-verification/resend").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/v1/account/password-reset/request").contentType("application/json")
                .content("{\"email\":\"nobody@example.test\"}")).andExpect(status().isForbidden());
    }

    private UserAccount account(String prefix) {
        String id = prefix + "-" + shortId(); var request = new RegistrationRequest(); request.setLoginId(id);
        request.setEmail(id + "@example.test"); request.setDisplayName(prefix); request.setPassword("old-password-123");
        UserAccount account = registrations.register(request);
        onboarding.complete(account, new OnboardingRequest("UNKNOWN", "N5", java.util.List.of(), "normal", null, null, null));
        return account;
    }
    private static String shortId() { return UUID.randomUUID().toString().substring(0, 8); }
    private static String token(String url) { return url.substring(url.indexOf("token=") + 6); }
}
