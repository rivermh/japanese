package com.japanese.content.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupCreationRequest;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupCreationResult;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.EdgeExpectation;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.ParticipantExpectation;
import com.japanese.content.dto.NormalizedCandidateGroupPromotionModels.GroupPromotionResult;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupStatus;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.service.NormalizedCandidateCanonicalGroupRejectedException;
import com.japanese.content.service.NormalizedCandidateCanonicalGroupService;
import com.japanese.content.service.NormalizedCandidateCanonicalGroupStaleException;
import com.japanese.content.service.NormalizedCandidateGroupPromotionRejectedException;
import com.japanese.content.service.NormalizedCandidateGroupPromotionService;
import com.japanese.content.service.NormalizedCandidateGroupPromotionStaleException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * JLPT-MAX Ticket 4E-3A: the admin web UI for the human canonical-selection persistence workflow -
 * mirrors {@code AdminNormalizedCandidateReviewController}/
 * {@code AdminNormalizedCandidatePromotionReadinessController}'s existing POST-then-redirect-with-flash
 * convention exactly. Every path here is under {@code /admin/**}, already restricted to
 * {@code ROLE_ADMIN} by {@code SecurityConfig} - no security configuration change needed.
 *
 * <p><b>Smallest usable creation entry point</b> (JLPT-MAX Ticket 4E-3A, "do not redesign the whole
 * admin UI"): the first implementation only exposes creating a TWO-candidate canonical group, reached
 * directly from the existing pair-review detail page
 * ({@code admin/normalized-candidate-review-detail.html}) when that pair's current review is a fresh
 * SAME_CONTENT decision - see {@link #createFromPair}. The underlying
 * {@link NormalizedCandidateCanonicalGroupService#create} itself is fully general for 3+ participants
 * (exercised directly by service-level tests) - a future ticket can add a dedicated multi-select
 * creation page without any service change.
 */
@Controller
@RequestMapping("/admin/normalized-candidates/canonical-groups")
public class AdminNormalizedCandidateCanonicalGroupController {

    private final NormalizedCandidateCanonicalGroupService groups;
    private final NormalizedCandidateGroupPromotionService promotion;
    private final CurrentUserService current;

    public AdminNormalizedCandidateCanonicalGroupController(NormalizedCandidateCanonicalGroupService groups,
            NormalizedCandidateGroupPromotionService promotion, CurrentUserService current) {
        this.groups = groups;
        this.promotion = promotion;
        this.current = current;
    }

    @GetMapping
    public String list(@RequestParam(required = false) NormalizedCandidateCanonicalGroupStatus status,
            Model model) {
        model.addAttribute("result", groups.list(status));
        model.addAttribute("status", status);
        return "admin/normalized-candidate-canonical-group-list";
    }

    @GetMapping("/{groupId}")
    public String detail(@PathVariable Long groupId, Model model) {
        var detail = groups.detail(groupId);
        model.addAttribute("detail", detail);
        if (detail.active()) {
            model.addAttribute("eligibility", promotion.previewEligibility(groupId));
        }
        return "admin/normalized-candidate-canonical-group-detail";
    }

    /**
     * JLPT-MAX Ticket 4E-3B: promotes exactly one ACTIVE, fresh canonical group to a production draft.
     * See {@link NormalizedCandidateGroupPromotionService}'s class javadoc for the full transaction/
     * lock/mapping contract - this controller method only routes the request and turns its outcome
     * into a flash message, matching the existing PRG convention
     * {@code AdminNormalizedCandidatePromotionReadinessController#promote} already uses for Ticket
     * 4E-1's own single-candidate promotion.
     */
    @PostMapping("/{groupId}/promote")
    public String promote(@PathVariable Long groupId, @RequestParam long expectedGroupVersion,
            RedirectAttributes flash) {
        String redirect = "redirect:/admin/normalized-candidates/canonical-groups/" + groupId;
        try {
            GroupPromotionResult result = promotion.promote(groupId, expectedGroupVersion, current.currentAccount());
            flash.addFlashAttribute("adminMessage",
                    "group이 production draft로 승격되었습니다 (slug=" + result.slug() + ").");
        } catch (NormalizedCandidateGroupPromotionRejectedException | NormalizedCandidateGroupPromotionStaleException e) {
            flash.addFlashAttribute("adminError", e.getMessage());
        }
        return redirect;
    }

    /**
     * Builds the smallest-necessary {@link CanonicalGroupCreationRequest} (2 participants, 1 edge)
     * directly from the exact freshness evidence
     * {@code admin/normalized-candidate-review-detail.html} already renders for a SAME_CONTENT pair -
     * see that template's own hidden fields for {@code expectedPairGeneratedAt}/
     * {@code expectedAssessment}/{@code expectedReviewVersion} (Ticket 4C convention this form reuses).
     */
    @PostMapping("/create-pair")
    public String createFromPair(@RequestParam NormalizedCandidateType candidateType,
            @RequestParam Long leftCandidateId, @RequestParam Long rightCandidateId,
            @RequestParam Long canonicalCandidateId, @RequestParam Instant expectedLeftNormalizedAt,
            @RequestParam Instant expectedRightNormalizedAt, @RequestParam Long expectedReviewVersion,
            @RequestParam(required = false) String note, RedirectAttributes flash) {
        String reviewRedirect = "redirect:/admin/normalized-candidates/reviews/" + candidateType + "/"
                + leftCandidateId + "/" + rightCandidateId;
        try {
            CanonicalGroupCreationRequest request = new CanonicalGroupCreationRequest(canonicalCandidateId,
                    List.of(new ParticipantExpectation(leftCandidateId, expectedLeftNormalizedAt),
                            new ParticipantExpectation(rightCandidateId, expectedRightNormalizedAt)),
                    List.of(new EdgeExpectation(leftCandidateId, rightCandidateId, expectedReviewVersion)), note);
            CanonicalGroupCreationResult result = groups.create(candidateType, request, current.currentAccount());
            flash.addFlashAttribute("adminMessage", "canonical group이 생성되었습니다 (group=" + result.groupId() + ").");
            return "redirect:/admin/normalized-candidates/canonical-groups/" + result.groupId();
        } catch (NormalizedCandidateCanonicalGroupRejectedException | NormalizedCandidateCanonicalGroupStaleException
                | IllegalArgumentException e) {
            flash.addFlashAttribute("adminError", e.getMessage());
            return reviewRedirect;
        }
    }

    @PostMapping("/{groupId}/dissolve")
    public String dissolve(@PathVariable Long groupId, @RequestParam long expectedVersion,
            @RequestParam(required = false) String dissolutionNote, RedirectAttributes flash) {
        String redirect = "redirect:/admin/normalized-candidates/canonical-groups/" + groupId;
        try {
            groups.dissolve(groupId, expectedVersion, dissolutionNote, current.currentAccount());
            flash.addFlashAttribute("adminMessage", "canonical group이 해체되었습니다.");
        } catch (NormalizedCandidateCanonicalGroupRejectedException | NormalizedCandidateCanonicalGroupStaleException e) {
            flash.addFlashAttribute("adminError", e.getMessage());
        }
        return redirect;
    }
}
