package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.dto.WeaknessNotebook;
import com.japanese.learning.dto.WeaknessReviewSessionView;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.service.WeaknessNoteService;
import com.japanese.learning.service.WeaknessReviewSessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated endpoint for web and Android weakness-note clients. */
@RestController
@RequestMapping("/api/v1/weaknesses")
public class WeaknessApiController {
    private final CurrentUserService users;
    private final WeaknessNoteService notes;
    private final WeaknessReviewSessionService sessions;
    public WeaknessApiController(CurrentUserService users, WeaknessNoteService notes, WeaknessReviewSessionService sessions) {
        this.users=users; this.notes=notes; this.sessions=sessions;
    }
    @GetMapping public WeaknessNotebook notebook(@RequestParam(defaultValue = "20") int size) { return notes.notebook(users.currentAccount(), size); }
    @GetMapping("/session") public WeaknessReviewSessionView currentSession() { return sessions.current(users.currentAccount()); }
    @PostMapping("/session/start") public WeaknessReviewSessionView start() { return sessions.startOrResume(users.currentAccount()); }
    @PostMapping("/session/{slug}/complete") public WeaknessReviewSessionView complete(@PathVariable String slug, @RequestParam StudyResult result) {
        return sessions.complete(users.currentAccount(), slug, result);
    }
}
