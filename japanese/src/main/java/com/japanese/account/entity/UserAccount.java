package com.japanese.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 80)
    private String loginId;

    @Column(unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    /** Null belongs to accounts created before email verification and is treated as verified. */
    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected UserAccount() {
    }

    public UserAccount(String loginId, String email, String passwordHash, String displayName, UserRole role) {
        this.loginId = loginId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.emailVerified = email == null ? Boolean.TRUE : Boolean.FALSE;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public boolean isEmailVerified() { return emailVerified == null || Boolean.TRUE.equals(emailVerified); }
    public UserRole getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
    public void verifyEmail() { this.emailVerified = Boolean.TRUE; }
    public void changePasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void changeDisplayName(String displayName) { this.displayName = displayName; }
}
