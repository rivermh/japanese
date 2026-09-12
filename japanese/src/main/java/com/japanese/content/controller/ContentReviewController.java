package com.japanese.content.controller;

import com.japanese.content.service.ContentReviewService;
import com.japanese.content.entity.ReviewStatus;
import com.japanese.content.entity.ContentType;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
@Profile("import-sample")
public class ContentReviewController {

    private final ContentReviewService contentReviewService;

    public ContentReviewController(ContentReviewService contentReviewService) {
        this.contentReviewService = contentReviewService;
    }

    @GetMapping("/review")
    public String review(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        int normalizedPage = Math.max(page, 0);
        long totalCount = contentReviewService.countUnpublished(status, type, level);
        List<com.japanese.content.dto.ContentReviewSummary> contents =
                contentReviewService.findUnpublished(status, type, level, normalizedPage);
        model.addAttribute("contents", contents);
        model.addAttribute("status", status == null ? "" : status.name());
        model.addAttribute("type", type == null ? "" : type.name());
        model.addAttribute("level", level == null ? "" : level);
        model.addAttribute("page", normalizedPage);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("totalPages", (int) ((totalCount + 99) / 100));
        model.addAttribute("hasPrevious", normalizedPage > 0);
        model.addAttribute("hasNext", ((long) normalizedPage + 1) * 100 < totalCount);
        model.addAttribute("pendingCount", contentReviewService.countUnpublished(ReviewStatus.PENDING));
        model.addAttribute("rejectedCount", contentReviewService.countUnpublished(ReviewStatus.REJECTED));
        return "content-review";
    }

    @GetMapping("/review/{id}")
    public String reviewDetail(@PathVariable("id") Long contentId, Model model) {
        model.addAttribute("content", contentReviewService.findUnpublishedDetails(contentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND)));
        return "content-review-detail";
    }

    @PostMapping("/review/{id}/publish")
    public String publish(@PathVariable("id") Long contentId) {
        contentReviewService.publish(contentId);
        return "redirect:/review";
    }

    @PostMapping("/review/publish-selected")
    public String publishSelected(
            @RequestParam(name = "contentIds", required = false) List<Long> contentIds,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String level,
            RedirectAttributes redirectAttributes
    ) {
        int publishedCount = contentReviewService.publishSelected(contentIds);
        redirectAttributes.addFlashAttribute("reviewMessage", publishedCount + "개 콘텐츠를 공개했습니다.");
        addReviewFilters(redirectAttributes, status, type, level);
        return "redirect:/review";
    }

    @PostMapping("/review/{id}/reject")
    public String reject(
            @PathVariable("id") Long contentId,
            @RequestParam("reason") String reason
    ) {
        contentReviewService.reject(contentId, reason);
        return "redirect:/review";
    }

    @PostMapping("/review/{id}/reset")
    public String reset(@PathVariable("id") Long contentId) {
        contentReviewService.reset(contentId);
        return "redirect:/review";
    }

    private void addReviewFilters(
            RedirectAttributes redirectAttributes, ReviewStatus status, ContentType type, String level) {
        if (status != null) {
            redirectAttributes.addAttribute("status", status);
        }
        if (type != null) {
            redirectAttributes.addAttribute("type", type);
        }
        if (level != null && !level.isBlank()) {
            redirectAttributes.addAttribute("level", level);
        }
    }
}
