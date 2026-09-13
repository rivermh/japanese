package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.dto.LearningHomeStatus;
import com.japanese.learning.dto.ReminderPreference;
import com.japanese.learning.dto.ReminderPreferenceRequest;
import com.japanese.learning.service.LearningGuidanceService;
import com.japanese.learning.service.ReminderPreferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class LearningStatusApiController {
    private final CurrentUserService currentUser;
    private final LearningGuidanceService guidance;
    private final ReminderPreferenceService reminders;
    public LearningStatusApiController(CurrentUserService currentUser, LearningGuidanceService guidance,
            ReminderPreferenceService reminders) {
        this.currentUser=currentUser; this.guidance=guidance; this.reminders=reminders;
    }
    @GetMapping("/home/learning-status")
    public LearningHomeStatus status() { return guidance.status(currentUser.currentAccount()); }
    @PostMapping("/reminders/preferences")
    public ReminderPreference updatePreference(@Valid @RequestBody ReminderPreferenceRequest request) {
        return reminders.update(currentUser.currentAccount(), request.enabled(), request.preferredTime());
    }
}
