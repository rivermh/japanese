package com.japanese.account.controller;

import com.japanese.account.mail.AccountMailSender;
import com.japanese.account.mail.DevelopmentAccountMailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "japanese.account.dev-mailbox-enabled", havingValue = "true")
public class DevelopmentMailboxController {
    private final DevelopmentAccountMailSender mailbox;
    public DevelopmentMailboxController(AccountMailSender mailSender) {
        if (!(mailSender instanceof DevelopmentAccountMailSender development)) throw new IllegalStateException("Development mailbox requires development mail mode");
        this.mailbox = development;
    }
    @GetMapping("/dev/account-mail")
    ResponseEntity<?> latest(@RequestParam("email") String email) {
        var message = mailbox.latest(email);
        return message == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(message);
    }
}
