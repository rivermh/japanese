package com.japanese.content.controller;

import com.japanese.content.dto.PromotionReadinessModels.OverallStatus;
import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.service.NormalizedCandidatePromotionRejectedException;
import com.japanese.content.service.NormalizedCandidatePromotionReadinessService;
import com.japanese.content.service.NormalizedCandidatePromotionStaleException;
import com.japanese.content.service.NormalizedVocabularyCandidatePromotionResult;
import com.japanese.content.service.NormalizedVocabularyCandidatePromotionService;
import com.japanese.content.service.PromotionReadinessIssueCode;
import java.time.Instant;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * JLPT-MAX Ticket 4D: 100% read-only admin UI for the private candidate promotion-readiness/dry-run
 * planner (see {@link NormalizedCandidatePromotionReadinessService}'s class javadoc for the full
 * read-only boundary that service itself still keeps).
 *
 * <p>JLPT-MAX Ticket 4E-1 adds exactly one write action to this controller: {@link #promote}, a
 * single-Vocabulary-candidate production draft promotion. It follows the same
 * POST-then-redirect-with-flash-message shape as {@code AdminNormalizedCandidateReviewController}'s
 * {@code submitDecision}/{@code reanalyze} - errors are caught inline as a flash message rather than
 * surfaced through {@link AdminNormalizedCandidatePromotionReadinessExceptionHandler} (that advice
 * remains scoped to the GET list/detail path-parameter-resolution failures it already handled before
 * this ticket).
 *
 * <p>Every path here is under {@code /admin/**}, which {@code SecurityConfig} already restricts to
 * {@code ROLE_ADMIN} - no security configuration change was needed for this ticket, mirroring
 * {@code AdminNormalizedCandidateReviewController}.
 */
@Controller
@RequestMapping("/admin/normalized-candidates/promotion-readiness")
public class AdminNormalizedCandidatePromotionReadinessController {

    private final NormalizedCandidatePromotionReadinessService readiness;
    private final NormalizedVocabularyCandidatePromotionService promotion;

    public AdminNormalizedCandidatePromotionReadinessController(NormalizedCandidatePromotionReadinessService readiness,
            NormalizedVocabularyCandidatePromotionService promotion) {
        this.readiness = readiness;
        this.promotion = promotion;
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

    /**
     * JLPT-MAX Ticket 4E-1: promotes exactly one Vocabulary candidate to a production draft. See
     * {@link NormalizedVocabularyCandidatePromotionService}'s class javadoc for the full
     * transaction/lock/mapping contract - this controller method only routes the request and turns
     * its outcome into a flash message, matching the existing admin PRG convention.
     *
     * <p>Ticket 4E-1 hardening: {@code expectedNormalizedAt} carries the candidate revision the admin
     * actually saw on the GET detail page (rendered from {@code detail.candidateFields.normalizedAt} -
     * see that template) - mirrors {@code AdminNormalizedCandidateReviewController.submitDecision}'s
     * own {@code expectedPairGeneratedAt} hidden-field convention.
     */
    @PostMapping("/{candidateType}/{candidateId}/promote")
    public String promote(@PathVariable NormalizedCandidateType candidateType, @PathVariable Long candidateId,
            @RequestParam Instant expectedNormalizedAt, RedirectAttributes flash) {
        String redirect = "redirect:/admin/normalized-candidates/promotion-readiness/" + candidateType + "/" + candidateId;
        try {
            NormalizedVocabularyCandidatePromotionResult result =
                    promotion.promote(candidateType, candidateId, expectedNormalizedAt);
            flash.addFlashAttribute("adminMessage",
                    "production draft로 승격되었습니다 (slug=" + result.slug() + ").");
        } catch (NormalizedCandidatePromotionStaleException | NormalizedCandidatePromotionRejectedException e) {
            flash.addFlashAttribute("adminError", e.getMessage());
        }
        return redirect;
    }
}
