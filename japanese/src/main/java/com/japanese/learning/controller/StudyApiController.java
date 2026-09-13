package com.japanese.learning.controller;

import com.japanese.learning.dto.StudyAnswer;
import com.japanese.learning.dto.StudyOverview;
import com.japanese.learning.dto.StudyHistoryEntry;
import com.japanese.learning.dto.DailyLearningProgress;
import com.japanese.learning.dto.QuizHistoryEntry;
import com.japanese.learning.dto.StreakStatus;
import com.japanese.learning.dto.TodayLearningPlan;
import com.japanese.learning.dto.TodayStudySessionView;
import com.japanese.learning.dto.LearningScope;
import com.japanese.learning.dto.StudyPreferences;
import com.japanese.learning.dto.ContentLearningStatus;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.entity.RelearningTarget;
import com.japanese.learning.service.LearningService;
import com.japanese.account.service.CurrentUserService;
import java.util.List;
import com.japanese.content.dto.ContentSummary;
import com.japanese.content.entity.ContentType;
import com.japanese.learning.service.WeaknessNoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/study")
public class StudyApiController {

    private final LearningService learningService;
    private final CurrentUserService currentUserService;
    private final com.japanese.learning.service.StreakService streakService;
    private final com.japanese.learning.service.TodayStudySessionService todayStudySessions;
    private final WeaknessNoteService weaknessNotes;

    public StudyApiController(LearningService learningService, CurrentUserService currentUserService,
                              com.japanese.learning.service.StreakService streakService,
                              com.japanese.learning.service.TodayStudySessionService todayStudySessions,
                              WeaknessNoteService weaknessNotes) {
        this.learningService = learningService; this.currentUserService = currentUserService;
        this.streakService = streakService; this.todayStudySessions = todayStudySessions; this.weaknessNotes=weaknessNotes;
    }

    @GetMapping("/overview")
    public StudyOverview overview() {
        return learningService.overview(currentUserService.currentAccount());
    }

    @GetMapping("/daily")
    public DailyLearningProgress daily() {
        return learningService.todayProgress(currentUserService.currentAccount());
    }

    @GetMapping("/today")
    public TodayLearningPlan today() {
        return learningService.todayPlan(currentUserService.currentAccount());
    }

    /** A persisted regular-learning plan for clients that need resume semantics. */
    @GetMapping("/today/session")
    public TodayStudySessionView todaySession() {
        return todayStudySessions.startOrResume(currentUserService.currentAccount());
    }

    @PostMapping("/today/session/{slug}/complete")
    public TodayStudySessionView completeTodaySession(@PathVariable String slug, @RequestParam StudyResult result) {
        return todayStudySessions.complete(currentUserService.currentAccount(), slug, result);
    }

    @GetMapping("/scope")
    public LearningScope scope() {
        return learningService.learningScope(currentUserService.currentAccount());
    }

    @PostMapping("/scope")
    public LearningScope updateScope(
            @RequestParam(required = false) List<String> levels,
            @RequestParam(required = false) List<String> categories
    ) {
        var account = currentUserService.currentAccount();
        learningService.updateLearningScope(account, levels, categories);
        return learningService.learningScope(account);
    }

    @GetMapping("/preferences")
    public StudyPreferences preferences() {
        return learningService.studyPreferences(currentUserService.currentAccount());
    }

    @PostMapping("/preferences")
    public StudyPreferences updatePreferences(
            @RequestParam int dailyNewWordLimit,
            @RequestParam int dailyNewGrammarLimit
    ) {
        var account = currentUserService.currentAccount();
        learningService.updateNewContentLimits(account, dailyNewWordLimit, dailyNewGrammarLimit);
        return learningService.studyPreferences(account);
    }

    @GetMapping("/streak")
    public StreakStatus streak() { return streakService.status(currentUserService.currentAccount()); }

    @GetMapping("/cards")
    public List<ContentSummary> cards(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) ContentType type,
            @RequestParam(defaultValue = "false") boolean reviewOnly
    ) {
        return learningService.cards(currentUserService.currentAccount(), level, type, reviewOnly);
    }

    @GetMapping("/contents/{slug}/status")
    public ContentLearningStatus contentStatus(@PathVariable String slug) {
        return learningService.contentLearningStatus(currentUserService.currentAccount(), slug);
    }

    @GetMapping("/relearn/{target}")
    public List<ContentSummary> relearn(@PathVariable String target) {
        RelearningTarget relearnTarget=parseTarget(target);
        return relearnTarget == RelearningTarget.WEAKNESSES
                ? weaknessNotes.focusedReviewCandidates(currentUserService.currentAccount(), 20)
                : learningService.relearningCards(currentUserService.currentAccount(), relearnTarget);
    }

    @GetMapping("/history")
    public List<StudyHistoryEntry> history(
            @RequestParam(defaultValue = "10") int size
    ) {
        return learningService.recentHistory(currentUserService.currentAccount(), size);
    }
    @GetMapping("/quiz-history") public List<QuizHistoryEntry> quizHistory(@RequestParam(defaultValue = "10") int size) {
        return learningService.recentQuizHistory(currentUserService.currentAccount(), size);
    }

    @PostMapping("/cards/{slug}/answer")
    public StudyAnswer answer(
            @PathVariable String slug,
            @RequestParam StudyResult result,
            @RequestParam(required = false) String sessionKey
    ) {
        return learningService.answer(currentUserService.currentAccount(), slug, result, sessionKey, false);
    }

    @PostMapping("/relearn/{target}/{slug}/answer")
    public StudyAnswer relearnAnswer(@PathVariable String target, @PathVariable String slug,
                                     @RequestParam StudyResult result,
                                     @RequestParam(required = false) String sessionKey) {
        return learningService.answer(currentUserService.currentAccount(), slug, result, sessionKey, true);
    }

    private RelearningTarget parseTarget(String target) {
        return RelearningTarget.valueOf(target.toUpperCase(java.util.Locale.ROOT));
    }
}
