package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.DecisionSubmission;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.PairFreshness;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.ReviewDetailView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.ReviewFreshness;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.NormalizedCandidateMatchAssessment;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.GrammarComparisonRepository;
import com.japanese.content.repository.GrammarEnrichmentRepository;
import com.japanese.content.repository.GrammarRelationRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewHistoryRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4C: synthetic scenario coverage for {@link NormalizedCandidatePairReviewService},
 * lettered to match the ticket's own test list (A-N). Every candidate is built through
 * {@code NormalizedCandidateStore} (Ticket 4A) and every pair through
 * {@link NormalizedCandidateConflictAnalyzer} (Ticket 4B) - this class never constructs either by
 * any other means, matching {@code NormalizedCandidateConflictAnalyzerTest}'s own convention.
 */
@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class NormalizedCandidatePairReviewServiceTest {

    @Autowired
    NormalizedCandidateStore store;
    @Autowired
    NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired
    NormalizedCandidatePairReviewService reviewService;
    @Autowired
    NormalizedContentCandidateRepository candidateRepository;
    @Autowired
    NormalizedCandidateMatchPairRepository pairRepository;
    @Autowired
    NormalizedCandidatePairReviewRepository reviewRepository;
    @Autowired
    NormalizedCandidatePairReviewHistoryRepository historyRepository;
    @Autowired
    UserAccountRepository accounts;
    @Autowired
    ContentItemRepository contentItems;
    @Autowired
    GrammarEnrichmentRepository grammarEnrichments;
    @Autowired
    GrammarRelationRepository grammarRelations;
    @Autowired
    GrammarComparisonRepository grammarComparisons;
    @Autowired
    ImportedSourceRecordRepository importedSourceRecords;
    @Autowired
    JdbcClient jdbcClient;

    private String ref() {
        return "pair-review-test-" + UUID.randomUUID();
    }

    private UserAccount admin(String suffix) {
        return accounts.save(new UserAccount("review-admin-" + suffix + "-" + UUID.randomUUID(), null, "hash",
                "Admin " + suffix, UserRole.ADMIN));
    }

    @Test
    void a_unreviewedPairCanRecordAFirstDecision() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);
        UserAccount reviewer = admin("a");

        var view = reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, "동일 항목으로 보임"), reviewer);

        assertThat(view.decision()).isEqualTo(HumanReviewDecision.SAME_CONTENT);
        assertThat(view.reviewerDisplayName()).isEqualTo(reviewer.getDisplayName());
        assertThat(reviewRepository.findByLeftCandidateIdAndRightCandidateId(
                pair.getLeftCandidate().getId(), pair.getRightCandidate().getId())).isPresent();
    }

    @Test
    void b_firstDecisionAppendsExactlyOneHistoryRow() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, null), admin("b"));

        List<?> history = historyRepository.findByCandidatePairOrderByReviewedAtAsc(
                pair.getLeftCandidate().getId(), pair.getRightCandidate().getId());
        assertThat(history).hasSize(1);
    }

    @Test
    void c_reReviewChangesCurrentAndAppendsHistory() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);
        Long leftId = pair.getLeftCandidate().getId();
        Long rightId = pair.getRightCandidate().getId();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.NEEDS_FOLLOWUP, "확인 필요"), admin("c1"));
        var updated = reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submissionWithVersion(pair, HumanReviewDecision.SAME_CONTENT, "확인 완료 - 동일 항목",
                        currentVersion(leftId, rightId)),
                admin("c2"));

        assertThat(updated.decision()).isEqualTo(HumanReviewDecision.SAME_CONTENT);
        List<?> history = historyRepository.findByCandidatePairOrderByReviewedAtAsc(leftId, rightId);
        assertThat(history).hasSize(2);
        assertThat(reviewRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId).orElseThrow()
                .getDecision()).isEqualTo(HumanReviewDecision.SAME_CONTENT);
    }

    @Test
    void d_resubmittingTheSameDecisionAndNoteIsANoOp() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);
        Long leftId = pair.getLeftCandidate().getId();
        Long rightId = pair.getRightCandidate().getId();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, "동일"), admin("d1"));
        long versionAfterFirst = currentVersion(leftId, rightId);
        Instant reviewedAtAfterFirst = reviewRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId)
                .orElseThrow().getReviewedAt();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submissionWithVersion(pair, HumanReviewDecision.SAME_CONTENT, "동일", versionAfterFirst), admin("d2"));

        NormalizedCandidatePairReview current = reviewRepository
                .findByLeftCandidateIdAndRightCandidateId(leftId, rightId).orElseThrow();
        assertThat(current.getVersion()).isEqualTo(versionAfterFirst);
        assertThat(current.getReviewedAt()).isEqualTo(reviewedAtAfterFirst);
        assertThat(historyRepository.findByCandidatePairOrderByReviewedAtAsc(leftId, rightId)).hasSize(1);
        assertThat(reviewService.detail(NormalizedCandidateType.VOCABULARY, leftId, rightId)
                .currentReview().freshness()).isEqualTo(ReviewFreshness.FRESH);
    }

    @Test
    void e_staleSameDecisionAndNoteReaffirmsTheCurrentCandidateSnapshot() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair firstPair = onlyPair(ref);
        Long leftId = firstPair.getLeftCandidate().getId();
        Long rightId = firstPair.getRightCandidate().getId();
        long leftNoteId = firstPair.getLeftCandidate().getSourceNoteId();
        var leftDetail = firstPair.getLeftCandidate().getVocabularyDetail();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(firstPair, HumanReviewDecision.SAME_CONTENT, null), admin("e1"));
        NormalizedCandidatePairReview firstReview = reviewRepository
                .findByLeftCandidateIdAndRightCandidateId(leftId, rightId).orElseThrow();
        long firstVersion = firstReview.getVersion();

        store.saveVocabulary(vocab(ref, leftNoteId, leftDetail.getEntryId(), leftDetail.getExpression(),
                leftDetail.getReading(), leftDetail.getLevelCode(), "word (refreshed)"));
        assertThat(reviewService.detail(NormalizedCandidateType.VOCABULARY, leftId, rightId)
                .currentReview().freshness()).isEqualTo(ReviewFreshness.STALE);

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        NormalizedCandidateMatchPair refreshedPair = onlyPair(ref);
        assertThat(reviewService.detail(NormalizedCandidateType.VOCABULARY, leftId, rightId)
                .pairFreshness()).isEqualTo(PairFreshness.FRESH);

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submissionWithVersion(refreshedPair, HumanReviewDecision.SAME_CONTENT, null, firstVersion), admin("e2"));

        NormalizedCandidatePairReview reaffirmed = reviewRepository
                .findByLeftCandidateIdAndRightCandidateId(leftId, rightId).orElseThrow();
        assertThat(reaffirmed.getVersion()).isEqualTo(firstVersion + 1);
        assertThat(reaffirmed.getLeftNormalizedAtSnapshot())
                .isEqualTo(refreshedPair.getLeftCandidate().getNormalizedAt());
        assertThat(reaffirmed.getRightNormalizedAtSnapshot())
                .isEqualTo(refreshedPair.getRightCandidate().getNormalizedAt());
        assertThat(reaffirmed.getAssessmentSnapshot()).isEqualTo(refreshedPair.getAssessment());
        assertThat(reviewService.detail(NormalizedCandidateType.VOCABULARY, leftId, rightId)
                .currentReview().freshness()).isEqualTo(ReviewFreshness.FRESH);
        var history = historyRepository.findByCandidatePairOrderByReviewedAtAsc(leftId, rightId);
        assertThat(history).hasSize(2);
        assertThat(history.get(1).getPreviousDecision()).isEqualTo(HumanReviewDecision.SAME_CONTENT);
        assertThat(history.get(1).getNewDecision()).isEqualTo(HumanReviewDecision.SAME_CONTENT);
    }

    @Test
    void noteLengthIsValidatedBeforeAnyReviewOrHistoryWrite() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);
        Long leftId = pair.getLeftCandidate().getId();
        Long rightId = pair.getRightCandidate().getId();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, "x".repeat(2000)), admin("note-ok"));
        NormalizedCandidatePairReview accepted = reviewRepository
                .findByLeftCandidateIdAndRightCandidateId(leftId, rightId).orElseThrow();
        long acceptedVersion = accepted.getVersion();
        Instant acceptedReviewedAt = accepted.getReviewedAt();

        assertThatThrownBy(() -> reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submissionWithVersion(pair, HumanReviewDecision.DISTINCT_CONTENT, "x".repeat(2001), acceptedVersion),
                admin("note-too-long"))).isInstanceOf(IllegalArgumentException.class);

        NormalizedCandidatePairReview unchanged = reviewRepository
                .findByLeftCandidateIdAndRightCandidateId(leftId, rightId).orElseThrow();
        assertThat(unchanged.getVersion()).isEqualTo(acceptedVersion);
        assertThat(unchanged.getReviewedAt()).isEqualTo(acceptedReviewedAt);
        assertThat(unchanged.getDecision()).isEqualTo(HumanReviewDecision.SAME_CONTENT);
        assertThat(historyRepository.findByCandidatePairOrderByReviewedAtAsc(leftId, rightId)).hasSize(1);
    }

    @Test
    void f_reviewingAnArbitraryNonExistingPairIsRejected() {
        NormalizedContentCandidate orphanA = candidateRepository.save(
                new NormalizedContentCandidate(NormalizedCandidateType.VOCABULARY, ref(), 1L, null,
                        com.japanese.content.entity.NormalizedCandidateQualityState.CLEAN, Instant.now()));
        NormalizedContentCandidate orphanB = candidateRepository.save(
                new NormalizedContentCandidate(NormalizedCandidateType.VOCABULARY, orphanA.getSourceRef(), 2L, null,
                        com.japanese.content.entity.NormalizedCandidateQualityState.CLEAN, Instant.now()));

        assertThatExceptionOfType(NormalizedCandidatePairReviewConflictException.class).isThrownBy(() ->
                reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                        new DecisionSubmission(orphanA.getId(), orphanB.getId(), HumanReviewDecision.SAME_CONTENT, null,
                                Instant.now(), NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE, null),
                        admin("f")));
    }

    @Test
    void g_crossSourceRefPairIsRejected() {
        String refA = ref();
        String refB = ref();
        store.saveVocabulary(vocab(refA, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(refB, 1L, "E1", "語", "ご", "N5", "word"));
        NormalizedContentCandidate a = candidateRepository.findByCandidateTypeAndSourceRef(
                NormalizedCandidateType.VOCABULARY, refA).get(0);
        NormalizedContentCandidate b = candidateRepository.findByCandidateTypeAndSourceRef(
                NormalizedCandidateType.VOCABULARY, refB).get(0);

        assertThatThrownBy(() -> reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                new DecisionSubmission(a.getId(), b.getId(), HumanReviewDecision.SAME_CONTENT, null, Instant.now(),
                        NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE, null), admin("g")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void h_staleCandidateAfterPairGeneratedRejectsNewDecision() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);
        NormalizedContentCandidate left = pair.getLeftCandidate();

        // Refresh the left candidate (bumps normalizedAt past the already-generated pair's
        // generatedAt) without reanalyzing - the pair itself is now stale.
        store.saveVocabulary(vocab(ref, left.getSourceNoteId(), "E1", "語", "ご", "N5", "word (edited)"));

        assertThatExceptionOfType(NormalizedCandidatePairReviewConflictException.class).isThrownBy(() ->
                reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                        submission(pair, HumanReviewDecision.SAME_CONTENT, null), admin("h")));
    }

    @Test
    void i_candidateRefreshMakesAnExistingReviewStale() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);
        Long leftId = pair.getLeftCandidate().getId();
        Long rightId = pair.getRightCandidate().getId();
        long noteId = pair.getLeftCandidate().getSourceNoteId();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, null), admin("i"));
        assertThat(reviewService.detail(NormalizedCandidateType.VOCABULARY, leftId, rightId).currentReview().freshness())
                .isEqualTo(ReviewFreshness.FRESH);

        store.saveVocabulary(vocab(ref, noteId, "E1", "語", "ご", "N5", "word (edited)"));

        ReviewDetailView afterRefresh = reviewService.detail(NormalizedCandidateType.VOCABULARY, leftId, rightId);
        assertThat(afterRefresh.pairFreshness()).isEqualTo(PairFreshness.STALE);
        assertThat(afterRefresh.currentReview().freshness()).isEqualTo(ReviewFreshness.STALE);
    }

    @Test
    void j_unchangedReanalysisKeepsAnExistingReviewFreshDespitePairIdChanging() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair firstPair = onlyPair(ref);
        Long leftId = firstPair.getLeftCandidate().getId();
        Long rightId = firstPair.getRightCandidate().getId();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(firstPair, HumanReviewDecision.SAME_CONTENT, null), admin("j"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        NormalizedCandidateMatchPair secondPair = onlyPair(ref);
        assertThat(secondPair.getId()).isNotEqualTo(firstPair.getId());

        ReviewDetailView detail = reviewService.detail(NormalizedCandidateType.VOCABULARY, leftId, rightId);
        assertThat(detail.pairFreshness()).isEqualTo(PairFreshness.FRESH);
        assertThat(detail.currentReview().freshness()).isEqualTo(ReviewFreshness.FRESH);

        // Fresh review over an unchanged reanalysis can still be re-reviewed using the new pair's
        // generatedAt/assessment as the expected values.
        var again = reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submissionWithVersion(secondPair, HumanReviewDecision.SAME_CONTENT, null,
                        currentVersion(leftId, rightId)),
                admin("j2"));
        assertThat(again.decision()).isEqualTo(HumanReviewDecision.SAME_CONTENT);
    }

    @Test
    void l_pairDisappearingPreservesReviewAndHistoryWithoutCrashing() {
        String ref = ref();
        long noteA = 1L;
        long noteB = 2L;
        store.saveVocabulary(vocab(ref, noteA, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, noteB, "E1", "語", "ご", "N5", "word"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);
        Long leftId = pair.getLeftCandidate().getId();
        Long rightId = pair.getRightCandidate().getId();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, "동일"), admin("l"));

        // Refresh one side so the two candidates no longer share an identity/expression+reading key
        // - the next analyze() run will find no relationship between them at all.
        store.saveVocabulary(vocab(ref, noteB, "E9", "別物", "べつ", "N3", "different word"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        assertThat(pairRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId)).isEmpty();

        ReviewDetailView detail = reviewService.detail(NormalizedCandidateType.VOCABULARY, leftId, rightId);
        assertThat(detail.analysisPresent()).isFalse();
        assertThat(detail.currentReview()).isNotNull();
        assertThat(detail.currentReview().freshness()).isEqualTo(ReviewFreshness.ANALYSIS_NO_LONGER_PRESENT);
        assertThat(historyRepository.findByCandidatePairOrderByReviewedAtAsc(leftId, rightId)).hasSize(1);
    }

    @Test
    void n_concurrentReReviewWithAStaleVersionIsRejected() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);
        Long leftId = pair.getLeftCandidate().getId();
        Long rightId = pair.getRightCandidate().getId();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.NEEDS_FOLLOWUP, null), admin("n1"));
        long staleVersion = currentVersion(leftId, rightId);

        // A second admin re-reviews using the version they saw.
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submissionWithVersion(pair, HumanReviewDecision.SAME_CONTENT, "확인", staleVersion), admin("n2"));

        // The first admin's browser tab still only knows the old (now-stale) version.
        assertThatExceptionOfType(NormalizedCandidatePairReviewConflictException.class).isThrownBy(() ->
                reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                        submissionWithVersion(pair, HumanReviewDecision.DISTINCT_CONTENT, "아니오", staleVersion),
                        admin("n3")));
    }

    @Test
    void m_reviewOperationsNeverChangeProductionOrImportedSourceRowCounts() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);
        Map<String, Long> before = productionCounts();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, "동일"), admin("m1"));
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submissionWithVersion(pair, HumanReviewDecision.DISTINCT_CONTENT, "다시 생각해보니 다름",
                        currentVersion(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId())),
                admin("m2"));
        reviewService.reanalyze(NormalizedCandidateType.VOCABULARY, ref);

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void reviewOperationsKeepEveryProductionTableCountUnchangedAtEachStep() {
        String ref = ref();
        seedDuplicatePair(ref);
        NormalizedCandidateMatchPair pair = onlyPair(ref);
        Map<String, Long> before = productionCounts();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, null), admin("isolation-first"));
        assertThat(productionCounts()).isEqualTo(before);

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submissionWithVersion(pair, HumanReviewDecision.DISTINCT_CONTENT, null,
                        currentVersion(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId())),
                admin("isolation-rereview"));
        assertThat(productionCounts()).isEqualTo(before);

        reviewService.reanalyze(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // Helpers
    // ===================================================================================

    private Map<String, Long> productionCounts() {
        return Map.of(
                "contentItems", contentItems.count(),
                "words", jdbcClient.sql("select count(*) from words").query(Long.class).single(),
                "grammars", jdbcClient.sql("select count(*) from grammars").query(Long.class).single(),
                "grammarEnrichments", grammarEnrichments.count(),
                "grammarRelations", grammarRelations.count(),
                "grammarComparisons", grammarComparisons.count(),
                "importedSourceRecords", importedSourceRecords.count());
    }

    private void seedDuplicatePair(String ref) {
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E2", "語", "ご", "N5", "word"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
    }

    private NormalizedCandidateMatchPair onlyPair(String ref) {
        List<NormalizedCandidateMatchPair> pairs = pairRepository
                .findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(pairs).hasSize(1);
        return pairs.get(0);
    }

    private long currentVersion(Long leftId, Long rightId) {
        return reviewRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId).orElseThrow().getVersion();
    }

    private DecisionSubmission submission(NormalizedCandidateMatchPair pair, HumanReviewDecision decision, String note) {
        return new DecisionSubmission(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId(), decision, note,
                pair.getGeneratedAt(), pair.getAssessment(), null);
    }

    private DecisionSubmission submissionWithVersion(NormalizedCandidateMatchPair pair, HumanReviewDecision decision,
            String note, long expectedVersion) {
        return new DecisionSubmission(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId(), decision, note,
                pair.getGeneratedAt(), pair.getAssessment(), expectedVersion);
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
