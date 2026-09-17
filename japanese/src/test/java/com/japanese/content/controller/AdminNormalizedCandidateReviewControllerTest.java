package com.japanese.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewHistoryRepository;
import com.japanese.content.service.NormalizedCandidateConflictAnalyzer;
import com.japanese.content.service.NormalizedCandidateStore;
import java.util.List;
import java.util.Map;
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
 * JLPT-MAX Ticket 4C: admin-only web-route security + PRG behavior for the private
 * normalized-candidate pair review UI, mirroring {@code AdminContentReviewControllerTest}'s
 * conventions ({@code @SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("sample") @Transactional},
 * {@code SecurityMockMvcRequestPostProcessors}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sample")
@Transactional
class AdminNormalizedCandidateReviewControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserAccountRepository accounts;
    @Autowired
    NormalizedCandidateStore store;
    @Autowired
    NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired
    NormalizedCandidateMatchPairRepository pairRepository;
    @Autowired
    NormalizedCandidatePairReviewRepository reviewRepository;
    @Autowired
    NormalizedCandidatePairReviewHistoryRepository historyRepository;

    UserAccount admin;
    String ref;
    NormalizedCandidateMatchPair pair;

    @BeforeEach
    void setup() {
        admin = accounts.save(new UserAccount("candidate-review-admin-" + UUID.randomUUID(), null, "hash",
                "Candidate Review Admin", UserRole.ADMIN));
        ref = "controller-test-" + UUID.randomUUID();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E2", "語", "ご", "N5", "word"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        pair = pairRepository.findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(
                NormalizedCandidateType.VOCABULARY, ref).get(0);
    }

    @Test
    void listIsAdminOnly() throws Exception {
        mvc.perform(get("/admin/normalized-candidates/reviews").param("candidateType", "VOCABULARY").param("sourceRef", ref))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/normalized-candidates/reviews").param("candidateType", "VOCABULARY").param("sourceRef", ref)
                        .with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/normalized-candidates/reviews").param("candidateType", "VOCABULARY").param("sourceRef", ref)
                        .with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NORMALIZED CANDIDATE REVIEW")));
    }

    @Test
    void detailIsAdminOnly() throws Exception {
        Long leftId = pair.getLeftCandidate().getId();
        Long rightId = pair.getRightCandidate().getId();
        String path = "/admin/normalized-candidates/reviews/VOCABULARY/" + leftId + "/" + rightId;

        mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        mvc.perform(get(path).with(user("u").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UNREVIEWED")));
    }

    @Test
    void detailForMissingCandidateIsNotFound() throws Exception {
        mvc.perform(get("/admin/normalized-candidates/reviews/VOCABULARY/999999/999998")
                        .with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void submittingADecisionRequiresCsrfAndAdminThenRedirectsWithSuccessMessage() throws Exception {
        Long leftId = pair.getLeftCandidate().getId();
        Long rightId = pair.getRightCandidate().getId();
        String path = "/admin/normalized-candidates/reviews/VOCABULARY/" + leftId + "/" + rightId + "/decision";

        mvc.perform(post(path).param("decision", "SAME_CONTENT")
                        .param("expectedPairGeneratedAt", pair.getGeneratedAt().toString())
                        .param("expectedAssessment", pair.getAssessment().name())
                        .with(user(admin.getLoginId()).roles("ADMIN")))
                .andExpect(status().isForbidden());

        mvc.perform(post(path).param("decision", "SAME_CONTENT").param("note", "동일 항목")
                        .param("expectedPairGeneratedAt", pair.getGeneratedAt().toString())
                        .param("expectedAssessment", pair.getAssessment().name())
                        .with(user(admin.getLoginId()).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("adminMessage"));

        assertThat(reviewRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId)).isPresent();
        assertThat(reviewRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId).orElseThrow().getReviewer()
                .getLoginId()).isEqualTo(admin.getLoginId());
    }

    @Test
    void submittingWithAStaleExpectedGeneratedAtIsRejectedAsAConflictNotSilentlyAccepted() throws Exception {
        Long leftId = pair.getLeftCandidate().getId();
        Long rightId = pair.getRightCandidate().getId();
        String path = "/admin/normalized-candidates/reviews/VOCABULARY/" + leftId + "/" + rightId + "/decision";

        mvc.perform(post(path).param("decision", "SAME_CONTENT")
                        .param("expectedPairGeneratedAt", "2000-01-01T00:00:00Z")
                        .param("expectedAssessment", pair.getAssessment().name())
                        .with(user(admin.getLoginId()).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("adminError"));

        assertThat(reviewRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId)).isEmpty();
    }

    @Test
    void submittingAnOldFormAfterItsPairDisappearsRedirectsWithAnErrorWithoutWritingAReview() throws Exception {
        Long leftId = pair.getLeftCandidate().getId();
        Long rightId = pair.getRightCandidate().getId();
        String path = "/admin/normalized-candidates/reviews/VOCABULARY/" + leftId + "/" + rightId + "/decision";

        store.saveVocabulary(vocab(ref, pair.getRightCandidate().getSourceNoteId(), "E9", "different", "different", "N3",
                "different word"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(pairRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId)).isEmpty();

        mvc.perform(post(path).param("decision", "SAME_CONTENT")
                        .param("expectedPairGeneratedAt", pair.getGeneratedAt().toString())
                        .param("expectedAssessment", pair.getAssessment().name())
                        .with(user(admin.getLoginId()).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("adminError"));

        assertThat(reviewRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId)).isEmpty();
        assertThat(historyRepository.findByCandidatePairOrderByReviewedAtAsc(leftId, rightId)).isEmpty();
    }

    @Test
    void reanalyzeIsAdminOnlyAndDoesNotRequireAnExistingReview() throws Exception {
        String path = "/admin/normalized-candidates/reviews/VOCABULARY/reanalyze";

        mvc.perform(post(path).param("sourceRef", ref).with(user("u").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post(path).param("sourceRef", ref).with(user(admin.getLoginId()).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("adminMessage"));
    }

    private VocabularyNormalizationResult vocab(String ref, long noteId, String entryId, String expression,
            String reading, String level, String meaning) {
        return new VocabularyNormalizationResult(
                ref, noteId, entryId, expression, reading, "noun", null,
                List.of(new NormalizedMeaning(1, meaning)),
                List.of(), new NormalizedJlptLevel(level, level, "WordJLPT"), expression, reading, Map.of(),
                List.of(), true);
    }
}
