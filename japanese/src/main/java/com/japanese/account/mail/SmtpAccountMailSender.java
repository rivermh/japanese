package com.japanese.account.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "japanese.account.mail.mode", havingValue = "smtp")
public class SmtpAccountMailSender implements AccountMailSender {
    private final JavaMailSender mailSender;
    private final String from;
    public SmtpAccountMailSender(JavaMailSender mailSender,
            @Value("${japanese.account.mail.from:no-reply@localhost}") String from) {
        this.mailSender = mailSender; this.from = from;
    }
    @Override public void sendEmailVerification(String email, String displayName, String verificationUrl) {
        send(email, "Japanese 이메일 인증", displayName + "님, 아래 주소에서 이메일 인증을 완료해 주세요.\n\n" + verificationUrl);
    }
    @Override public void sendPasswordReset(String email, String displayName, String resetUrl) {
        send(email, "Japanese 비밀번호 재설정", displayName + "님, 아래 주소에서 비밀번호를 재설정해 주세요.\n\n" + resetUrl + "\n\n요청하지 않았다면 이 메일을 무시하세요.");
    }
    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(to); message.setSubject(subject); message.setText(body);
        mailSender.send(message);
    }
}
