package com.japanese.account.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens", indexes = {
        @Index(name = "idx_email_verification_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_email_verification_user_created", columnList = "user_account_id,created_at")
})
public class EmailVerificationToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_account_id", nullable = false) private UserAccount user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected EmailVerificationToken() {}
    public EmailVerificationToken(UserAccount user, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.user = user; this.tokenHash = tokenHash; this.createdAt = createdAt; this.expiresAt = expiresAt;
    }
    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isUsable(Instant now) { return usedAt == null && expiresAt.isAfter(now); }
    public void use(Instant now) { this.usedAt = now; }
}
