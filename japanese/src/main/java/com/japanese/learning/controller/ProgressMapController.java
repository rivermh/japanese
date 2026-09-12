package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.dto.JlptLevelProgress;
import com.japanese.learning.dto.JlptProgressMap;
import com.japanese.learning.service.LearningProgressMapService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
class ProgressMapPageController {
    private final CurrentUserService currentUser;
    private final LearningProgressMapService progressMap;
    ProgressMapPageController(CurrentUserService currentUser, LearningProgressMapService progressMap) {
        this.currentUser = currentUser; this.progressMap = progressMap;
    }
    @GetMapping("/progress") String page(Model model) {
        model.addAttribute("progressMap", progressMap.map(currentUser.currentAccount()));
        return "progress-map";
    }
}

@RestController
@RequestMapping("/api/v1/progress")
class ProgressMapApiController {
    private final CurrentUserService currentUser;
    private final LearningProgressMapService progressMap;
    ProgressMapApiController(CurrentUserService currentUser, LearningProgressMapService progressMap) {
        this.currentUser = currentUser; this.progressMap = progressMap;
    }
    @GetMapping("/jlpt") JlptProgressMap map() { return progressMap.map(currentUser.currentAccount()); }
    @GetMapping("/jlpt/{level}") JlptLevelProgress level(@PathVariable String level) {
        return progressMap.level(currentUser.currentAccount(), level);
    }
}
