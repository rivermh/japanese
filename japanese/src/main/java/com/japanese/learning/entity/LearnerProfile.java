package com.japanese.learning.entity;

import com.japanese.account.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.Instant;

@Entity
@Table(name = "learner_profiles")
public class LearnerProfile {

    private static final int EXP_PER_LEVEL = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "learner_key", nullable = false, unique = true, length = 120)
    private String learnerKey;

    @OneToOne(optional = true)
    @JoinColumn(name = "user_account_id", unique = true)
    private UserAccount userAccount;

    @Column(name = "daily_goal")
    private Integer dailyGoal;

    @Column(name = "last_studied_at")
    private Instant lastStudiedAt;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(name = "character_key", nullable = false, length = 80)
    private String characterKey;

    @Column(name = "pending_growth_stage_key", length = 40)
    private String pendingGrowthStageKey;

    @Column(nullable = false)
    private int experience;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Null is intentionally treated as completed for profiles created before onboarding. */
    @Column(name = "onboarding_completed")
    private Boolean onboardingCompleted;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    protected LearnerProfile() {
    }

    public LearnerProfile(String learnerKey, String displayName, String characterKey) {
        this.learnerKey = learnerKey;
        this.displayName = displayName;
        this.characterKey = characterKey;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public LearnerProfile(UserAccount userAccount, int dailyGoal, String characterKey) {
        this(userAccount.getLoginId(), userAccount.getDisplayName(), characterKey);
        this.userAccount = userAccount;
        this.dailyGoal = dailyGoal;
    }

    public void addExperience(int amount) {
        if (amount > 0) {
            experience += amount;
            updatedAt = Instant.now();
            lastStudiedAt = updatedAt;
        }
    }

    public Long getId() {
        return id;
    }

    public String getLearnerKey() {
        return learnerKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCharacterKey() {
        return characterKey;
    }

    public int getExperience() {
        return experience;
    }

    public int getLevel() {
        return experience / EXP_PER_LEVEL + 1;
    }

    public int getLevelExperience() {
        return experience % EXP_PER_LEVEL;
    }
    public Integer getDailyGoal() { return dailyGoal; }
    public UserAccount getUserAccount() { return userAccount; }
    public Instant getLastStudiedAt() { return lastStudiedAt; }
    public void updateDailyGoal(int dailyGoal) {
        this.dailyGoal = dailyGoal;
        this.updatedAt = Instant.now();
    }

    public void updateCharacterKey(String characterKey) { this.characterKey = characterKey; this.updatedAt = Instant.now(); }
    public void markGrowthPending(String stageKey) { this.pendingGrowthStageKey = stageKey; this.updatedAt = Instant.now(); }
    public void acknowledgeGrowth() { this.pendingGrowthStageKey = null; this.updatedAt = Instant.now(); }
    public String getPendingGrowthStageKey() { return pendingGrowthStageKey; }
    public boolean requiresOnboarding() { return Boolean.FALSE.equals(onboardingCompleted); }
    public Instant getOnboardingCompletedAt() { return onboardingCompletedAt; }
    public void requireOnboarding() { this.onboardingCompleted = false; this.onboardingCompletedAt = null; this.updatedAt = Instant.now(); }
    public void completeOnboarding() { this.onboardingCompleted = true; this.onboardingCompletedAt = Instant.now(); this.updatedAt = onboardingCompletedAt; }
}
