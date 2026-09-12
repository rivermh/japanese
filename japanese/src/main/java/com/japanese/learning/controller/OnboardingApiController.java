package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.dto.OnboardingOptions;
import com.japanese.learning.dto.OnboardingRequest;
import com.japanese.learning.dto.OnboardingStatus;
import com.japanese.learning.dto.StudyPreferences;
import com.japanese.learning.service.OnboardingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingApiController {
    private final CurrentUserService currentUser;
    private final OnboardingService onboarding;
    public OnboardingApiController(CurrentUserService currentUser, OnboardingService onboarding) {
        this.currentUser = currentUser; this.onboarding = onboarding;
    }
    @GetMapping public OnboardingStatus status() { return onboarding.status(currentUser.currentAccount()); }
    @GetMapping("/options") public OnboardingOptions options() { return onboarding.options(); }
    @PostMapping("/complete") public OnboardingStatus complete(@RequestBody OnboardingRequest request) {
        var account = currentUser.currentAccount(); onboarding.complete(account, request); return onboarding.status(account);
    }
    @GetMapping("/plan") public StudyPreferences plan() { return onboarding.status(currentUser.currentAccount()).plan(); }
}
