package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.DecisionSubmission;
import com.japanese.content.dto.PromotionReadinessModels.ExistingProductionLinkStatus;
import com.japanese.content.dto.PromotionReadinessModels.MappingStatus;
import com.japanese.content.dto.PromotionReadinessModels.OverallStatus;
import com.japanese.content.dto.PromotionReadinessModels.PairResolutionStatus;
import com.japanese.content.dto.PromotionReadinessModels.PromotionReadinessDetailView;
import com.japanese.content.dto.PromotionReadinessModels.PromotionReadinessIssue;
import com.japanese.content.dto.PromotionReadinessModels.ReadinessSummary;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.importer.GrammarNormalizationResult;
import com.japanese.content.importer.NormalizedGrammarExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.NormalizedPitchAccent;
import com.japanese.content.importer.VocabularyNormalizationIssue;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.importer.VocabularyNormalizationWarning;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentReleaseBatchItemRepository;
import com.japanese.content.repository.ContentReleaseBatchRepository;
import com.japanese.content.repository.ContentReviewHistoryRepository;
import com.japanese.content.repository.ContentSourceRepository;
import com.japanese.content.repository.GrammarComparisonRepository;
import com.japanese.content.repository.GrammarEnrichmentRepository;
import com.japanese.content.repository.GrammarRelationRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewHistoryRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
 * JLPT-MAX Ticket 4D: synthetic scenario coverage for {@link NormalizedCandidatePromotionReadinessService},
 * lettered to match the ticket's own test list. Every candidate is built through
 * {@code NormalizedCandidateStore} (Ticket 4A), every pair through
 * {@link NormalizedCandidateConflictAnalyzer} (Ticket 4B), and every human decision through
 * {@link NormalizedCandidatePairReviewService} (Ticket 4C) - this class never constructs any of them
 * by any other means, matching the existing Ticket 4B/4C test conventions.
 */
