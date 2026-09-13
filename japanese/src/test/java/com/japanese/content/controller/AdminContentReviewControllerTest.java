package com.japanese.content.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.japanese.account.entity.*;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.entity.*;
import com.japanese.content.repository.ContentItemRepository;
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
    UserAccount admin;
    ContentItem pending;

    @BeforeEach void setup() {
        admin=accounts.save(new UserAccount("web-admin-"+UUID.randomUUID(),null,"hash","Admin",UserRole.ADMIN));
        pending=new ContentItem("web-pending-"+UUID.randomUUID(),ContentType.WORD,"web-source",false);
        Word word=new Word("관리검수","かんり","명사",null); word.addMeaning(new Meaning("ko","관리 검수",0)); pending.attachWord(word); pending=contents.save(pending);
    }

    @Test void protectsWebAndApiByRoleAndCsrf() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin").with(user("u").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin").with(user(admin.getLoginId()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("ADMIN CONTENT REVIEW")));
        mvc.perform(post("/api/v1/admin/contents/"+pending.getId()+"/approve").with(user(admin.getLoginId()).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test void rendersQualityAuditAndKeepsReviewActionIdempotent() throws Exception {
        var a=user(admin.getLoginId()).roles("ADMIN");
        mvc.perform(get("/admin/contents?status=PENDING&type=WORD&keyword=관리검수&qualityIssue=true").with(a)).andExpect(status().isOk()).andExpect(content().string(containsString("관리검수")));
        mvc.perform(get("/admin/contents/"+pending.getId()).with(a)).andExpect(status().isOk()).andExpect(content().string(containsString("Quality Audit")));
        mvc.perform(get("/api/v1/admin/contents?qualityIssue=true&qualitySeverity=WARNING").with(a)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(post("/api/v1/admin/contents/"+pending.getId()+"/approve").with(a).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true));
        mvc.perform(post("/api/v1/admin/contents/"+pending.getId()+"/approve").with(a).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
    }
}
