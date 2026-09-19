package com.japanese.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.japanese.account.dto.RegistrationRequest;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.account.service.AccountService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sample")
@Transactional
class AccountDeletionWebTest {
    @Autowired private MockMvc mvc;
    @Autowired private AccountService registrations;
    @Autowired private UserAccountRepository accounts;

    @Test
    void requiresAuthenticationAndNeverDeletesOnGetOrMissingCsrf() throws Exception {
        var account = account();
        mvc.perform(get("/settings/delete-account")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/settings/delete-account").with(user(account.getLoginId())))
                .andExpect(status().isOk()).andExpect(view().name("delete-account"));
        assertThat(accounts.findByLoginId(account.getLoginId())).isPresent();
        mvc.perform(post("/settings/delete-account").with(user(account.getLoginId()))
                .param("currentPassword", "delete-password-123")).andExpect(status().isForbidden());
        assertThat(accounts.findByLoginId(account.getLoginId())).isPresent();
    }

    @Test
    void invalidPasswordDoesNotDeleteAndSuccessfulPostInvalidatesSession() throws Exception {
        var account = account();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/settings/delete-account").with(user(account.getLoginId())).with(csrf())
                .session(session).param("currentPassword", "wrong-password"))
                .andExpect(status().isOk()).andExpect(view().name("delete-account"));
        assertThat(accounts.findByLoginId(account.getLoginId())).isPresent();

        mvc.perform(post("/settings/delete-account").with(user(account.getLoginId())).with(csrf())
                .session(session).param("currentPassword", "delete-password-123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?accountDeleted=true"));
        assertThat(session.isInvalid()).isTrue();
        assertThat(accounts.findByLoginId(account.getLoginId())).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registrations.loadUserByUsername(account.getLoginId()))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private com.japanese.account.entity.UserAccount account() {
        String id = "delete-web-" + UUID.randomUUID().toString().substring(0, 8);
        RegistrationRequest request = new RegistrationRequest();
        request.setLoginId(id);
        request.setEmail(id + "@example.test");
        request.setDisplayName(id);
        request.setPassword("delete-password-123");
        return registrations.register(request);
    }
}
