package com.japanese.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.repository.ContentSourceRepository;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4E-6: HTML admin workflow over {@code ContentSourceRightsService}. Mirrors
 * {@code AdminContentReviewControllerTest}'s own MockMvc/jsoup conventions exactly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sample")
@Transactional
class AdminContentSourceRightsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired ContentSourceRepository sources;

    UserAccount admin;

    @BeforeEach
    void setup() {
        admin = accounts.save(new UserAccount("rights-ui-admin-" + UUID.randomUUID(), null, "hash", "Admin", UserRole.ADMIN));
    }

    private ContentSource source(String suffix) {
        return sources.save(new ContentSource("rights-ui-" + suffix + "-" + UUID.randomUUID(), "Rights UI test source " + suffix,
                "1.0", "CC-BY 4.0", "https://example.com/license", null, "내부 사용 메모"));
    }

    // ===================================================================================
    // LIST
    // ===================================================================================

    @Test
    void listShowsIdentityStatusEligibilityAndDetailLink() throws Exception {
        ContentSource s = source("list");
        var a = user(admin.getLoginId()).roles("ADMIN");

        String html = mvc.perform(get("/admin/content-sources").with(a))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(s.getDisplayName(), s.getSourceRef(), "UNKNOWN", "NOT_RELEASABLE");
        Document doc = Jsoup.parse(html);
        assertThat(doc.select("a[href=/admin/content-sources/" + s.getId() + "]")).isNotEmpty();
    }

    @Test
    void listIsAdminOnly() throws Exception {
        mvc.perform(get("/admin/content-sources")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/content-sources").with(user("plain-user").roles("USER"))).andExpect(status().isForbidden());
    }

    // ===================================================================================
    // DETAIL
    // ===================================================================================

    @Test
    void detailShowsIdentityLicenseUsageAttributionAndCurrentStatus() throws Exception {
        ContentSource s = source("detail");
        var a = user(admin.getLoginId()).roles("ADMIN");

        String html = mvc.perform(get("/admin/content-sources/" + s.getId()).with(a))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(s.getDisplayName(), s.getSourceRef(), "1.0", "CC-BY 4.0",
                "https://example.com/license", "내부 사용 메모", "UNKNOWN", "NOT_RELEASABLE");
    }

    @Test
    void detailOnlyOffersTransitionsValidFromTheCurrentStatus() throws Exception {
        ContentSource s = source("valid-only");
        var a = user(admin.getLoginId()).roles("ADMIN");

        // UNKNOWN: only MANUAL_REVIEW_REQUIRED must be offered.
        Document unknownPage = Jsoup.parse(mvc.perform(get("/admin/content-sources/" + s.getId()).with(a))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(unknownPage.select("input[name=status]")).hasSize(1);
        assertThat(unknownPage.select("input[name=status]").val()).isEqualTo("MANUAL_REVIEW_REQUIRED");
        assertThat(unknownPage.select("input[name=expectedCurrentStatus]").val()).isEqualTo("UNKNOWN");

        mvc.perform(post("/admin/content-sources/" + s.getId() + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "UNKNOWN")
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "검토 시작"))
                .andExpect(status().is3xxRedirection());

        // MANUAL_REVIEW_REQUIRED: both ALLOWED and BLOCKED must be offered, nothing else.
        Document mrrPage = Jsoup.parse(mvc.perform(get("/admin/content-sources/" + s.getId()).with(a))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var values = mrrPage.select("input[name=status]").eachAttr("value");
        assertThat(values).containsExactlyInAnyOrder("ALLOWED", "BLOCKED");
    }

    @Test
    void detailReturns404StyleResponseForNonexistentSource() throws Exception {
        var a = user(admin.getLoginId()).roles("ADMIN");
        mvc.perform(get("/admin/content-sources/999999999").with(a))
                .andExpect(status().isNotFound());
    }

    // ===================================================================================
    // POST: valid transition, note, timestamp, attribution
    // ===================================================================================

    @Test
    void validTransitionPersistsStatusNoteTimestampAndRedirectsWithFlash() throws Exception {
        ContentSource s = source("transition");
        var a = user(admin.getLoginId()).roles("ADMIN");
        Long id = s.getId();

        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "UNKNOWN")
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "권리 자료 확인 시작"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/content-sources/" + id));

        ContentSource reloaded = sources.findById(id).orElseThrow();
        assertThat(reloaded.getRightsStatus()).isEqualTo(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED);
        assertThat(reloaded.getRightsReviewNote()).isEqualTo("권리 자료 확인 시작");
        assertThat(reloaded.getRightsReviewedAt()).isNotNull();

        String afterRedirect = mvc.perform(get("/admin/content-sources/" + id).with(a))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(afterRedirect).contains("MANUAL_REVIEW_REQUIRED");
    }

    @Test
    void successFlashMessageIsShownAfterRedirect() throws Exception {
        ContentSource s = source("flash");
        var a = user(admin.getLoginId()).roles("ADMIN");

        var result = mvc.perform(post("/admin/content-sources/" + s.getId() + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "UNKNOWN")
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "검토"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/content-sources/" + s.getId()))
                .andReturn();

        var flashMap = result.getFlashMap();
        assertThat((Object) flashMap).isNotNull();
        assertThat((String) flashMap.get("adminMessage")).isNotBlank();
        assertThat((Object) flashMap.get("adminError")).isNull();
    }

    // ===================================================================================
    // POST: UNKNOWN -> ALLOWED must remain impossible (section 13's explicit regression)
    // ===================================================================================

    @Test
    void unknownToAllowedDirectlyIsRejectedWithNoMutation() throws Exception {
        ContentSource s = source("unknown-allowed");
        var a = user(admin.getLoginId()).roles("ADMIN");
        Long id = s.getId();

        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "UNKNOWN")
                        .param("status", "ALLOWED").param("note", "우회 시도"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/content-sources/" + id));

        ContentSource reloaded = sources.findById(id).orElseThrow();
        assertThat(reloaded.getRightsStatus())
                .as("UNKNOWN -> ALLOWED must be rejected by the existing domain validation, not silently accepted")
                .isEqualTo(ContentSourceRightsStatus.UNKNOWN);
        assertThat(reloaded.getRightsReviewedAt()).isNull();
        assertThat(reloaded.getRightsReviewNote()).isNull();

        String afterRedirect = mvc.perform(get("/admin/content-sources/" + id).with(a))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(afterRedirect).contains("UNKNOWN");
    }

    @Test
    void blankReviewNoteIsRejectedWithNoMutation() throws Exception {
        ContentSource s = source("blank-note");
        var a = user(admin.getLoginId()).roles("ADMIN");
        Long id = s.getId();

        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "UNKNOWN")
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "   "))
                .andExpect(status().is3xxRedirection());

        assertThat(sources.findById(id).orElseThrow().getRightsStatus()).isEqualTo(ContentSourceRightsStatus.UNKNOWN);
    }

    // ===================================================================================
    // POST: stale browser intent (hardening pass, 2026-09-18) - a stale detail page must never
    // silently overwrite a newer rights decision made by someone else since it was rendered.
    // ===================================================================================

    @Test
    void staleExpectedCurrentStatusRejectsSubmissionWithoutOverwritingNewerRightsData() throws Exception {
        ContentSource s = source("stale-overwrite");
        var a = user(admin.getLoginId()).roles("ADMIN");
        Long id = s.getId();

        // Admin A's browser is still showing UNKNOWN (both A and a stale B loaded the page at this point).
        // A completed review moves the source all the way to ALLOWED with fresh attribution data.
        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "UNKNOWN")
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "1차 검토"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "MANUAL_REVIEW_REQUIRED")
                        .param("status", "ALLOWED").param("note", "최신 검토 - 허용")
                        .param("attributionRequired", "true").param("attributionText", "NEW ATTRIBUTION"))
                .andExpect(status().is3xxRedirection());

        ContentSource afterFreshReview = sources.findById(id).orElseThrow();
        assertThat(afterFreshReview.getRightsStatus()).isEqualTo(ContentSourceRightsStatus.ALLOWED);
        assertThat(afterFreshReview.isAttributionRequired()).isTrue();
        assertThat(afterFreshReview.getAttribution()).isEqualTo("NEW ATTRIBUTION");
        var newTimestamp = afterFreshReview.getRightsReviewedAt();
        var newNote = afterFreshReview.getRightsReviewNote();
        assertThat(newTimestamp).isNotNull();
        assertThat(newNote).isEqualTo("최신 검토 - 허용");

        // Stale admin B's browser tab still remembers expectedCurrentStatus=MANUAL_REVIEW_REQUIRED (the
        // status it saw when it rendered, before A's ALLOWED review above) and submits old form values.
        var staleResult = mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "MANUAL_REVIEW_REQUIRED")
                        .param("status", "BLOCKED").param("note", "오래된 화면에서 제출한 차단 시도")
                        .param("attributionRequired", "false").param("attributionText", "OLD ATTRIBUTION"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/content-sources/" + id))
                .andReturn();
        assertThat((String) staleResult.getFlashMap().get("adminError")).isNotBlank();
        assertThat((Object) staleResult.getFlashMap().get("adminMessage")).isNull();

        ContentSource afterStaleSubmission = sources.findById(id).orElseThrow();
        assertThat(afterStaleSubmission.getRightsStatus())
                .as("the stale submission must not overwrite the newer ALLOWED status")
                .isEqualTo(ContentSourceRightsStatus.ALLOWED);
        assertThat(afterStaleSubmission.isAttributionRequired())
                .as("the stale submission's attributionRequired=false must not overwrite the newer true value")
                .isTrue();
        assertThat(afterStaleSubmission.getAttribution())
                .as("the stale submission's OLD ATTRIBUTION must not overwrite NEW ATTRIBUTION")
                .isEqualTo("NEW ATTRIBUTION");
        assertThat(afterStaleSubmission.getRightsReviewedAt())
                .as("the stale submission must not replace the newer review timestamp")
                .isEqualTo(newTimestamp);
        assertThat(afterStaleSubmission.getRightsReviewNote())
                .as("the stale submission must not replace the newer review note")
                .isEqualTo(newNote);
    }

    @Test
    void missingExpectedCurrentStatusIsRejectedWithNoMutation() throws Exception {
        ContentSource s = source("missing-expected-status");
        var a = user(admin.getLoginId()).roles("ADMIN");
        Long id = s.getId();

        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "expectedCurrentStatus 누락"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/content-sources/" + id));

        assertThat(sources.findById(id).orElseThrow().getRightsStatus())
                .as("a POST missing expectedCurrentStatus must be treated as stale/untrustworthy and rejected")
                .isEqualTo(ContentSourceRightsStatus.UNKNOWN);
    }

    // ===================================================================================
    // POST: attribution checkbox/text semantics (sections 14-16)
    // ===================================================================================

    @Test
    void requiredAttributionMustBePresentBeforeAllowedTransitionThroughHtmlPath() throws Exception {
        ContentSource s = source("attribution-required");
        var a = user(admin.getLoginId()).roles("ADMIN");
        Long id = s.getId();

        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "UNKNOWN")
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "검토")
                        .param("attributionRequired", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(sources.findById(id).orElseThrow().isAttributionRequired()).isTrue();

        // ALLOWED with attributionRequired=true and blank attributionText must fail.
        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "MANUAL_REVIEW_REQUIRED")
                        .param("status", "ALLOWED").param("note", "허용 시도")
                        .param("attributionRequired", "true").param("attributionText", ""))
                .andExpect(status().is3xxRedirection());
        assertThat(sources.findById(id).orElseThrow().getRightsStatus())
                .as("ALLOWED with attributionRequired=true and no attribution text must not succeed")
                .isEqualTo(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED);

        // Same transition with truthful nonblank attribution must succeed.
        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "MANUAL_REVIEW_REQUIRED")
                        .param("status", "ALLOWED").param("note", "허용 근거 확인")
                        .param("attributionRequired", "true").param("attributionText", "Source Authors"))
                .andExpect(status().is3xxRedirection());
        ContentSource reloaded = sources.findById(id).orElseThrow();
        assertThat(reloaded.getRightsStatus()).isEqualTo(ContentSourceRightsStatus.ALLOWED);
        assertThat(reloaded.getAttribution()).isEqualTo("Source Authors");
    }

    @Test
    void checkedCheckboxSetsAttributionRequiredTrue() throws Exception {
        ContentSource s = source("checkbox-true");
        var a = user(admin.getLoginId()).roles("ADMIN");
        Long id = s.getId();

        // Simulates the checked-checkbox HTML submission: both "true" (checkbox) and "false" (hidden
        // fallback) are submitted, checkbox first - the controller/binding must resolve this to true.
        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "UNKNOWN")
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "검토")
                        .param("attributionRequired", "true", "false"))
                .andExpect(status().is3xxRedirection());

        assertThat(sources.findById(id).orElseThrow().isAttributionRequired())
                .as("a checked checkbox (true submitted before the hidden false fallback) must bind to true")
                .isTrue();
    }

    @Test
    void uncheckedCheckboxSetsAttributionRequiredFalseEvenIfPreviouslyTrue() throws Exception {
        ContentSource s = source("checkbox-false");
        var a = user(admin.getLoginId()).roles("ADMIN");
        Long id = s.getId();

        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "UNKNOWN")
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "검토")
                        .param("attributionRequired", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(sources.findById(id).orElseThrow().isAttributionRequired()).isTrue();

        // Simulates the unchecked-checkbox HTML submission: only the hidden "false" fallback is
        // submitted (the checkbox itself sends nothing when unchecked) - must NOT silently preserve
        // the previous true value.
        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "MANUAL_REVIEW_REQUIRED")
                        .param("status", "BLOCKED").param("note", "정지")
                        .param("attributionRequired", "false"))
                .andExpect(status().is3xxRedirection());

        assertThat(sources.findById(id).orElseThrow().isAttributionRequired())
                .as("an intentionally unchecked checkbox must bind to false, not silently preserve the old true value")
                .isFalse();
    }

    @Test
    void blankAttributionTextClearsExistingAttributionDeliberately() throws Exception {
        ContentSource s = source("clear-attribution");
        var a = user(admin.getLoginId()).roles("ADMIN");
        Long id = s.getId();

        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "UNKNOWN")
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "검토")
                        .param("attributionRequired", "false").param("attributionText", "Old Attribution"))
                .andExpect(status().is3xxRedirection());
        assertThat(sources.findById(id).orElseThrow().getAttribution()).isEqualTo("Old Attribution");

        // Submitting a blank attributionText (the always-present text input, emptied) must clear it -
        // never silently preserve the old value, since the HTML form always sends this field explicitly.
        mvc.perform(post("/admin/content-sources/" + id + "/rights").with(a).with(csrf())
                        .param("expectedCurrentStatus", "MANUAL_REVIEW_REQUIRED")
                        .param("status", "BLOCKED").param("note", "정지")
                        .param("attributionRequired", "false").param("attributionText", ""))
                .andExpect(status().is3xxRedirection());

        assertThat(sources.findById(id).orElseThrow().getAttribution())
                .as("a deliberately blanked attribution text input must clear the stored attribution")
                .isNull();
    }

    // ===================================================================================
    // POST: security
    // ===================================================================================

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        ContentSource s = source("no-csrf");
        var a = user(admin.getLoginId()).roles("ADMIN");

        mvc.perform(post("/admin/content-sources/" + s.getId() + "/rights").with(a)
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "검토"))
                .andExpect(status().isForbidden());
        assertThat(sources.findById(s.getId()).orElseThrow().getRightsStatus()).isEqualTo(ContentSourceRightsStatus.UNKNOWN);
    }

    @Test
    void postAsPlainUserIsForbidden() throws Exception {
        ContentSource s = source("plain-user-post");

        mvc.perform(post("/admin/content-sources/" + s.getId() + "/rights").with(user("plain-user").roles("USER")).with(csrf())
                        .param("status", "MANUAL_REVIEW_REQUIRED").param("note", "검토"))
                .andExpect(status().isForbidden());
        assertThat(sources.findById(s.getId()).orElseThrow().getRightsStatus()).isEqualTo(ContentSourceRightsStatus.UNKNOWN);
    }

    @Test
    void dashboardLinksToContentSourceRightsList() throws Exception {
        var a = user(admin.getLoginId()).roles("ADMIN");
        mvc.perform(get("/admin").with(a))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/content-sources")));
    }
}
