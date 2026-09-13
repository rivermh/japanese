package com.japanese.account.mail;

public interface AccountMailSender {
    void sendEmailVerification(String email, String displayName, String verificationUrl);
    void sendPasswordReset(String email, String displayName, String resetUrl);
}
