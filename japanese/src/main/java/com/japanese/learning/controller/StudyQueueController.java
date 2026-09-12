package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.service.StudyQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class StudyQueuePageController {
    private final StudyQueueService queueService;
    private final CurrentUserService currentUserService;

    StudyQueuePageController(StudyQueueService queueService, CurrentUserService currentUserService) {
        this.queueService = queueService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/study-queue")
    String page(Model model) {
        model.addAttribute("queue", queueService.list(currentUserService.currentAccount()));
        return "study-queue";
    }

    @PostMapping("/contents/{slug}/study-queue")
    String add(@PathVariable String slug, @org.springframework.web.bind.annotation.RequestParam(required = false) String returnTo,
               RedirectAttributes attributes) {
        var status = queueService.add(currentUserService.currentAccount(), slug);
        attributes.addFlashAttribute("queueMessage", status.queued() ? "학습 목록에 추가했습니다." : status.label());
        return "redirect:" + safeReturn(returnTo, slug);
    }

    @PostMapping("/contents/{slug}/study-queue/remove")
    String remove(@PathVariable String slug, @org.springframework.web.bind.annotation.RequestParam(required = false) String returnTo,
                  RedirectAttributes attributes) {
        queueService.remove(currentUserService.currentAccount(), slug);
        attributes.addFlashAttribute("queueMessage", "학습 목록에서 제거했습니다.");
        return "redirect:" + safeReturn(returnTo, slug);
    }

    private String safeReturn(String returnTo, String slug) {
        return returnTo != null && returnTo.startsWith("/") ? returnTo : "/contents/" + slug;
    }
}

@RestController
@RequestMapping("/api/v1/study/queue")
class StudyQueueApiController {
    private final StudyQueueService queueService;
    private final CurrentUserService currentUserService;

    StudyQueueApiController(StudyQueueService queueService, CurrentUserService currentUserService) {
        this.queueService = queueService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    Object list() {
        return queueService.list(currentUserService.currentAccount());
    }

    @PostMapping("/{slug}")
    Object add(@PathVariable String slug) {
        return queueService.add(currentUserService.currentAccount(), slug);
    }

    @DeleteMapping("/{slug}")
    ResponseEntity<Void> remove(@PathVariable String slug) {
        queueService.remove(currentUserService.currentAccount(), slug);
        return ResponseEntity.noContent().build();
    }
}
