package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.dto.OnboardingRequest;
import com.japanese.learning.service.OnboardingService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OnboardingController {
    private final CurrentUserService currentUser;
    private final OnboardingService onboarding;

    public OnboardingController(CurrentUserService currentUser, OnboardingService onboarding) {
        this.currentUser = currentUser;
        this.onboarding = onboarding;
    }

    @GetMapping("/onboarding")
    public String page(Model model) {
        var status = onboarding.status(currentUser.currentAccount());
        if (!status.required()) return "redirect:/today";
        model.addAttribute("options", status.options());
        return "onboarding";
    }

    @PostMapping("/onboarding")
    public String complete(@RequestParam(required = false) String currentLevel,
                           @RequestParam(required = false) String targetLevel,
                           @RequestParam(required = false) List<String> categories,
                           @RequestParam(required = false) String preset,
                           @RequestParam(required = false) Integer dailyNewWordLimit,
                           @RequestParam(required = false) Integer dailyNewGrammarLimit,
                           @RequestParam(required = false) Integer dailyGoal,
                           RedirectAttributes attributes) {
        try {
            String effectivePreset = dailyNewWordLimit != null && dailyNewGrammarLimit != null ? null : preset;
            onboarding.complete(currentUser.currentAccount(), new OnboardingRequest(currentLevel, targetLevel, categories,
                    effectivePreset, dailyNewWordLimit, dailyNewGrammarLimit, dailyGoal));
            return "redirect:/onboarding/complete";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            attributes.addFlashAttribute("onboardingError", exception.getMessage());
            return "redirect:/onboarding";
        }
    }

    @GetMapping("/onboarding/complete")
    public String completed(Model model) {
        var status = onboarding.status(currentUser.currentAccount());
        if (status.required()) return "redirect:/onboarding";
        model.addAttribute("plan", status.plan());
        return "onboarding-complete";
    }
}
