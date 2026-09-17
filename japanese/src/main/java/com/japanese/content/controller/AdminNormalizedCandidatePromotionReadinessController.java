package com.japanese.content.controller;

import com.japanese.content.dto.PromotionReadinessModels.OverallStatus;
import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.service.NormalizedCandidatePromotionReadinessService;
import com.japanese.content.service.PromotionReadinessIssueCode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * JLPT-MAX Ticket 4D: 100% read-only admin UI for the private candidate promotion-readiness/dry-run
 * planner. There is no POST mapping anywhere in this controller - viewing this page never changes
 * any row in any table, private or production (see
 * {@link NormalizedCandidatePromotionReadinessService}'s class javadoc for the full boundary).
 *
 * <p>Every path here is under {@code /admin/**}, which {@code SecurityConfig} already restricts to
 * {@code ROLE_ADMIN} - no security configuration change was needed for this ticket, mirroring
 * {@code AdminNormalizedCandidateReviewController}.
 */
@Controller
@RequestMapping("/admin/normalized-candidates/promotion-readiness")
public class AdminNormalizedCandidatePromotionReadinessController {

    private final NormalizedCandidatePromotionReadinessService readiness;

    public AdminNormalizedCandidatePromotionReadinessController(NormalizedCandidatePromotionReadinessService readiness) {
        this.readiness = readiness;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "VOCABULARY") NormalizedCandidateType candidateType,
            @RequestParam(required = false) String sourceRef,
            @RequestParam(required = false) OverallStatus overallStatus,
            @RequestParam(required = false) PromotionReadinessIssueCode issueCode,
            @RequestParam(required = false) NormalizedCandidateQualityState quality,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        model.addAttribute("result", readiness.list(candidateType, sourceRef, overallStatus, issueCode, quality, page));
        model.addAttribute("summary", readiness.summary(candidateType, sourceRef));
        model.addAttribute("candidateType", candidateType);
        model.addAttribute("sourceRef", sourceRef);
        model.addAttribute("overallStatus", overallStatus);
        model.addAttribute("issueCode", issueCode);
        model.addAttribute("quality", quality);
        return "admin/normalized-candidate-promotion-readiness-list";
    }

    @GetMapping("/{candidateType}/{candidateId}")
    public String detail(@PathVariable NormalizedCandidateType candidateType, @PathVariable Long candidateId,
            Model model) {
        model.addAttribute("detail", readiness.detail(candidateType, candidateId));
        return "admin/normalized-candidate-promotion-readiness-detail";
    }
}
