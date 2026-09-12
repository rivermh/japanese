package com.japanese.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "learning_streaks")
public class LearningStreak {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "learner_profile_id", nullable = false, unique = true) private LearnerProfile learnerProfile;
    @Column(name = "current_streak", nullable = false) private int currentStreak;
    @Column(name = "longest_streak", nullable = false) private int longestStreak;
    @Column(name = "last_activity_date") private LocalDate lastActivityDate;
    protected LearningStreak() { }
    public LearningStreak(LearnerProfile learnerProfile) { this.learnerProfile = learnerProfile; }
    public void recordActivity(LocalDate activityDate) {
        if (lastActivityDate == null) currentStreak = 1;
        else if (activityDate.equals(lastActivityDate)) return;
        else if (activityDate.equals(lastActivityDate.plusDays(1))) currentStreak++;
        else if (activityDate.isAfter(lastActivityDate)) currentStreak = 1;
        else return;
        lastActivityDate = activityDate;
        longestStreak = Math.max(longestStreak, currentStreak);
    }
    public int getCurrentStreak() { return currentStreak; }
    public int getLongestStreak() { return longestStreak; }
    public LocalDate getLastActivityDate() { return lastActivityDate; }
}
