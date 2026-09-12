package com.japanese.learning.controller;

import com.japanese.content.entity.ContentType;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.service.LearningService;
import com.japanese.account.service.CurrentUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import com.japanese.learning.entity.RelearningTarget;

@Controller
public class StudyController {

    private final LearningService learningService;
    private final CurrentUserService currentUserService;

    public StudyController(LearningService learningService, CurrentUserService currentUserService) {
        this.learningService = learningService; this.currentUserService = currentUserService;
    }

    @GetMapping("/study")
    public String study(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) ContentType type,
            @RequestParam(defaultValue = "false") boolean reviewOnly,
            Model model,
            HttpSession session
    ) {
        var account = currentUserService.currentAccount();
        model.addAttribute("overview", learningService.overview(account));
        model.addAttribute("dailyProgress", learningService.todayProgress(account));
        model.addAttribute("cards", learningService.cards(account, level, type, reviewOnly));
        model.addAttribute("level", level == null ? "" : level);
        model.addAttribute("type", type == null ? "" : type.name());
        model.addAttribute("reviewOnly", reviewOnly);
        model.addAttribute("relearnTarget", null);
        model.addAttribute("studySessionKey", sessionKey(session, "freeStudy"));
        return "study";
    }

    @GetMapping("/study/relearn/{target}")
    public String relearn(@PathVariable String target, Model model, HttpSession session) {
        RelearningTarget relearnTarget = parseTarget(target);
        var account = currentUserService.currentAccount();
        model.addAttribute("overview", learningService.overview(account));
        model.addAttribute("dailyProgress", learningService.todayProgress(account));
        model.addAttribute("cards", learningService.relearningCards(account, relearnTarget));
        model.addAttribute("level", "");
        model.addAttribute("type", "");
        model.addAttribute("reviewOnly", false);
        model.addAttribute("relearnTarget", relearnTarget);
        model.addAttribute("studySessionKey", sessionKey(session, "relearn-" + relearnTarget.name().toLowerCase(Locale.ROOT)));
        return "study";
    }

    @PostMapping("/study/{slug}/answer")
    public String answer(
            @PathVariable String slug,
            @RequestParam StudyResult result,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean reviewOnly,
            @RequestParam(required = false) String sessionKey,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        var answer = learningService.answer(currentUserService.currentAccount(), slug, result, sessionKey, false);
        if (answer.earnedExperience() > 0) session.setAttribute("characterReaction", "happy");
        if (level != null && !level.isBlank()) {
            redirectAttributes.addAttribute("level", level);
        }
        if (type != null && !type.isBlank()) {
            redirectAttributes.addAttribute("type", type);
        }
        if (reviewOnly) {
            redirectAttributes.addAttribute("reviewOnly", true);
        }
        return "redirect:/study";
    }

    @PostMapping("/study/relearn/{target}/{slug}/answer")
    public String relearnAnswer(@PathVariable String target, @PathVariable String slug,
                                @RequestParam StudyResult result,
                                @RequestParam(required = false) String sessionKey,
                                HttpSession session) {
        RelearningTarget relearnTarget = parseTarget(target);
        var answer = learningService.answer(currentUserService.currentAccount(), slug, result, sessionKey, true);
        if (answer.earnedExperience() > 0) session.setAttribute("characterReaction", "happy");
        return "redirect:/study/relearn/" + relearnTarget.name().toLowerCase(Locale.ROOT);
    }

    private RelearningTarget parseTarget(String target) {
        try {
            return RelearningTarget.valueOf(target.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("알 수 없는 다시 학습 대상입니다.");
        }
    }

    private String sessionKey(HttpSession session, String prefix) {
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        String dateAttribute = prefix + "Date";
        String keyAttribute = prefix + "Key";
        if (!today.equals(session.getAttribute(dateAttribute))) {
            session.setAttribute(dateAttribute, today);
            session.setAttribute(keyAttribute, prefix + "-" + today + "-" + UUID.randomUUID());
        }
        return (String) session.getAttribute(keyAttribute);
    }
}
