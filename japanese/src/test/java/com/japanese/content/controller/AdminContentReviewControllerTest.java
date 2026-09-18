package com.japanese.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.japanese.account.entity.*;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.entity.*;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentSourceRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.service.ContentSourceRightsService;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("sample") @Transactional
class AdminContentReviewControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired ContentItemRepository contents;
    @Autowired ContentSourceRepository sources;
    @Autowired LevelRepository levels;
    @Autowired ContentSourceRightsService sourceRights;
    @Autowired com.japanese.content.service.ContentReleaseBatchService releaseBatches;
    @Autowired com.japanese.content.service.ContentReleaseDryRunService releasePreview;
    UserAccount admin;
    ContentItem pending;

    @BeforeEach void setup() {
        admin=accounts.save(new UserAccount("web-admin-"+UUID.randomUUID(),null,"hash","Admin",UserRole.ADMIN));
        ContentSource source=sources.save(new ContentSource("web-source","Web test source","1",null,null,null,null));
        sourceRights.reviewRights(source.getId(),ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,"검토",false,null);
        sourceRights.reviewRights(source.getId(),ContentSourceRightsStatus.ALLOWED,"허용",false,null);
        pending=new ContentItem("web-pending-"+UUID.randomUUID(),ContentType.WORD,"web-source",false);
        Word word=new Word("관리검수","かんり","명사",null); word.addMeaning(new Meaning("ko","관리 검수",0)); pending.attachWord(word); pending=contents.save(pending);
    }

    @Test void adminCanReviewSourceRightsThroughProtectedApi() throws Exception {
        ContentSource source=sources.save(new ContentSource("web-rights-"+UUID.randomUUID(),"Web rights source","1",null,null,null,null));
        var a=user(admin.getLoginId()).roles("ADMIN");

        mvc.perform(get("/api/v1/admin/content-sources/"+source.getId()).with(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sourceRef").value(source.getSourceRef()))
                .andExpect(jsonPath("$.displayName").exists())
                .andExpect(jsonPath("$.rightsStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.allowedForRelease").value(false))
                // JLPT-MAX Ticket 4E-6 hardening: the HTML-only admin UI fields (usageNote,
                // allowedNextStatuses) must never leak into this JSON API contract - they live on a
                // separate HTML-only view record (SourceRightsAdminView), not on SourceRightsView.
                .andExpect(jsonPath("$.usageNote").doesNotExist())
                .andExpect(jsonPath("$.allowedNextStatuses").doesNotExist());
        mvc.perform(post("/api/v1/admin/content-sources/"+source.getId()+"/rights").with(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MANUAL_REVIEW_REQUIRED\",\"note\":\"자료 확인\",\"attributionRequired\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rightsStatus").value("MANUAL_REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.usageNote").doesNotExist())
                .andExpect(jsonPath("$.allowedNextStatuses").doesNotExist());
        mvc.perform(post("/api/v1/admin/content-sources/"+source.getId()+"/rights").with(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ALLOWED\",\"note\":\"허용 근거 확인\",\"attributionRequired\":true,\"attributionText\":\"Source authors\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rightsStatus").value("ALLOWED"))
                .andExpect(jsonPath("$.rightsReviewNote").value("허용 근거 확인"))
                .andExpect(jsonPath("$.attributionText").value("Source authors"))
                .andExpect(jsonPath("$.allowedForRelease").value(true))
                .andExpect(jsonPath("$.usageNote").doesNotExist())
                .andExpect(jsonPath("$.allowedNextStatuses").doesNotExist());

        // This API path (no expectedCurrentStatus concept) must remain unaffected by the HTML
        // controller's stale-intent protection - it keeps using the pre-existing 5-arg reviewRights
        // overload, which always skips the staleness check.
        mvc.perform(get("/api/v1/admin/content-sources").with(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=="+source.getId()+")]").exists());
    }

    @Test void protectsWebAndApiByRoleAndCsrf() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin").with(user("u").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin").with(user(admin.getLoginId()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("ADMIN CONTENT REVIEW")));
        mvc.perform(post("/api/v1/admin/contents/"+pending.getId()+"/approve").with(user(admin.getLoginId()).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test void releaseDryRunIsAdminOnlyAndReadOnly() throws Exception {
        var a=user(admin.getLoginId()).roles("ADMIN");
        mvc.perform(post("/api/v1/admin/content-release/dry-run").with(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WORD\",\"level\":\"N5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateVersion").value("phase1a-v1"))
                .andExpect(jsonPath("$.totalTargetCount").exists())
                .andExpect(jsonPath("$.digest").isString());
        mvc.perform(post("/api/v1/admin/content-release/dry-run").with(user("regular").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void releaseDryRunPageRendersForAdmin() throws Exception {
        mvc.perform(get("/admin/contents/dry-run").with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/content-dry-run"))
                .andExpect(content().string(containsString("RELEASE DRY-RUN")));
    }

    @Test void batchPagesProtectRolesAndRenderSafeDefaults() throws Exception {
        mvc.perform(get("/admin/contents/dry-run").with(user("ordinary").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/content-release/batches").with(user("ordinary").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/content-release/batches/1").with(user("ordinary").roles("USER")))
                .andExpect(status().isForbidden());
        String html = mvc.perform(get("/admin/contents/dry-run").with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var doc = org.jsoup.Jsoup.parse(html);
        assertThat(doc.select("select[name=type][required] option").first().attr("value")).isEmpty();
        assertThat(doc.select("select[name=level][required]")).hasSize(1);
        assertThat(doc.select("select[name=published] option").first().val()).isEqualTo("false");
        assertThat(doc.select("select[name=reviewStatus] option").first().val()).isEqualTo("PENDING");
        assertThat(doc.select("#release-preview[hidden]")).hasSize(1);
        assertThat(doc.select("#execute-button[disabled]")).hasSize(1);
        assertThat(doc.select("#execution-note[required]")).hasSize(1);
        assertThat(doc.select("#confirm-preview[required]")).hasSize(1);
        assertThat(doc.select("#release-csrf").val()).isNotBlank();
    }

    @Test void batchHistoryDetailAndRollbackRenderSyntheticManifest() throws Exception {
        pending.addLevel(levels.findBySystemAndCode("JLPT", "N5")
                .orElseGet(() -> levels.save(new Level("JLPT", "N5", "N5"))));
        pending.addExample(new Example("検査します。", null, "검사합니다.", 0));
        contents.flush();
        var preview = releasePreview.run(new com.japanese.content.dto.AdminContentReleaseDryRunModels.Filter(
                ContentType.WORD, "N5", ReviewStatus.PENDING, false, "web-source", null));
        var batch = releaseBatches.execute(new com.japanese.content.dto.AdminContentReleaseBatchModels.ExecuteRequest(
                preview.targetIds(), preview.digest(), preview.gateVersion(), ContentReleaseMode.RELEASABLE_ONLY,
                "Synthetic UI fixture", java.util.List.of()), admin);
        var auth = user(admin.getLoginId()).roles("ADMIN");
        mvc.perform(get("/admin/content-release/batches").with(auth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("EXECUTED")));
        String html = mvc.perform(get("/admin/content-release/batches/" + batch.id()).with(auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var doc = org.jsoup.Jsoup.parse(html);
        assertThat(doc.select("#rollback-form")).hasSize(1);
        assertThat(doc.select("#rollback-reason[required]")).hasSize(1);
        assertThat(doc.select("#rollback-form input[type=checkbox][required]")).hasSize(1);
        assertThat(doc.text()).contains("Synthetic UI fixture", preview.digest(), "PENDING", "APPROVED").doesNotContain("강제 rollback");
        releaseBatches.rollback(batch.id(), "Synthetic rollback", admin);
        String after = mvc.perform(get("/admin/content-release/batches/" + batch.id()).with(auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(org.jsoup.Jsoup.parse(after).select("#rollback-form")).isEmpty();
        assertThat(after).contains("ROLLED_BACK", "Synthetic rollback");
    }

    @Test void releaseBatchApisAreAdminOnly() throws Exception {
        mvc.perform(post("/api/v1/admin/content-release/batches").with(user("regular").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/content-release/batches/999999").with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test void adminPublicationActionsEnforceExplicitLifecycleAndExposeDiagnostics() throws Exception {
        ContentItem approved = new ContentItem("web-publication-"+UUID.randomUUID(),ContentType.WORD,"web-source",false);
        Word word = new Word("공개전환","こうかい","명사",null);word.addMeaning(new Meaning("ko","공개 전환",0));approved.attachWord(word);
        approved.addExample(new Example("公開する。",null,"공개한다.",0));approved.addLevel(levels.findBySystemAndCode("JLPT","N5").orElseThrow());approved.approve(false);approved=contents.saveAndFlush(approved);
        var a=user(admin.getLoginId()).roles("ADMIN");

        mvc.perform(post("/api/v1/admin/contents/"+approved.getId()+"/publish").with(a).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"initial\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
        mvc.perform(post("/api/v1/admin/contents/"+approved.getId()+"/unpublish").with(a).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"withdraw\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(false));
        mvc.perform(post("/api/v1/admin/contents/"+approved.getId()+"/republish").with(a).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
        mvc.perform(post("/api/v1/admin/contents/"+approved.getId()+"/reopen").with(a).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"content correction\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.published").value(false));
        mvc.perform(get("/api/v1/admin/publication-diagnostics").with(a))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invalidPublished").isNumber());
    }

    @Test void rendersQualityAuditAndKeepsReviewActionIdempotent() throws Exception {
        var a=user(admin.getLoginId()).roles("ADMIN");
        mvc.perform(get("/admin/contents?status=PENDING&type=WORD&keyword=관리검수&qualityIssue=true").with(a)).andExpect(status().isOk()).andExpect(content().string(containsString("관리검수")));
        mvc.perform(get("/admin/contents/"+pending.getId()).with(a)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Quality Audit")))
                .andExpect(content().string(containsString("Release Gate")))
                .andExpect(content().string(containsString("BLOCKED")))
                .andExpect(content().string(containsString("JLPT_LEVEL_MISSING")));
        mvc.perform(get("/api/v1/admin/contents?qualityIssue=true&qualitySeverity=WARNING").with(a)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(post("/api/v1/admin/contents/"+pending.getId()+"/approve").with(a).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true));
        mvc.perform(post("/api/v1/admin/contents/"+pending.getId()+"/approve").with(a).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
    }

    @Test void returnsValidationErrorWhenApiApprovalHasQualityError() throws Exception {
        ContentItem invalid=new ContentItem("web-quality-error-"+UUID.randomUUID(),ContentType.WORD,"web-source",false);
        invalid.attachWord(new Word("quality-error","クオリティ","명사",null));
        invalid=contents.save(invalid);
        mvc.perform(post("/api/v1/admin/contents/"+invalid.getId()+"/approve").with(user(admin.getLoginId()).roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error",containsString("MEANING_MISSING")));
        assertThat(contents.findById(invalid.getId()).orElseThrow().isPublished()).isFalse();
        assertThat(contents.findById(invalid.getId()).orElseThrow().getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }
}
