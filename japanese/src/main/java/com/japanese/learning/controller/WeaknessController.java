package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.content.service.ContentQueryService;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.service.LearningService;
import com.japanese.learning.service.StreakService;
import com.japanese.learning.service.WeaknessNoteService;
import com.japanese.learning.service.WeaknessReviewSessionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WeaknessController {
    private final CurrentUserService users;
    private final WeaknessNoteService notes;
    private final WeaknessReviewSessionService sessions;
    private final ContentQueryService contents;
    private final LearningService learning;
    private final StreakService streaks;

    public WeaknessController(CurrentUserService users, WeaknessNoteService notes, WeaknessReviewSessionService sessions,
                              ContentQueryService contents, LearningService learning, StreakService streaks) {
        this.users=users; this.notes=notes; this.sessions=sessions; this.contents=contents; this.learning=learning; this.streaks=streaks;
    }

    @GetMapping("/weaknesses")
    public String notebook(Model model) {
        var account=users.currentAccount();
        model.addAttribute("notebook", notes.notebook(account));
        model.addAttribute("activeSession", sessions.current(account));
        return "weaknesses";
    }

    @PostMapping("/weaknesses/session/start")
    public String start() {
        sessions.startOrResume(users.currentAccount());
        return "redirect:/weaknesses/session";
    }

    @GetMapping("/weaknesses/session")
    public String session(Model model) {
        var account=users.currentAccount();
        var session=sessions.current(account);
        if (session==null) return "redirect:/weaknesses";
        model.addAttribute("weaknessSession", session);
        model.addAttribute("overview", learning.overview(account));
        model.addAttribute("streak", streaks.status(account));
        if (session.currentSlug()!=null) model.addAttribute("lesson", contents.findBySlug(session.currentSlug()).orElseThrow());
        return session.completed() ? "weakness-session-complete" : "weakness-session";
    }

    @PostMapping("/weaknesses/session/{slug}/complete")
    public String complete(@PathVariable String slug, @RequestParam StudyResult result) {
        sessions.complete(users.currentAccount(), slug, result);
        return "redirect:/weaknesses/session";
    }
}
