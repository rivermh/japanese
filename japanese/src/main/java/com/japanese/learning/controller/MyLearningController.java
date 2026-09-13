package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.content.service.BookmarkService;
import com.japanese.learning.service.LearningService;
import com.japanese.learning.service.StreakService;
import com.japanese.learning.service.StudyCollectionService;
import com.japanese.learning.service.StudyQueueService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyLearningController {
    private final CurrentUserService users;
    private final LearningService learning;
    private final StreakService streaks;
    private final BookmarkService bookmarks;
    private final StudyQueueService queue;
    private final StudyCollectionService collections;

    public MyLearningController(CurrentUserService users, LearningService learning, StreakService streaks,
                                BookmarkService bookmarks, StudyQueueService queue,
                                StudyCollectionService collections) {
        this.users = users;
        this.learning = learning;
        this.streaks = streaks;
        this.bookmarks = bookmarks;
        this.queue = queue;
        this.collections = collections;
    }

    @GetMapping("/my-learning")
    public String page(Model model) {
        var account = users.currentAccount();
        model.addAttribute("overview", learning.overview(account));
        model.addAttribute("dailyProgress", learning.todayProgress(account));
        model.addAttribute("studyPreferences", learning.studyPreferences(account));
        model.addAttribute("streak", streaks.status(account));
        model.addAttribute("bookmarkCount", bookmarks.count(account));
        model.addAttribute("queueCount", queue.count(account));
        model.addAttribute("collectionCount", collections.count(account));
        return "my-learning";
    }
}
