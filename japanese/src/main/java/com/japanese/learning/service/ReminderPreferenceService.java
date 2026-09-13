package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.learning.dto.ReminderPreference;
import com.japanese.learning.entity.LearnerStudyPreference;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearnerStudyPreferenceRepository;
import java.time.LocalTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderPreferenceService {
    private final LearnerProfileRepository profiles;
    private final LearnerStudyPreferenceRepository preferences;
    private final LearningTime time;
    public ReminderPreferenceService(LearnerProfileRepository profiles, LearnerStudyPreferenceRepository preferences, LearningTime time) {
        this.profiles = profiles; this.preferences = preferences; this.time = time;
    }
    @Transactional(readOnly = true)
    public ReminderPreference preference(UserAccount account) {
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        return preferences.findByLearnerProfileId(profile.getId())
                .map(value -> dto(value.isReminderEnabled(), value.getReminderTime()))
                .orElseGet(() -> dto(true, LocalTime.of(19, 0)));
    }
    @Transactional
    public ReminderPreference update(UserAccount account, boolean enabled, LocalTime preferredTime) {
        if (preferredTime == null) throw new IllegalArgumentException("리마인더 시간을 입력하세요.");
        var profile = profiles.findByUserAccountLoginIdForUpdate(account.getLoginId()).orElseThrow();
        var preference = preferences.findByLearnerProfileId(profile.getId())
                .orElseGet(() -> preferences.save(new LearnerStudyPreference(profile)));
        preference.updateReminder(enabled, preferredTime.withSecond(0).withNano(0));
        return dto(preference.isReminderEnabled(), preference.getReminderTime());
    }
    private ReminderPreference dto(boolean enabled, LocalTime preferredTime) {
        return new ReminderPreference(enabled, preferredTime, time.zone().getId());
    }
}
