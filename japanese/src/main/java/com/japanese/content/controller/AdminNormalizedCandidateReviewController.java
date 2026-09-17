package com.japanese.content.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.DecisionSubmission;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.NormalizedCandidateMatchAssessment;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.service.NormalizedCandidatePairReviewConflictException;
import com.japanese.content.service.NormalizedCandidatePairReviewService;
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
 * JLPT-MAX Ticket 4C: the admin web UI for the private normalized-candidate pair human-review
 * workflow. Web-only by design (no {@code /api/v1/admin/...} counterpart) - this admin UI is purely
 * server-rendered like {@code admin/content-list.html}/{@code admin/content-detail.html}, there is no
 * existing JS client that would need a JSON endpoint, and the ticket explicitly asks not to build one
 * only because {@code AdminContentReviewController}/{@code AdminContentReviewApiController} happen to
 * be paired elsewhere - see the Ticket 4C report for the full rationale.
 *
 * <p>Every path here is under {@code /admin/**}, which {@code SecurityConfig} already restricts to
 * {@code ROLE_ADMIN} - no security configuration change was needed for this ticket.
 */
@Controller
@RequestMapping("/admin/normalized-candidates/reviews")
public class AdminNormalizedCandidateReviewController {

    private final NormalizedCandidatePairReviewService reviews;
    private final CurrentUserService current;

    public AdminNormalizedCandidateReviewController(NormalizedCandidatePairReviewService reviews,
            CurrentUserService current) {
        this.reviews = reviews;
        this.current = current;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "VOCABULARY") NormalizedCandidateType candidateType,
            @RequestParam(required = false) String sourceRef,
            @RequestParam(required = false) NormalizedCandidateMatchAssessment assessment,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String freshness,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        model.addAttribute("result", reviews.list(candidateType, sourceRef, assessment, decision, freshness, page));
        model.addAttribute("candidateType", candidateType);
        model.addAttribute("sourceRef", sourceRef);
        model.addAttribute("assessment", assessment);
        model.addAttribute("decision", decision);
        model.addAttribute("freshness", freshness);
        return "admin/normalized-candidate-review-list";
    }

    @GetMapping("/{candidateType}/{leftCandidateId}/{rightCandidateId}")
    public String detail(@PathVariable NormalizedCandidateType candidateType, @PathVariable Long leftCandidateId,
            @PathVariable Long rightCandidateId, Model model) {
        model.addAttribute("detail", reviews.detail(candidateType, leftCandidateId, rightCandidateId));
        return "admin/normalized-candidate-review-detail";
    }

    @PostMapping("/{candidateType}/{leftCandidateId}/{rightCandidateId}/decision")
    public String submitDecision(@PathVariable NormalizedCandidateType candidateType,
            @PathVariable Long leftCandidateId, @PathVariable Long rightCandidateId,
            @RequestParam HumanReviewDecision decision, @RequestParam(required = false) String note,
            @RequestParam Instant expectedPairGeneratedAt,
            @RequestParam NormalizedCandidateMatchAssessment expectedAssessment,
            @RequestParam(required = false) Long expectedReviewVersion,
            RedirectAttributes flash) {
        String redirect = "redirect:/admin/normalized-candidates/reviews/" + candidateType + "/" + leftCandidateId
                + "/" + rightCandidateId;
        try {
            reviews.submitDecision(candidateType,
                    new DecisionSubmission(leftCandidateId, rightCandidateId, decision, note,
                            expectedPairGeneratedAt, expectedAssessment, expectedReviewVersion),
                    current.currentAccount());
            flash.addFlashAttribute("adminMessage", "검토 판단이 저장되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException | NormalizedCandidatePairReviewConflictException e) {
            flash.addFlashAttribute("adminError", e.getMessage());
        }
        return redirect;
    }

    @PostMapping("/{candidateType}/reanalyze")
    public String reanalyze(@PathVariable NormalizedCandidateType candidateType, @RequestParam String sourceRef,
            RedirectAttributes flash) {
        try {
            var summary = reviews.reanalyze(candidateType, sourceRef);
            flash.addFlashAttribute("adminMessage", "재분석 완료 - pair " + summary.pairCount() + "건 (unique "
                    + summary.uniqueCount() + ", exact " + summary.exactDuplicateCount() + ", possible "
                    + summary.possibleDuplicateCount() + ", conflict " + summary.conflictCount() + ")");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("adminError", e.getMessage());
        }
        return "redirect:/admin/normalized-candidates/reviews?candidateType=" + candidateType
                + "&sourceRef=" + sourceRef;
    }
}
