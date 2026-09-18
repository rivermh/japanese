package com.japanese.content.controller;

import com.japanese.content.dto.AdminContentSourceRightsModels.SourceRightsAdminView;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.service.ContentSourceRightsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * JLPT-MAX Ticket 4E-6: a normal server-rendered admin HTML workflow over the existing
 * {@link ContentSourceRightsService} - closes the operational gap Ticket 4E-4's own audit identified
 * (source-rights review was previously reachable only through direct authenticated JSON API calls, with
 * no browser-usable form anywhere in the admin UI).
 *
 * <p>This controller is a thin routing/PRG layer only - it never persists anything itself and never
 * duplicates {@link ContentSourceRightsService#reviewRights}'s transition/attribution/note validation.
 * {@link AdminContentSourceRightsApiController} (the existing JSON API) remains fully supported and
 * unmodified; both controllers call the exact same service method, so there is exactly one write path
 * for source rights in this codebase, regardless of which surface (HTML or JSON) an admin uses.
 */
@Controller
@RequestMapping("/admin/content-sources")
public class AdminContentSourceRightsController {

    private final ContentSourceRightsService rights;

    public AdminContentSourceRightsController(ContentSourceRightsService rights) {
        this.rights = rights;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("result", rights.sources().stream().map(this::view).toList());
        return "admin/content-source-rights-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("source", view(rights.source(id)));
        return "admin/content-source-rights-detail";
    }

    /**
     * The HTML form always submits an explicit {@code attributionRequired} value (checkbox-plus-hidden
     * -fallback binding - see the detail template) and an explicit {@code attributionText} value (a
     * normal, always-present text input, never omitted) - so, unlike the JSON API (where omitting a
     * field means "preserve the current value"), this HTML path always tells
     * {@link ContentSourceRightsService#reviewRights} exactly what the admin currently sees in the form:
     * a blank attribution box deliberately clears attribution (via that method's own existing
     * blank-to-null cleaning), never silently preserves a stale value.
     *
     * <p>{@code expectedCurrentStatus} is a hidden field bound to the status shown when the detail page
     * was rendered (never derived from the submitted target). It is required: a missing value is treated
     * as a stale/untrustworthy submission and rejected the same way a real mismatch is, so no request
     * can reach {@link ContentSourceRightsService#reviewRights} without it. The service compares this
     * against the persisted status AFTER acquiring its row lock, guaranteeing that a browser tab left
     * open against an older status can never silently overwrite a newer rights decision (status,
     * attribution, note, or timestamp) made by someone else in the meantime - see
     * {@link ContentSourceRightsService#reviewRights(Long, ContentSourceRightsStatus,
     * ContentSourceRightsStatus, String, Boolean, String)}.
     */
    @PostMapping("/{id}/rights")
    public String review(@PathVariable Long id,
            @RequestParam(required = false) ContentSourceRightsStatus expectedCurrentStatus,
            @RequestParam ContentSourceRightsStatus status,
            @RequestParam String note, @RequestParam(defaultValue = "false") boolean attributionRequired,
            @RequestParam(required = false) String attributionText, RedirectAttributes flash) {
        String redirect = "redirect:/admin/content-sources/" + id;
        try {
            if (expectedCurrentStatus == null) {
                throw new IllegalArgumentException("expectedCurrentStatus가 필요합니다.");
            }
            rights.reviewRights(id, expectedCurrentStatus, status, note, attributionRequired, attributionText);
            flash.addFlashAttribute("adminMessage", "source rights가 갱신되었습니다 (status=" + status + ").");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("adminError", e.getMessage());
        }
        return redirect;
    }

    private SourceRightsAdminView view(ContentSource source) {
        return SourceRightsAdminView.from(source, rights.releaseEligibilityForSource(source));
    }
}
