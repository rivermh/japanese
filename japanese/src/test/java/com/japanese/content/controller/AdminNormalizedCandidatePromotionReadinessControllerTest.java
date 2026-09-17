package com.japanese.content.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.service.NormalizedCandidateConflictAnalyzer;
import com.japanese.content.service.NormalizedCandidateStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4D: admin-only web-route security for the read-only promotion-readiness planner UI,
 * mirroring {@code AdminNormalizedCandidateReviewControllerTest}'s conventions. There is no POST
 * mapping to test here at all - this controller only ever exposes GET routes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sample")
@Transactional
class AdminNormalizedCandidatePromotionReadinessControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired NormalizedCandidateMatchPairRepository pairRepository;

    UserAccount admin;
    String ref;
    NormalizedCandidateMatchPair pair;

    @BeforeEach
    void setup() {
        admin = accounts.save(new UserAccount("readiness-controller-admin-" + UUID.randomUUID(), null, "hash",
                "Readiness Admin", UserRole.ADMIN));
        ref = "readiness-controller-test-" + UUID.randomUUID();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E2", "語", "ご", "N5", "word"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        pair = pairRepository.findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(
                NormalizedCandidateType.VOCABULARY, ref).get(0);
    }

    @Test
    void listIsAdminOnly() throws Exception {
        mvc.perform(get("/admin/normalized-candidates/promotion-readiness")
                        .param("candidateType", "VOCABULARY").param("sourceRef", ref))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/normalized-candidates/promotion-readiness")
                        .param("candidateType", "VOCABULARY").param("sourceRef", ref)
                        .with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/normalized-candidates/promotion-readiness")
                        .param("candidateType", "VOCABULARY").param("sourceRef", ref)
                        .with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PROMOTION READINESS")));
    }

    @Test
    void detailIsAdminOnly() throws Exception {
        Long id = pair.getLeftCandidate().getId();
        String path = "/admin/normalized-candidates/promotion-readiness/VOCABULARY/" + id;

        mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        mvc.perform(get(path).with(user("u").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void detailForMissingCandidateIsNotFound() throws Exception {
        mvc.perform(get("/admin/normalized-candidates/promotion-readiness/VOCABULARY/999999")
                        .with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anInvalidEnumFilterValueIsRejectedAsABadRequestNotAServerError() throws Exception {
        mvc.perform(get("/admin/normalized-candidates/promotion-readiness")
                        .param("candidateType", "NOT_A_REAL_TYPE")
                        .with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/normalized-candidates/promotion-readiness")
                        .param("candidateType", "VOCABULARY").param("overallStatus", "NOT_A_REAL_STATUS")
                        .with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private VocabularyNormalizationResult vocab(String ref, long noteId, String entryId, String expression,
            String reading, String level, String meaning) {
        return new VocabularyNormalizationResult(
                ref, noteId, entryId, expression, reading, "noun", null,
                List.of(new NormalizedMeaning(1, meaning)),
                List.of(), new NormalizedJlptLevel(level, level, "WordJLPT"), expression, reading, java.util.Map.of(),
                List.of(), true);
    }
}
