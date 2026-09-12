package com.japanese.content.controller;

import com.japanese.content.service.ContentQueryService;
import com.japanese.content.entity.ContentType;
import com.japanese.learning.service.LearningService;
import com.japanese.content.service.ContentReviewService;
import com.japanese.content.service.BookmarkService;
import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.service.StreakService;
import com.japanese.learning.service.StudyQueueService;
import com.japanese.learning.service.StudyCollectionService;
import com.japanese.learning.service.OnboardingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import jakarta.servlet.http.HttpSession;

@Controller
public class ContentController {

    private final ContentQueryService contentQueryService;
    private final LearningService learningService;
    private final ContentReviewService contentReviewService;
    private final BookmarkService bookmarkService;
    private final CurrentUserService currentUserService;
    private final StreakService streakService;
    private final StudyQueueService studyQueueService;
    private final StudyCollectionService studyCollectionService;
    private final OnboardingService onboardingService;
    private final String activeProfiles;

    public ContentController(
            ContentQueryService contentQueryService,
            LearningService learningService,
            ContentReviewService contentReviewService,
            BookmarkService bookmarkService,
            CurrentUserService currentUserService,
            StreakService streakService,
            StudyQueueService studyQueueService,
            StudyCollectionService studyCollectionService,
            OnboardingService onboardingService,
            @Value("${spring.profiles.active:}") String activeProfiles
    ) {
        this.contentQueryService = contentQueryService;
        this.learningService = learningService;
        this.contentReviewService = contentReviewService;
        this.bookmarkService = bookmarkService;
        this.currentUserService = currentUserService;
        this.streakService = streakService;
        this.studyQueueService = studyQueueService;
        this.studyCollectionService = studyCollectionService;
        this.onboardingService = onboardingService;
        this.activeProfiles = activeProfiles;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model,
            HttpSession session
    ) {
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("type", type == null ? "" : type.name());
        model.addAttribute("level", level == null ? "" : level);
        model.addAttribute("category", category == null ? "" : category);
        var contentPage = contentQueryService.searchPage(keyword, type, level, category, page, size);
        model.addAttribute("contentPage", contentPage);
        model.addAttribute("contents", contentPage.contents());
        model.addAttribute("filterOptions", contentQueryService.filterOptions());
        boolean authenticated = currentUserService.isAuthenticated();
        model.addAttribute("authenticated", authenticated);
        if (authenticated) {
            var account = currentUserService.currentAccount();
            if (onboardingService.status(account).required()) return "redirect:/onboarding";
            var learner = learningService.overview(account);
            var dailyProgress = learningService.todayProgress(account);
            String reaction = (String) session.getAttribute("characterReaction");
            session.removeAttribute("characterReaction");
            String characterState = learner.character().growthNoticePending()
                    ? "growth"
                    : dailyProgress.remaining() == 0
                    ? "goal-complete"
                    : "happy".equals(reaction) ? "happy" : "idle";
            model.addAttribute("learner", learner);
            model.addAttribute("dailyProgress", dailyProgress);
            model.addAttribute("characterState", characterState);
            model.addAttribute("todayPlan", learningService.todayPlan(account));
            model.addAttribute("studyPreferences", learningService.studyPreferences(account));
            model.addAttribute("recentHistory", learningService.recentHistory(account, 5));
            model.addAttribute("recentQuizHistory", learningService.recentQuizHistory(account, 5));
            model.addAttribute("streak", streakService.status(account));
        }
        model.addAttribute("pendingCount", contentReviewService.countUnpublished(null));
        model.addAttribute("importReviewEnabled", activeProfiles.contains("import-sample"));
        return "home";
    }

    @GetMapping("/contents/{slug}")
    public String detail(@PathVariable String slug, Model model) {
        model.addAttribute("content", contentQueryService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND)));
        boolean authenticated = currentUserService.isAuthenticated();
        model.addAttribute("authenticated", authenticated);
        model.addAttribute("bookmarked", authenticated && bookmarkService.isBookmarked(currentUserService.currentAccount(), slug));
        model.addAttribute("queueStatus", authenticated
                ? studyQueueService.status(currentUserService.currentAccount(), slug) : null);
        model.addAttribute("collections", authenticated
                ? studyCollectionService.list(currentUserService.currentAccount()) : java.util.List.of());
        model.addAttribute("learningStatus", authenticated
                ? learningService.contentLearningStatus(currentUserService.currentAccount(), slug)
                : null);
        return "content-detail";
    }

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", contentQueryService.categoryOverview());
        return "category-overview";
    }
}
