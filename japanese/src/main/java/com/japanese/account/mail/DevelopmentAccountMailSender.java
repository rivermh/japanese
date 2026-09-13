package com.japanese.account.mail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "japanese.account.mail.mode", havingValue = "development", matchIfMissing = true)
public class DevelopmentAccountMailSender implements AccountMailSender {
    public record Message(String type, String recipient, String url) {}
    private final Map<String, Message> messages = new ConcurrentHashMap<>();

    @Override public void sendEmailVerification(String email, String displayName, String verificationUrl) {
        messages.put(email, new Message("EMAIL_VERIFICATION", email, verificationUrl));
    }
    @Override public void sendPasswordReset(String email, String displayName, String resetUrl) {
        messages.put(email, new Message("PASSWORD_RESET", email, resetUrl));
    }
    public Message latest(String email) { return messages.get(email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT)); }
}
