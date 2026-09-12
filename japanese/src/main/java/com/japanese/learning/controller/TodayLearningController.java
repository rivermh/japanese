package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.service.LearningService;
import com.japanese.learning.service.StreakService;
import com.japanese.learning.service.LearningHistoryService;
import com.japanese.learning.service.OnboardingService;
import com.japanese.learning.service.TodayStudySessionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.ZoneId;

@Controller
public class TodayLearningController {
    private final LearningService learningService;
    private final CurrentUserService currentUserService;
    private final StreakService streakService;
    private final LearningHistoryService historyService;
    private final com.japanese.content.service.ContentQueryService contentQueryService;
    private final OnboardingService onboardingService;
    private final TodayStudySessionService todaySessionService;

    public TodayLearningController(LearningService learningService, CurrentUserService currentUserService, StreakService streakService,
                                   LearningHistoryService historyService, com.japanese.content.service.ContentQueryService contentQueryService,
                                   OnboardingService onboardingService, TodayStudySessionService todaySessionService) {
        this.learningService = learningService; this.currentUserService = currentUserService; this.streakService = streakService;
        this.historyService = historyService;
        this.contentQueryService = contentQueryService;
        this.onboardingService = onboardingService;
        this.todaySessionService = todaySessionService;
    }

    @GetMapping("/today")
    public String today(Model model) {
        var account = currentUserService.currentAccount();
        if (onboardingService.status(account).required()) return "redirect:/onboarding";
        var todaySession = todaySessionService.startOrResume(account);
        model.addAttribute("todaySession", todaySession);
        if (todaySession.currentSlug() != null) {
            model.addAttribute("lesson", contentQueryService.findBySlug(todaySession.currentSlug()).orElseThrow());
        }
        model.addAttribute("dailyProgress", learningService.todayProgress(account));
        model.addAttribute("overview", learningService.overview(account));
        model.addAttribute("streak", streakService.status(account));
        model.addAttribute("todayReport", historyService.day(account, LocalDate.now(ZoneId.of("Asia/Seoul"))));
        return todaySession.completed() ? "today-session-complete" : "today-learning";
    }

    @PostMapping("/today/{slug}/complete")
    public String complete(@PathVariable String slug, @RequestParam StudyResult result,
                           org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes, HttpSession session) {
        var account = currentUserService.currentAccount();
        if (onboardingService.status(account).required()) return "redirect:/onboarding";
        var todaySession = todaySessionService.complete(account, slug, result);
        if (result == StudyResult.CORRECT && !todaySession.completed()) session.setAttribute("characterReaction", "happy");
        if (todaySession.completed()) redirectAttributes.addFlashAttribute("todaySessionCompleted", true);
        return "redirect:/today";
    }
}
