package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("sample")
@ContextConfiguration(classes = {com.japanese.JapaneseApplication.class, ReminderScheduleBoundaryTest.FixedClock.class})
class ReminderScheduleBoundaryTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock reminderTestClock() {
            return Clock.fixed(Instant.parse("2026-09-13T09:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private SampleContentDataLoader sample;
    @Autowired private UserAccountRepository accounts;
    @Autowired private LearningService learning;
    @Autowired private LearningGuidanceService guidance;
    @Autowired private ReminderPreferenceService preferences;
    private UserAccount account;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        account = accounts.saveAndFlush(new UserAccount(
                "reminder-time-" + UUID.randomUUID(), null, "hash", "Reminder learner", UserRole.USER));
        learning.overview(account);
        learning.updateNewContentLimits(account, 1, 1);
    }

    @Test
    void preferredTimeControlsVisibilityUsingSeoulLocalTime() {
        preferences.update(account, true, LocalTime.of(19, 0));
        assertThat(guidance.status(account).date()).isEqualTo(java.time.LocalDate.of(2026, 9, 13));
        assertThat(guidance.status(account).reminder().type()).isEqualTo("SCHEDULED");
        assertThat(guidance.status(account).reminder().visible()).isFalse();

        preferences.update(account, true, LocalTime.of(17, 0));
        assertThat(guidance.status(account).reminder().type()).isEqualTo("NOT_STARTED");
        assertThat(guidance.status(account).reminder().visible()).isTrue();
    }
}
