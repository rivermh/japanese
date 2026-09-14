package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.service.LearningService;
import com.japanese.learning.service.OnboardingService;
import com.japanese.learning.service.StreakService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HaruController {
    private final CurrentUserService currentUserService;
    private final LearningService learningService;
    private final OnboardingService onboardingService;
    private final StreakService streakService;

    public HaruController(CurrentUserService currentUserService, LearningService learningService,
                          OnboardingService onboardingService, StreakService streakService) {
        this.currentUserService = currentUserService;
        this.learningService = learningService;
        this.onboardingService = onboardingService;
        this.streakService = streakService;
    }

    @GetMapping("/haru")
    public String haru(Model model) {
        var account = currentUserService.currentAccount();
        if (onboardingService.status(account).required()) return "redirect:/onboarding";
        model.addAttribute("learner", learningService.overview(account));
        model.addAttribute("streak", streakService.status(account));
        return "haru";
    }
}