@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class NormalizedCandidatePromotionReadinessServiceTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired NormalizedCandidatePairReviewService reviewService;
    @Autowired NormalizedCandidatePromotionReadinessService readiness;
    @Autowired NormalizedContentCandidateRepository candidateRepository;
    @Autowired NormalizedCandidateMatchPairRepository pairRepository;
    @Autowired NormalizedCandidatePairReviewRepository reviewRepository;
    @Autowired NormalizedCandidatePairReviewHistoryRepository historyRepository;
    @Autowired UserAccountRepository accounts;
    @Autowired ContentSourceRepository contentSources;
    @Autowired ContentItemRepository contentItems;
    @Autowired ImportedSourceRecordRepository importedSourceRecords;
    @Autowired LevelRepository levels;
    @Autowired GrammarEnrichmentRepository grammarEnrichments;
    @Autowired GrammarRelationRepository grammarRelations;
    @Autowired GrammarComparisonRepository grammarComparisons;
    @Autowired ContentReviewHistoryRepository contentReviewHistory;
    @Autowired ContentReleaseBatchRepository releaseBatches;
    @Autowired ContentReleaseBatchItemRepository releaseBatchItems;
    @Autowired JdbcClient jdbcClient;

    /** Matches {@code ApkgVocabularyImporter.SOURCE_REF} / {@code NormalizedVocabularyMeaningLanguagePolicy}. */
    private static final String CANONICAL_JLPT_MAX_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";

    private String ref() {
        return "promotion-readiness-test-" + UUID.randomUUID();
    }

    private UserAccount admin(String suffix) {
        return accounts.save(new UserAccount("readiness-admin-" + suffix + "-" + UUID.randomUUID(), null, "hash",
                "Admin " + suffix, UserRole.ADMIN));
    }

    /**
     * Idempotent by design: {@code SampleContentDataLoader} ("sample" profile) already commits a
     * single {@code Level(JLPT, N5)} row once at application startup, shared across this whole test
     * JVM run. An unconditional {@code levels.save(new Level(...))} here would insert a second N5 row
     * inside every test's own transaction - exactly the (system, code) duplicate scenario Level
     * mapping must treat as unmappable, which would make every "clean" test in this class fail.
     */
    private void seedN5Level() {
        levels.findBySystemAndCode("JLPT", "N5").orElseGet(() -> levels.save(new Level("JLPT", "N5", "JLPT N5")));
    }

    // ===================================================================================
    // A-D: normalization quality
    // ===================================================================================

    @Test
    void a_cleanCandidatePassesTheQualityAxis() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidate.getId()).result();

        assertThat(result.qualityState()).isEqualTo(com.japanese.content.entity.NormalizedCandidateQualityState.CLEAN);
        assertThat(codesOf(result)).doesNotContain(PromotionReadinessIssueCode.NORMALIZATION_FATAL,
                PromotionReadinessIssueCode.NORMALIZATION_REVIEW_REQUIRED);
    }

    @Test
    void b_informationalCandidatePassesTheQualityAxis() {
        String ref = ref();
        seedN5Level();
        VocabularyNormalizationResult base = vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning");
        store.saveVocabulary(withWarnings(base, new VocabularyNormalizationWarning(
                VocabularyNormalizationIssue.UNKNOWN_EXTRA_FIELD, "informational only")));
        NormalizedContentCandidate candidate = onlyCandidate(ref);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidate.getId()).result();

        assertThat(result.qualityState()).isEqualTo(com.japanese.content.entity.NormalizedCandidateQualityState.INFORMATIONAL);
        assertThat(codesOf(result)).doesNotContain(PromotionReadinessIssueCode.NORMALIZATION_FATAL,
                PromotionReadinessIssueCode.NORMALIZATION_REVIEW_REQUIRED);
    }

    @Test
    void c_reviewRequiredCandidateIsBlockedOnTheQualityAxis() {
        String ref = ref();
        seedN5Level();
        VocabularyNormalizationResult base = vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning");
        store.saveVocabulary(withWarnings(base, new VocabularyNormalizationWarning(
                VocabularyNormalizationIssue.EMPTY_PART_OF_SPEECH, "review required")));
        NormalizedContentCandidate candidate = onlyCandidate(ref);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidate.getId()).result();

        assertThat(result.qualityState()).isEqualTo(com.japanese.content.entity.NormalizedCandidateQualityState.REVIEW_REQUIRED);
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.NORMALIZATION_REVIEW_REQUIRED);
        assertThat(result.overallStatus()).isEqualTo(OverallStatus.BLOCKED);
    }

    @Test
    void d_fatalCandidateIsBlockedOnTheQualityAxis() {
        String ref = ref();
        seedN5Level();
        VocabularyNormalizationResult base = vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning");
        store.saveVocabulary(withWarnings(base, new VocabularyNormalizationWarning(
                VocabularyNormalizationIssue.MISSING_ENTRY_ID, "fatal")));
        NormalizedContentCandidate candidate = onlyCandidate(ref);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidate.getId()).result();

        assertThat(result.qualityState()).isEqualTo(com.japanese.content.entity.NormalizedCandidateQualityState.FATAL);
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.NORMALIZATION_FATAL);
    }

    // ===================================================================================
    // E-L: pair/dedup review resolution
    // ===================================================================================

    @Test
    void e_candidateWithNoCurrentPairIsResolvedOnThePairAxis() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidate.getId()).result();

        assertThat(result.pairResolutionStatus()).isEqualTo(PairResolutionStatus.RESOLVED);
    }

    @Test
    void f_currentPairWithNoHumanReviewIsBlocked() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, pair.getLeftCandidate().getId()).result();

        assertThat(result.pairResolutionStatus()).isEqualTo(PairResolutionStatus.BLOCKED);
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.PAIR_UNREVIEWED);
    }

    @Test
    void g_currentPairWithAStaleHumanReviewIsBlocked() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        Long leftId = pair.getLeftCandidate().getId();
        long leftNoteId = pair.getLeftCandidate().getSourceNoteId();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.DISTINCT_CONTENT, null), admin("g"));
        store.saveVocabulary(vocab(ref, leftNoteId, "E1", "語", "ご", "noun (edited)", "N5", "meaning"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, leftId).result();

        assertThat(result.pairResolutionStatus()).isEqualTo(PairResolutionStatus.BLOCKED);
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.PAIR_REVIEW_STALE);
    }

    @Test
    void g2_pairAnalysisItselfBeingStaleIsItsOwnBlocker() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        Long leftId = pair.getLeftCandidate().getId();
        long leftNoteId = pair.getLeftCandidate().getSourceNoteId();

        // Refresh without reanalyzing: the pair's own generatedAt now predates the candidate's
        // normalizedAt, so the machine analysis itself is stale.
        store.saveVocabulary(vocab(ref, leftNoteId, "E1", "語", "ご", "noun (edited)", "N5", "meaning"));

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, leftId).result();

        assertThat(result.pairResolutionStatus()).isEqualTo(PairResolutionStatus.BLOCKED);
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.PAIR_ANALYSIS_STALE);
    }

    @Test
    void h_needsFollowupPairIsBlocked() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.NEEDS_FOLLOWUP, null), admin("h"));

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, pair.getLeftCandidate().getId()).result();

        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.PAIR_NEEDS_FOLLOWUP);
    }

    @Test
    void i_distinctContentFreshPairIsResolved() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.DISTINCT_CONTENT, null), admin("i"));

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, pair.getLeftCandidate().getId()).result();

        assertThat(result.pairResolutionStatus()).isEqualTo(PairResolutionStatus.RESOLVED);
    }

    @Test
    void j_sameContentFreshPairRequiresCanonicalSelection() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, null), admin("j"));

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, pair.getLeftCandidate().getId()).result();

        assertThat(result.pairResolutionStatus()).isEqualTo(PairResolutionStatus.BLOCKED);
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.SAME_CONTENT_CANONICAL_SELECTION_REQUIRED);
    }

    @Test
    void k_candidateWithOneDistinctAndOneUnreviewedPairIsBlocked() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        store.saveVocabulary(vocab(ref, 2L, "E2", "語", "ご", "noun", "N5", "meaning"));
        store.saveVocabulary(vocab(ref, 3L, "E3", "語", "ご", "noun", "N5", "meaning"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        List<NormalizedContentCandidate> all = candidateRepository
                .findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, ref);
        NormalizedContentCandidate c = all.stream().filter(x -> x.getSourceNoteId() == 1L).findFirst().orElseThrow();
        NormalizedContentCandidate d = all.stream().filter(x -> x.getSourceNoteId() == 2L).findFirst().orElseThrow();
        NormalizedContentCandidate e = all.stream().filter(x -> x.getSourceNoteId() == 3L).findFirst().orElseThrow();

        NormalizedCandidateMatchPair cdPair = pairInvolving(ref, c.getId(), d.getId());
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(cdPair, HumanReviewDecision.DISTINCT_CONTENT, null), admin("k"));
        // c-e pair intentionally left unreviewed.

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, c.getId()).result();

        assertThat(result.pairResolutionStatus()).isEqualTo(PairResolutionStatus.BLOCKED);
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.PAIR_UNREVIEWED);
    }

    @Test
    void l_pairDisappearingLeavesThePairAxisResolvedDespitePastHistory() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        Long leftId = pair.getLeftCandidate().getId();
        long rightNoteId = pair.getRightCandidate().getSourceNoteId();
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, "동일"), admin("l"));

        store.saveVocabulary(vocab(ref, rightNoteId, "E9", "別物", "べつ", "noun", "N5", "different"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(pairRepository.findByLeftCandidateIdAndRightCandidateId(leftId, pair.getRightCandidate().getId()))
                .isEmpty();

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, leftId).result();

        assertThat(result.pairResolutionStatus()).isEqualTo(PairResolutionStatus.RESOLVED);
        assertThat(historyRepository.findByCandidatePairOrderByReviewedAtAsc(leftId, pair.getRightCandidate().getId()))
                .hasSize(1);
    }

    // ===================================================================================
    // M-S: Vocabulary mapping
    // ===================================================================================

    @Test
    void m_expressionExactlyAtTheProductionMaxIsNotTooLong() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "x".repeat(120), "ご", "noun", "N5", "meaning"));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).doesNotContain(PromotionReadinessIssueCode.VOCAB_EXPRESSION_TOO_LONG);
    }

    @Test
    void n_expressionOneOverTheProductionMaxIsTooLong() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "x".repeat(121), "ご", "noun", "N5", "meaning"));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.VOCAB_EXPRESSION_TOO_LONG);
        assertThat(result.mappingStatus()).isEqualTo(MappingStatus.BLOCKED);
    }

    @Test
    void o_readingOneOverTheProductionMaxIsTooLong() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "x".repeat(121), "noun", "N5", "meaning"));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.VOCAB_READING_TOO_LONG);
    }

    @Test
    void p_partOfSpeechOneOverTheProductionMaxIsTooLong() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "x".repeat(121), "N5", "meaning"));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.VOCAB_PART_OF_SPEECH_TOO_LONG);
    }

    @Test
    void q_missingMeaningIsBlocked() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(new VocabularyNormalizationResult(ref, 1L, "E1", "語", "ご", "noun", null,
                List.of(), List.of(), new NormalizedJlptLevel("N5", "N5", "WordJLPT"), "語", "ご", Map.of(),
                List.of(new VocabularyNormalizationWarning(VocabularyNormalizationIssue.MISSING_MEANING, "no meaning")),
                false));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.VOCAB_MEANING_MISSING);
    }

    @Test
    void r_unmappableJlptLevelIsBlocked() {
        String ref = ref();
        // Deliberately not N5: SampleContentDataLoader ("sample" profile) already seeds a
        // Level(JLPT, N5) row once at application startup, shared/committed across this whole test
        // JVM run - N9 is never seeded by anything, so it reliably stays unmappable.
        store.saveVocabulary(new VocabularyNormalizationResult(ref, 1L, "E1", "語", "ご", "noun", null,
                List.of(new NormalizedMeaning(1, "meaning")), List.of(),
                new NormalizedJlptLevel("N9", "N9", "WordJLPT"), "語", "ご", Map.of(), List.of(), true));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.JLPT_LEVEL_UNMAPPABLE);
    }

    @Test
    void r2_exactlyOneMatchingProductionLevelRowMakesTheLevelMappable() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).doesNotContain(PromotionReadinessIssueCode.JLPT_LEVEL_UNMAPPABLE);
    }

    @Test
    void r3_duplicateProductionLevelRowsForTheSameCodeMakeTheLevelUnmappable() {
        String ref = ref();
        // levels 테이블에는 (system, code) unique constraint가 없다 - 두 row가 동시에 존재하는 상태를
        // 직접 재현해, 어느 한쪽을 임의로 골라 매핑하지 않고 unmappable로 처리되는지 검증한다. N4는
        // SampleContentDataLoader가 seed하지 않는 code라서 이 테스트 트랜잭션 안에서 정확히 두 개의
        // row만 존재함이 보장된다.
        levels.save(new Level("JLPT", "N4", "JLPT N4"));
        levels.save(new Level("JLPT", "N4", "JLPT N4 (duplicate)"));
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N4", "meaning"));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.JLPT_LEVEL_UNMAPPABLE);
    }

    @Test
    void s_pitchAccentSerializationOverflowIsBlocked() {
        String ref = ref();
        seedN5Level();
        VocabularyNormalizationResult base = vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning");
        VocabularyNormalizationResult withPitch = new VocabularyNormalizationResult(base.sourceRef(), base.sourceNoteId(),
                base.entryId(), base.expression(), base.reading(), base.partOfSpeech(),
                new NormalizedPitchAccent("t".repeat(250), "m".repeat(250)), base.meanings(), base.examples(),
                base.level(), base.normalizedSearchExpression(), base.normalizedSearchReading(),
                base.preservedExtraFields(), base.warnings(), base.validForPromotion());
        store.saveVocabulary(withPitch);
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.VOCAB_PITCH_ACCENT_TOO_LONG);
    }

    // ===================================================================================
    // T-W: Grammar mapping
    // ===================================================================================

    @Test
    void t_grammarPatternOneOverTheProductionMaxIsTooLong() {
        String ref = ref();
        seedN5Level();
        store.saveGrammar(grammar(ref, 1L, "U1", "x".repeat(201), "y", "N5"));
        var result = readiness.detail(NormalizedCandidateType.GRAMMAR, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.GRAMMAR_PATTERN_TOO_LONG);
    }

    @Test
    void u_grammarConnectionOneOverTheProductionMaxIsTooLong() {
        String ref = ref();
        seedN5Level();
        store.saveGrammar(grammar(ref, 1L, "U1", "pattern", "c".repeat(501), "N5"));
        var result = readiness.detail(NormalizedCandidateType.GRAMMAR, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.GRAMMAR_CONNECTION_TOO_LONG);
    }

    @Test
    void v_grammarExplanationMappingIsAlwaysUnresolved() {
        String ref = ref();
        seedN5Level();
        store.saveGrammar(grammar(ref, 1L, "U1", "pattern", "connection", "N5"));
        var detail = readiness.detail(NormalizedCandidateType.GRAMMAR, onlyCandidate(ref).getId());

        assertThat(codesOf(detail.result())).contains(PromotionReadinessIssueCode.GRAMMAR_MAPPING_POLICY_UNRESOLVED);
        assertThat(detail.result().mappingStatus()).isEqualTo(MappingStatus.BLOCKED);
        assertThat(detail.mappingPreview().grammar().explanationMappingUnresolved()).isTrue();
    }

    @Test
    void w_frontExampleAndConfusablePatternsAreNeverAutoMappedIntoCuratedProductionEntities() {
        String ref = ref();
        seedN5Level();
        store.saveGrammar(grammar(ref, 1L, "U1", "pattern", "connection", "N5"));

        readiness.detail(NormalizedCandidateType.GRAMMAR, onlyCandidate(ref).getId());

        assertThat(grammarEnrichments.count()).isZero();
        assertThat(grammarRelations.count()).isZero();
        assertThat(grammarComparisons.count()).isZero();
    }

    // ===================================================================================
    // X-Z: production identity/slug (Ticket 4E-0: ProductionContentSlugPolicy resolves this axis
    // for both candidate types - it never merges/combines candidates into one identity, and never
    // derives a slug from expression/reading/pattern; see ProductionContentSlugPolicyTest)
    // ===================================================================================

    @Test
    void x_sameExpressionAndReadingCandidatesAreStillTwoIndependentlyResolvedIdentitiesNeverMerged() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        store.saveVocabulary(vocab(ref, 2L, "E2", "語", "ご", "noun", "N5", "meaning"));

        List<NormalizedContentCandidate> all = candidateRepository
                .findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(all).hasSize(2);
        for (NormalizedContentCandidate c : all) {
            var result = readiness.detail(NormalizedCandidateType.VOCABULARY, c.getId()).result();
            // The identity axis resolves per candidate type, not from expression/reading - it does not
            // (and cannot) treat these two candidates as one, since it never inspects their fields.
            assertThat(codesOf(result)).doesNotContain(PromotionReadinessIssueCode.PRODUCTION_IDENTITY_POLICY_UNRESOLVED);
            assertThat(result.productionIdentityStatus())
                    .isEqualTo(com.japanese.content.dto.PromotionReadinessModels.ProductionIdentityStatus.RESOLVED);
        }
    }

    @Test
    void y_sameGrammarPatternCandidatesAreStillTwoIndependentlyResolvedIdentitiesNeverCombined() {
        String ref = ref();
        seedN5Level();
        store.saveGrammar(grammar(ref, 1L, "U1", "pattern", "connection", "N5"));
        store.saveGrammar(grammar(ref, 2L, "U2", "pattern", "connection", "N5"));

        List<NormalizedContentCandidate> all = candidateRepository
                .findByCandidateTypeAndSourceRef(NormalizedCandidateType.GRAMMAR, ref);
        assertThat(all).hasSize(2);
        for (NormalizedContentCandidate c : all) {
            var result = readiness.detail(NormalizedCandidateType.GRAMMAR, c.getId()).result();
            assertThat(codesOf(result)).doesNotContain(PromotionReadinessIssueCode.PRODUCTION_IDENTITY_POLICY_UNRESOLVED);
            assertThat(result.productionIdentityStatus())
                    .isEqualTo(com.japanese.content.dto.PromotionReadinessModels.ProductionIdentityStatus.RESOLVED);
        }
    }

    @Test
    void z_productionIdentityResolvesForASupportedTypeRegardlessOfSourceRef() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        registerAllowedSource(ref);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidate.getId()).result();

        // Identity resolution depends only on candidateType (VOCABULARY is supported), never on
        // sourceRef - unlike VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED below, which does.
        assertThat(result.productionIdentityStatus())
                .isEqualTo(com.japanese.content.dto.PromotionReadinessModels.ProductionIdentityStatus.RESOLVED);
        assertThat(codesOf(result)).doesNotContain(PromotionReadinessIssueCode.PRODUCTION_IDENTITY_POLICY_UNRESOLVED);
        // ref() is not the canonical JLPT-MAX source, so this candidate is still BLOCKED overall -
        // just no longer for an identity reason.
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED);
        assertThat(result.overallStatus()).isEqualTo(OverallStatus.BLOCKED);
    }

    // ===================================================================================
    // Vocabulary meaning-language policy (Ticket 4E-0: NormalizedVocabularyMeaningLanguagePolicy)
    // ===================================================================================

    @Test
    void unknownVocabularySourceIsBlockedByTheMeaningLanguagePolicy() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED);
    }

    @Test
    void canonicalJlptMaxVocabularySourcePassesTheMeaningLanguageAxis() {
        seedN5Level();
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, 100001L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidate.getId()).result();

        assertThat(codesOf(result))
                .doesNotContain(PromotionReadinessIssueCode.VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED);
    }

    @Test
    void grammarCandidatesNeverCarryTheVocabularyMeaningLanguageCode() {
        String ref = ref();
        seedN5Level();
        store.saveGrammar(grammar(ref, 1L, "U1", "pattern", "connection", "N5"));
        var result = readiness.detail(NormalizedCandidateType.GRAMMAR, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result))
                .doesNotContain(PromotionReadinessIssueCode.VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED);
    }

    @Test
    void aFullyCleanCanonicalSourceVocabularyCandidateIsNowReadyForDraftPromotion() {
        seedN5Level();
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, 100002L, "E2", "本", "ほん", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF);
        registerAllowedCanonicalSource();

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidate.getId()).result();

        // As of Ticket 4E-0, a candidate with clean quality, no pairs, valid field mapping, a
        // resolved meaning language, a mappable Level, ALLOWED rights, and a resolved identity axis
        // has no remaining issues at all.
        assertThat(result.issues()).isEmpty();
        assertThat(result.overallStatus()).isEqualTo(OverallStatus.READY_FOR_DRAFT_PROMOTION);
    }

    // ===================================================================================
    // Source rights
    // ===================================================================================

    @Test
    void sourceNotRegisteredIsBlocked() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(result.sourceRightsStatus()).isNull();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.SOURCE_NOT_REGISTERED);
    }

    @Test
    void sourceAllowedPassesTheRightsAxis() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        registerAllowedSource(ref);
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(result.sourceRightsStatus()).isEqualTo(ContentSourceRightsStatus.ALLOWED);
        assertThat(codesOf(result)).doesNotContain(PromotionReadinessIssueCode.SOURCE_RIGHTS_NOT_ALLOWED,
                PromotionReadinessIssueCode.SOURCE_RIGHTS_MANUAL_REVIEW, PromotionReadinessIssueCode.SOURCE_NOT_REGISTERED);
    }

    @Test
    void sourceBlockedIsBlockedWithTheNotAllowedCode() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        ContentSource source = contentSources.save(new ContentSource(ref, "test source", "1", null, null, null, null));
        source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 시작", false, null);
        source.reviewRights(ContentSourceRightsStatus.BLOCKED, "허용 불가", false, null);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.SOURCE_RIGHTS_NOT_ALLOWED);
    }

    @Test
    void sourceManualReviewRequiredIsBlockedWithTheManualReviewCode() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        ContentSource source = contentSources.save(new ContentSource(ref, "test source", "1", null, null, null, null));
        source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 중", false, null);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.SOURCE_RIGHTS_MANUAL_REVIEW);
    }

    // ===================================================================================
    // Already promoted
    // ===================================================================================

    @Test
    void alreadyLinkedProvenanceMakesTheOverallStatusAlreadyPromoted() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 42L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);

        ContentItem item = contentItems.save(new ContentItem("already-promoted-" + UUID.randomUUID(), ContentType.WORD, ref, false));
        ImportedSourceRecord record = new ImportedSourceRecord(ref, "JLPT MAX덱 어휘", 42L, "N5", "", "f", "v");
        record.linkContentItem(item);
        importedSourceRecords.save(record);

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidate.getId()).result();

        assertThat(result.existingProductionLinkStatus()).isEqualTo(ExistingProductionLinkStatus.LINKED);
        assertThat(result.overallStatus()).isEqualTo(OverallStatus.ALREADY_PROMOTED);
        assertThat(codesOf(result)).contains(PromotionReadinessIssueCode.ALREADY_PROMOTED);
    }

    @Test
    void anUnlinkedImportedSourceRecordDoesNotCountAsAlreadyPromoted() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 42L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);

        importedSourceRecords.save(new ImportedSourceRecord(ref, "JLPT MAX덱 어휘", 42L, "N5", "", "f", "v"));

        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidate.getId()).result();

        assertThat(result.existingProductionLinkStatus()).isEqualTo(ExistingProductionLinkStatus.NOT_LINKED);
        assertThat(result.overallStatus()).isNotEqualTo(OverallStatus.ALREADY_PROMOTED);
    }

    // ===================================================================================
    // Zero-write isolation + determinism
    // ===================================================================================

    @Test
    void readinessComputationNeverWritesAnyRowAnywhere() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.SAME_CONTENT, "동일"), admin("iso"));
        registerAllowedSource(ref);

        Map<String, Long> before = allCounts();

        readiness.list(NormalizedCandidateType.VOCABULARY, ref, null, null, null, 0);
        readiness.summary(NormalizedCandidateType.VOCABULARY, ref);
        readiness.detail(NormalizedCandidateType.VOCABULARY, pair.getLeftCandidate().getId());
        readiness.detail(NormalizedCandidateType.VOCABULARY, pair.getRightCandidate().getId());
        readiness.list(NormalizedCandidateType.VOCABULARY, ref, null, null, null, 0);

        assertThat(allCounts()).isEqualTo(before);
    }

    @Test
    void repeatedComputationIsDeterministic() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                submission(pair, HumanReviewDecision.NEEDS_FOLLOWUP, null), admin("det"));

        ReadinessSummary first = readiness.summary(NormalizedCandidateType.VOCABULARY, ref);
        ReadinessSummary second = readiness.summary(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(second).isEqualTo(first);

        PromotionReadinessDetailView firstDetail =
                readiness.detail(NormalizedCandidateType.VOCABULARY, pair.getLeftCandidate().getId());
        PromotionReadinessDetailView secondDetail =
                readiness.detail(NormalizedCandidateType.VOCABULARY, pair.getLeftCandidate().getId());
        assertThat(secondDetail.result().issues()).isEqualTo(firstDetail.result().issues());
    }

    @Test
    void summaryBreakdownMapsPreserveDeclaredEnumOrderAcrossRepeatedCalls() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        store.saveVocabulary(new VocabularyNormalizationResult(ref, 2L, "E2", "語2", "ご2", "noun", null,
                List.of(), List.of(), new NormalizedJlptLevel("N9", "N9", "WordJLPT"), "語2", "ご2", Map.of(),
                List.of(), true));

        List<String> expectedIssueOrder = Arrays.stream(PromotionReadinessIssueCode.values()).map(Enum::name).toList();
        List<String> expectedQualityOrder = Arrays.stream(
                com.japanese.content.entity.NormalizedCandidateQualityState.values()).map(Enum::name).toList();
        List<String> expectedPairOrder = Arrays.stream(PairResolutionStatus.values()).map(Enum::name).toList();
        List<String> expectedMappingOrder = Arrays.stream(MappingStatus.values()).map(Enum::name).toList();
        List<String> expectedRightsOrder = new ArrayList<>(
                Arrays.stream(ContentSourceRightsStatus.values()).map(Enum::name).toList());
        expectedRightsOrder.add("NOT_REGISTERED");

        // 두 번 반복 호출해도 순서가 매번 동일한지 함께 검증한다 (Map.copyOf(...)의 iteration order는
        // 보장되지 않으므로 여기서 회귀를 잡는다).
        for (int i = 0; i < 2; i++) {
            ReadinessSummary summary = readiness.summary(NormalizedCandidateType.VOCABULARY, ref);
            assertThat(summary.blockedByIssueCode().keySet()).containsExactlyElementsOf(expectedIssueOrder);
            assertThat(summary.qualityBreakdown().keySet()).containsExactlyElementsOf(expectedQualityOrder);
            assertThat(summary.pairResolutionBreakdown().keySet()).containsExactlyElementsOf(expectedPairOrder);
            assertThat(summary.mappingBreakdown().keySet()).containsExactlyElementsOf(expectedMappingOrder);
            assertThat(summary.sourceRightsBreakdown().keySet()).containsExactlyElementsOf(expectedRightsOrder);
            assertThat(List.copyOf(summary.candidatesByBlockerCountBreakdown().keySet()))
                    .isSortedAccordingTo(Comparator.comparingInt(Integer::parseInt));
        }
    }

    @Test
    void issueOrderWithinACandidateIsDeterministicByDeclaredEnumOrder() {
        String ref = ref();
        // No Level seeded (JLPT_LEVEL_UNMAPPABLE) and no ContentSource registered (SOURCE_NOT_REGISTERED),
        // so this candidate always carries at least these two plus PRODUCTION_IDENTITY_POLICY_UNRESOLVED.
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        var result = readiness.detail(NormalizedCandidateType.VOCABULARY, onlyCandidate(ref).getId()).result();

        List<Integer> ordinals = result.issues().stream().map(i -> i.code().ordinal()).toList();
        assertThat(ordinals).isSorted();
    }

    // ===================================================================================
    // Helpers
    // ===================================================================================

    private List<PromotionReadinessIssueCode> codesOf(com.japanese.content.dto.PromotionReadinessModels.PromotionReadinessResult result) {
        return result.issues().stream().map(PromotionReadinessIssue::code).toList();
    }

    private void registerAllowedSource(String ref) {
        ContentSource source = contentSources.save(new ContentSource(ref, "test source", "1", null, null, null, null));
        source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 시작", false, null);
        source.reviewRights(ContentSourceRightsStatus.ALLOWED, "허용", false, null);
    }

    /**
     * {@code ContentSourceCatalog} ("sample"/"import-sample" profiles) already commits a
     * {@code ContentSource(JLPT-MAX-Deck-2.1.1.apkg)} row (rightsStatus {@code UNKNOWN}) once at
     * application startup, shared across this whole test JVM run - mirrors {@link #seedN5Level()}'s
     * idempotent get-or-create for exactly the same reason.
     *
     * <p>This helper's precondition is that this canonical source ends this call {@code ALLOWED} -
     * every caller relies on that to exercise the fully-clean/rights-cleared path. It only knows how to
     * get there from {@code UNKNOWN} or {@code MANUAL_REVIEW_REQUIRED} (already {@code ALLOWED} is also
     * fine - nothing to do); any other starting state (e.g. {@code BLOCKED}) is a fixture assumption
     * this helper was not designed for, so it fails loudly instead of silently leaving the source
     * un-cleared and letting the caller's assertions fail with a confusing, unrelated blocker.
     */
    private void registerAllowedCanonicalSource() {
        ContentSource source = contentSources.findBySourceRef(CANONICAL_JLPT_MAX_SOURCE_REF).orElseThrow();
        if (source.getRightsStatus() == ContentSourceRightsStatus.UNKNOWN) {
            source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 시작", false, null);
        }
        if (source.getRightsStatus() == ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED) {
            source.reviewRights(ContentSourceRightsStatus.ALLOWED, "허용", false, null);
        }
        assertThat(source.getRightsStatus())
                .as("registerAllowedCanonicalSource()는 UNKNOWN/MANUAL_REVIEW_REQUIRED/ALLOWED 초기 상태만 지원합니다 - "
                        + "실제 초기 상태(%s)는 이 fixture가 설계된 전제를 벗어납니다.", source.getRightsStatus())
                .isEqualTo(ContentSourceRightsStatus.ALLOWED);
    }

    private NormalizedContentCandidate onlyCandidate(String ref) {
        List<NormalizedContentCandidate> candidates = candidateRepository
                .findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(c -> c.getSourceRef().equals(ref))
                .toList();
        if (!candidates.isEmpty()) {
            assertThat(candidates).hasSize(1);
            return candidates.get(0);
        }
        List<NormalizedContentCandidate> grammarCandidates = candidateRepository
                .findByCandidateType(NormalizedCandidateType.GRAMMAR).stream()
                .filter(c -> c.getSourceRef().equals(ref))
                .toList();
        assertThat(grammarCandidates).hasSize(1);
        return grammarCandidates.get(0);
    }

    private NormalizedCandidateMatchPair seedDuplicatePair(String ref) {
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        store.saveVocabulary(vocab(ref, 2L, "E2", "語", "ご", "noun", "N5", "meaning"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        List<NormalizedCandidateMatchPair> pairs = pairRepository
                .findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(pairs).hasSize(1);
        return pairs.get(0);
    }

    private NormalizedCandidateMatchPair pairInvolving(String ref, Long idA, Long idB) {
        return pairRepository.findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(
                        NormalizedCandidateType.VOCABULARY, ref).stream()
                .filter(p -> (p.getLeftCandidate().getId().equals(idA) && p.getRightCandidate().getId().equals(idB))
                        || (p.getLeftCandidate().getId().equals(idB) && p.getRightCandidate().getId().equals(idA)))
                .findFirst().orElseThrow();
    }

    private DecisionSubmission submission(NormalizedCandidateMatchPair pair, HumanReviewDecision decision, String note) {
        return new DecisionSubmission(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId(), decision, note,
                pair.getGeneratedAt(), pair.getAssessment(), null);
    }

    private Map<String, Long> allCounts() {
        return Map.ofEntries(
                // private candidate pipeline (Tickets 4A-4C)
                Map.entry("candidates", candidateRepository.count()),
                Map.entry("normalizedVocabularyCandidates",
                        jdbcClient.sql("select count(*) from normalized_vocabulary_candidates").query(Long.class).single()),
                Map.entry("normalizedGrammarCandidates",
                        jdbcClient.sql("select count(*) from normalized_grammar_candidates").query(Long.class).single()),
                Map.entry("normalizedVocabularyCandidateMeanings",
                        jdbcClient.sql("select count(*) from normalized_vocabulary_candidate_meanings").query(Long.class).single()),
                Map.entry("normalizedVocabularyCandidateExamples",
                        jdbcClient.sql("select count(*) from normalized_vocabulary_candidate_examples").query(Long.class).single()),
                Map.entry("pairs", pairRepository.count()),
                Map.entry("matchEvidence", jdbcClient.sql("select count(*) from normalized_candidate_match_evidence")
                        .query(Long.class).single()),
                Map.entry("reviews", reviewRepository.count()),
                Map.entry("history", jdbcClient.sql("select count(*) from normalized_candidate_pair_review_history")
                        .query(Long.class).single()),
                // production
                Map.entry("contentItems", contentItems.count()),
                Map.entry("words", jdbcClient.sql("select count(*) from words").query(Long.class).single()),
                Map.entry("meanings", jdbcClient.sql("select count(*) from meanings").query(Long.class).single()),
                Map.entry("grammars", jdbcClient.sql("select count(*) from grammars").query(Long.class).single()),
                Map.entry("examples", jdbcClient.sql("select count(*) from examples").query(Long.class).single()),
                Map.entry("importedSourceRecords", importedSourceRecords.count()),
                Map.entry("contentSources", contentSources.count()),
                Map.entry("levels", levels.count()),
                Map.entry("contentReviewHistory", contentReviewHistory.count()),
                Map.entry("grammarEnrichments", grammarEnrichments.count()),
                Map.entry("grammarRelations", grammarRelations.count()),
                Map.entry("grammarComparisons", grammarComparisons.count()),
                Map.entry("releaseBatches", releaseBatches.count()),
                Map.entry("releaseBatchItems", releaseBatchItems.count()));
    }

    private VocabularyNormalizationResult vocab(String ref, long noteId, String entryId, String expression,
            String reading, String partOfSpeech, String level, String meaning) {
        return new VocabularyNormalizationResult(
                ref, noteId, entryId, expression, reading, partOfSpeech, null,
                List.of(new NormalizedMeaning(1, meaning)),
                List.of(), new NormalizedJlptLevel(level, level, "WordJLPT"), expression, reading, Map.of(),
                List.of(), true);
    }

    private VocabularyNormalizationResult withWarnings(VocabularyNormalizationResult base,
            VocabularyNormalizationWarning... warnings) {
        return new VocabularyNormalizationResult(base.sourceRef(), base.sourceNoteId(), base.entryId(),
                base.expression(), base.reading(), base.partOfSpeech(), base.pitchAccent(), base.meanings(),
                base.examples(), base.level(), base.normalizedSearchExpression(), base.normalizedSearchReading(),
                base.preservedExtraFields(), List.of(warnings), base.validForPromotion());
    }

    private GrammarNormalizationResult grammar(String ref, long noteId, String unitId, String pattern,
            String connection, String level) {
        return new GrammarNormalizationResult(ref, noteId, unitId, pattern,
                new NormalizedGrammarExample(1, "前文" + pattern, null, "번역"), "gloss", "nuance", connection,
                List.of(), new NormalizedJlptLevel(level, level, "Level"), null, Map.of(), List.of(), true);
    }
}
