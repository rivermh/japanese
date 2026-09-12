package com.japanese.account.controller;

import com.japanese.account.dto.RegistrationRequest;
import com.japanese.account.service.AccountService;
import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.service.LearningService;
import com.japanese.learning.character.CharacterCatalog;
import com.japanese.content.service.ContentQueryService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {
    private final AccountService accountService;
    private final CurrentUserService currentUserService;
    private final LearningService learningService;
    private final CharacterCatalog characterCatalog;
    private final ContentQueryService contentQueryService;
    public AccountController(AccountService accountService, CurrentUserService currentUserService, LearningService learningService, CharacterCatalog characterCatalog, ContentQueryService contentQueryService) { this.accountService = accountService; this.currentUserService = currentUserService; this.learningService = learningService; this.characterCatalog = characterCatalog; this.contentQueryService = contentQueryService; }
    @GetMapping("/signup") public String signup(Model model) {
        if (!model.containsAttribute("registrationRequest")) model.addAttribute("registrationRequest", new RegistrationRequest());
        return "signup";
    }
    @PostMapping("/signup") public String register(@Valid @ModelAttribute RegistrationRequest registrationRequest,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) { redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.registrationRequest", bindingResult); redirectAttributes.addFlashAttribute("registrationRequest", registrationRequest); return "redirect:/signup"; }
        try { accountService.register(registrationRequest); }
        catch (IllegalArgumentException exception) { redirectAttributes.addFlashAttribute("registrationError", exception.getMessage()); redirectAttributes.addFlashAttribute("registrationRequest", registrationRequest); return "redirect:/signup"; }
        redirectAttributes.addFlashAttribute("signupMessage", "가입이 완료되었습니다. 로그인해 주세요.");
        return "redirect:/login";
    }
    @GetMapping("/login") public String login() { return "login"; }
    @GetMapping("/settings") public String settings(Model model) {
        var account = currentUserService.currentAccount();
        model.addAttribute("account", account); model.addAttribute("dailyProgress", learningService.todayProgress(account));
        model.addAttribute("learner", learningService.overview(account)); model.addAttribute("characterOptions", characterCatalog.all());
        model.addAttribute("filterOptions", contentQueryService.filterOptions());
        model.addAttribute("learningScope", learningService.learningScope(account));
        model.addAttribute("studyPreferences", learningService.studyPreferences(account));
        return "settings";
    }
    @PostMapping("/settings/daily-goal") public String dailyGoal(@org.springframework.web.bind.annotation.RequestParam int dailyGoal, RedirectAttributes attributes) {
        try { learningService.updateDailyGoal(currentUserService.currentAccount(), dailyGoal); attributes.addFlashAttribute("settingsMessage", "일일 학습 목표를 저장했습니다."); }
        catch (IllegalArgumentException exception) { attributes.addFlashAttribute("settingsError", exception.getMessage()); }
        return "redirect:/settings";
    }
    @PostMapping("/settings/character") public String character(@org.springframework.web.bind.annotation.RequestParam String characterKey, RedirectAttributes attributes) {
        try { learningService.updateCharacter(currentUserService.currentAccount(), characterKey); attributes.addFlashAttribute("settingsMessage", "캐릭터를 변경했습니다."); }
        catch (IllegalArgumentException exception) { attributes.addFlashAttribute("settingsError", exception.getMessage()); }
        return "redirect:/settings";
    }
    @PostMapping("/settings/learning-scope") public String learningScope(
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.util.List<String> levels,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.util.List<String> categories,
            RedirectAttributes attributes) {
        try {
            learningService.updateLearningScope(currentUserService.currentAccount(), levels, categories);
            attributes.addFlashAttribute("settingsMessage", "오늘의 학습 범위를 저장했습니다.");
        } catch (IllegalArgumentException exception) {
            attributes.addFlashAttribute("settingsError", exception.getMessage());
        }
        return "redirect:/settings";
    }
    @PostMapping("/settings/new-content-limits") public String newContentLimits(
            @org.springframework.web.bind.annotation.RequestParam int dailyNewWordLimit,
            @org.springframework.web.bind.annotation.RequestParam int dailyNewGrammarLimit,
            RedirectAttributes attributes) {
        try {
            learningService.updateNewContentLimits(currentUserService.currentAccount(), dailyNewWordLimit, dailyNewGrammarLimit);
            attributes.addFlashAttribute("settingsMessage", "새 단어·문법 학습량을 저장했습니다.");
        } catch (IllegalArgumentException exception) {
            attributes.addFlashAttribute("settingsError", exception.getMessage());
        }
        return "redirect:/settings";
    }
    @PostMapping("/character/growth/acknowledge") public String acknowledgeGrowth(@org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "/") String returnTo) {
        learningService.acknowledgeGrowth(currentUserService.currentAccount());
        return "redirect:" + (returnTo.startsWith("/") && !returnTo.startsWith("//") ? returnTo : "/");
    }
}
