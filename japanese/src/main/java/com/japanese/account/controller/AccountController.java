package com.japanese.account.controller;

import com.japanese.account.dto.RegistrationRequest;
import com.japanese.account.service.AccountService;
import com.japanese.account.service.AccountManagementService;
import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.service.LearningService;
import com.japanese.content.service.ContentQueryService;
import com.japanese.learning.service.ReminderPreferenceService;
import java.time.LocalTime;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {
    private final AccountService accountService;
    private final CurrentUserService currentUserService;
    private final LearningService learningService;
    private final ContentQueryService contentQueryService;
    private final AccountManagementService accountManagement;
    private final ReminderPreferenceService reminderPreferences;
    public AccountController(AccountService accountService, CurrentUserService currentUserService, LearningService learningService, ContentQueryService contentQueryService, AccountManagementService accountManagement, ReminderPreferenceService reminderPreferences) { this.accountService = accountService; this.currentUserService = currentUserService; this.learningService = learningService; this.contentQueryService = contentQueryService; this.accountManagement = accountManagement; this.reminderPreferences = reminderPreferences; }
    @GetMapping("/signup") public String signup(Model model) {
        if (!model.containsAttribute("registrationRequest")) model.addAttribute("registrationRequest", new RegistrationRequest());
        return "signup";
    }
    @PostMapping("/signup") public String register(@Valid @ModelAttribute RegistrationRequest registrationRequest,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) { redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.registrationRequest", bindingResult); redirectAttributes.addFlashAttribute("registrationRequest", registrationRequest); return "redirect:/signup"; }
        try {
            var account = accountService.register(registrationRequest);
            accountManagement.requestEmailVerification(account);
        }
        catch (IllegalArgumentException exception) { redirectAttributes.addFlashAttribute("registrationError", exception.getMessage()); redirectAttributes.addFlashAttribute("registrationRequest", registrationRequest); return "redirect:/signup"; }
        redirectAttributes.addFlashAttribute("accountMessage", "가입이 완료되었습니다. 받은 메일에서 이메일 인증을 완료해 주세요.");
        redirectAttributes.addFlashAttribute("verificationEmail", registrationRequest.getEmail());
        return "redirect:/email-verification";
    }
    @GetMapping("/login") public String login() { return "login"; }
    @GetMapping("/email-verification") public String emailVerification() { return "email-verification"; }
    @PostMapping("/email-verification/resend") public String resendVerification(@RequestParam("email") String email, RedirectAttributes attributes) {
        accountManagement.requestEmailVerification(email);
        attributes.addFlashAttribute("accountMessage", "인증 가능한 계정이면 안내 메일을 보냈습니다. 잠시 후 다시 확인해 주세요.");
        attributes.addFlashAttribute("verificationEmail", email);
        return "redirect:/email-verification";
    }
    @GetMapping("/verify-email") public String verifyEmail(@RequestParam(value = "token", required = false) String token, Model model) {
        model.addAttribute("verificationResult", accountManagement.verifyEmail(token));
        return "email-verification-result";
    }
    @GetMapping("/forgot-password") public String forgotPassword() { return "forgot-password"; }
    @PostMapping("/forgot-password") public String requestPasswordReset(@RequestParam("email") String email, RedirectAttributes attributes) {
        accountManagement.requestPasswordReset(email);
        attributes.addFlashAttribute("accountMessage", "입력한 이메일로 가입된 계정이 있으면 재설정 안내를 보냈습니다.");
        return "redirect:/forgot-password";
    }
    @GetMapping("/reset-password") public String resetPassword(@RequestParam(value = "token", required = false) String token, Model model) {
        model.addAttribute("token", token == null ? "" : token);
        model.addAttribute("tokenUsable", accountManagement.passwordResetTokenUsable(token));
        return "reset-password";
    }
    @PostMapping("/reset-password") public String completePasswordReset(@RequestParam("token") String token,
            @RequestParam("password") String password, @RequestParam("passwordConfirmation") String confirmation,
            RedirectAttributes attributes) {
        try {
            var result = accountManagement.resetPassword(token, password, confirmation);
            if (result != AccountManagementService.TokenResult.SUCCESS) {
                attributes.addFlashAttribute("accountError", "재설정 링크가 잘못되었거나 만료되었습니다.");
                return "redirect:/reset-password?token=" + token;
            }
        } catch (IllegalArgumentException exception) {
            attributes.addFlashAttribute("accountError", exception.getMessage());
            return "redirect:/reset-password?token=" + token;
        }
        attributes.addFlashAttribute("signupMessage", "비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요.");
        return "redirect:/login";
    }
    @GetMapping("/settings") public String settings(Model model) {
        var account = currentUserService.currentAccount();
        model.addAttribute("account", account); model.addAttribute("dailyProgress", learningService.todayProgress(account));
        model.addAttribute("filterOptions", contentQueryService.filterOptions());
        model.addAttribute("learningScope", learningService.learningScope(account));
        model.addAttribute("studyPreferences", learningService.studyPreferences(account));
        model.addAttribute("reminderPreference", reminderPreferences.preference(account));
        return "settings";
    }
    @PostMapping("/settings/reminder") public String reminder(
            @RequestParam(value = "reminderEnabled", required = false, defaultValue = "false") boolean enabled,
            @RequestParam("reminderTime") LocalTime reminderTime, RedirectAttributes attributes) {
        try { reminderPreferences.update(currentUserService.currentAccount(), enabled, reminderTime); attributes.addFlashAttribute("settingsMessage", "학습 리마인더 설정을 저장했습니다."); }
        catch (IllegalArgumentException exception) { attributes.addFlashAttribute("settingsError", exception.getMessage()); }
        return "redirect:/settings";
    }
    @PostMapping("/settings/display-name") public String displayName(@RequestParam("displayName") String displayName, RedirectAttributes attributes) {
        try { accountManagement.changeDisplayName(currentUserService.currentAccount(), displayName); attributes.addFlashAttribute("settingsMessage", "표시 이름을 변경했습니다."); }
        catch (IllegalArgumentException exception) { attributes.addFlashAttribute("settingsError", exception.getMessage()); }
        return "redirect:/settings";
    }
    @PostMapping("/settings/email-verification/resend") public String resendCurrentVerification(RedirectAttributes attributes) {
        var result = accountManagement.requestEmailVerification(currentUserService.currentAccount());
        String message = switch (result) {
            case SENT -> "인증 메일을 보냈습니다.";
            case THROTTLED -> "인증 메일을 방금 보냈습니다. 잠시 후 다시 시도해 주세요.";
            case NOT_AVAILABLE -> "이미 인증되었거나 등록된 이메일이 없습니다.";
        };
        attributes.addFlashAttribute("settingsMessage", message);
        return "redirect:/settings";
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
    @PostMapping("/character/growth/acknowledge") public String acknowledgeGrowth(@org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "/") String returnTo, @org.springframework.web.bind.annotation.RequestParam(value="stageKey", required=false) String stageKey) {
        if (stageKey == null) learningService.acknowledgeGrowth(currentUserService.currentAccount());
        else learningService.acknowledgeGrowth(currentUserService.currentAccount(), stageKey);
        return "redirect:" + (returnTo.startsWith("/") && !returnTo.startsWith("//") ? returnTo : "/");
    }
}
