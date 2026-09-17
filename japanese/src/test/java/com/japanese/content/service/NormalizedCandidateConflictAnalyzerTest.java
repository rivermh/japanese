package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.content.entity.NormalizedCandidateMatchAssessment;
import com.japanese.content.entity.NormalizedCandidateMatchEvidence;
import com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.importer.GrammarNormalizationResult;
import com.japanese.content.importer.NormalizedConfusablePattern;
import com.japanese.content.importer.NormalizedGrammarExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.GrammarComparisonRepository;
import com.japanese.content.repository.GrammarEnrichmentRepository;
import com.japanese.content.repository.GrammarRelationRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
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
 * JLPT-MAX Ticket 4B: synthetic scenario coverage for {@link NormalizedCandidateConflictAnalyzer},
 * lettered to match the ticket's own test list (A-Q). Every scenario here builds candidates through
 * {@link NormalizedCandidateStore} (Ticket 4A), the same path production candidates go through -
 * this class never constructs {@code NormalizedContentCandidate} rows by any other means.
 */
@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class NormalizedCandidateConflictAnalyzerTest {

    @Autowired
    NormalizedCandidateStore store;
    @Autowired
    NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired
    NormalizedContentCandidateRepository candidateRepository;
    @Autowired
    NormalizedCandidateMatchPairRepository pairRepository;
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
        return "conflict-analyzer-test-" + UUID.randomUUID();
    }

    // ===================================================================================
    // Vocabulary
    // ===================================================================================

    @Test
    void a_sameEntryIdIdenticalSemanticsIsExactDuplicate() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E1", "語", "ご", "N5", "word"));

        var summary = analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        assertThat(summary.pairCount()).isEqualTo(1);
        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.EXACT_DUPLICATE);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.SAME_ENTRY_ID,
                NormalizedCandidateMatchEvidenceCode.SAME_EXPRESSION, NormalizedCandidateMatchEvidenceCode.SAME_READING,
                NormalizedCandidateMatchEvidenceCode.SAME_MEANING, NormalizedCandidateMatchEvidenceCode.SAME_LEVEL);
        assertThat(pairRepository.findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRefAndAssessment(
                NormalizedCandidateType.VOCABULARY, ref, NormalizedCandidateMatchAssessment.EXACT_DUPLICATE))
                .containsExactly(pair);
        assertThat(pairRepository.findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRefAndAssessment(
                NormalizedCandidateType.VOCABULARY, ref, NormalizedCandidateMatchAssessment.CONFLICT))
                .isEmpty();
    }

    @Test
    void b_sameExpressionReadingDifferentEntryIdIsPossibleDuplicate() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E2", "語", "ご", "N5", "word"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.DIFFERENT_ENTRY_ID);
    }

    @Test
    void c_sameEntryIdDifferentExpressionIsConflict() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E1", "別", "ご", "N5", "word"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.CONFLICT);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.SAME_ENTRY_ID,
                NormalizedCandidateMatchEvidenceCode.DIFFERENT_EXPRESSION);
    }

    @Test
    void d_sameEntryIdDifferentReadingIsConflict() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E1", "語", "べつ", "N5", "word"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.CONFLICT);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.SAME_ENTRY_ID,
                NormalizedCandidateMatchEvidenceCode.DIFFERENT_READING);
    }

    @Test
    void e_sameExpressionDifferentReadingIsNeverMisjudgedAsExact() {
        String ref = ref();
        // Same EntryID forces a comparison (blocking never joins on expression alone); the point
        // under test is that a differing reading must never still yield EXACT_DUPLICATE.
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E1", "語", "べつ", "N5", "word"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isNotEqualTo(NormalizedCandidateMatchAssessment.EXACT_DUPLICATE);
    }

    @Test
    void differentEntryIdCapsExactDuplicateEvenWithIdenticalContent() {
        // Ticket 4B design decision: a source-native identity disagreement (both EntryIDs present
        // and different) caps the result at POSSIBLE_DUPLICATE, never EXACT_DUPLICATE, even when
        // every other field happens to match - the source explicitly claims these are different
        // entries, so this ticket never overrides that claim with a stronger label.
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E2", "語", "ご", "N5", "word"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
    }

    @Test
    void unrelatedVocabularyCandidatesAreNeverPaired() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E2", "全然違う", "ぜんぜんちがう", "N3", "totally different"));

        var summary = analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        assertThat(summary.pairCount()).isZero();
        assertThat(summary.uniqueCount()).isEqualTo(2);
    }

    // ===================================================================================
    // Grammar
    // ===================================================================================

    @Test
    void f_sameUnitIdIdenticalSemanticsIsExactDuplicate() {
        String ref = ref();
        store.saveGrammar(grammar(ref, 1L, "U1", "〜てみる", "N4", "try", "casual", "verb-て + みる"));
        store.saveGrammar(grammar(ref, 2L, "U1", "〜てみる", "N4", "try", "casual", "verb-て + みる"));

        var summary = analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        assertThat(summary.pairCount()).isEqualTo(1);
        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.GRAMMAR);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.EXACT_DUPLICATE);
    }

    @Test
    void g_samePatternDifferentUnitIdIsPossibleDuplicate() {
        String ref = ref();
        store.saveGrammar(grammar(ref, 1L, "U1", "で", "N5", "location", "casual", "noun + で"));
        store.saveGrammar(grammar(ref, 2L, "U2", "で", "N5", "reason", "casual", "noun + で"));

        analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.GRAMMAR);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.SAME_PATTERN,
                NormalizedCandidateMatchEvidenceCode.DIFFERENT_UNIT_ID, NormalizedCandidateMatchEvidenceCode.DIFFERENT_MEANING_GLOSS);
    }

    @Test
    void h_sameUnitIdDifferentPatternIsConflict() {
        String ref = ref();
        store.saveGrammar(grammar(ref, 1L, "U1", "〜てみる", "N4", "try", "casual", "verb-て + みる"));
        store.saveGrammar(grammar(ref, 2L, "U1", "〜てしまう", "N4", "try", "casual", "verb-て + みる"));

        analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.GRAMMAR);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.CONFLICT);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.SAME_UNIT_ID,
                NormalizedCandidateMatchEvidenceCode.DIFFERENT_PATTERN);
    }

    @Test
    void i_samePatternSameLevelDifferentMeaningGlossIsPossibleDuplicateWithEvidence() {
        String ref = ref();
        store.saveGrammar(grammar(ref, 1L, "U1", "の", "N5", "possession", "casual", "noun + の"));
        store.saveGrammar(grammar(ref, 2L, "U2", "の", "N5", "nominalizer", "casual", "noun + の"));

        analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.GRAMMAR);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.SAME_PATTERN,
                NormalizedCandidateMatchEvidenceCode.SAME_LEVEL, NormalizedCandidateMatchEvidenceCode.DIFFERENT_MEANING_GLOSS);
    }

    @Test
    void j_samePatternDifferentLevelIsPossibleDuplicateWithEvidence() {
        String ref = ref();
        store.saveGrammar(grammar(ref, 1L, "U1", "読みかけの", "N2", "in the middle of reading", "casual", "verb + かけの"));
        store.saveGrammar(grammar(ref, 2L, "U2", "読みかけの", "N1", "not yet finished", "casual", "verb + かけの"));

        analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.GRAMMAR);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.SAME_PATTERN,
                NormalizedCandidateMatchEvidenceCode.DIFFERENT_LEVEL);
    }

    // ===================================================================================
    // Cross-cutting (K-Q)
    // ===================================================================================

    @Test
    void k_crossTypeCandidatesAreNeverPaired() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "SAME-ID", "語", "ご", "N5", "word"));
        store.saveGrammar(grammar(ref, 2L, "SAME-ID", "語", "N5", "word", "casual", "n/a"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        assertThat(pairRepository.findAll()).noneSatisfy(pair ->
                assertThat(pair.getLeftCandidate().getCandidateType())
                        .isNotEqualTo(pair.getRightCandidate().getCandidateType()));
    }

    @Test
    void l_selfMatchNeverPersists() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        assertThat(pairRepository.findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(
                NormalizedCandidateType.VOCABULARY, ref)).isEmpty();
    }

    @Test
    void m_pairOrientationIsCanonicalRegardlessOfInsertOrder() {
        String ref = ref();
        NormalizedContentCandidate first = store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        NormalizedContentCandidate second = store.saveVocabulary(vocab(ref, 2L, "E1", "語", "ご", "N5", "word"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getLeftCandidate().getId()).isEqualTo(Math.min(first.getId(), second.getId()));
        assertThat(pair.getRightCandidate().getId()).isEqualTo(Math.max(first.getId(), second.getId()));
    }

    @Test
    void n_rerunningOnUnchangedPopulationIsIdempotent() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E1", "語", "ご", "N5", "word"));

        var first = analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        Long firstPairId = onlyPair(ref, NormalizedCandidateType.VOCABULARY).getId();
        var second = analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        Long secondPairId = onlyPair(ref, NormalizedCandidateType.VOCABULARY).getId();

        assertThat(second.pairCount()).isEqualTo(first.pairCount());
        assertThat(pairRepository.findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(
                NormalizedCandidateType.VOCABULARY, ref)).hasSize(1);
        assertThat(secondPairId).isNotEqualTo(firstPairId);
    }

    @Test
    void o_candidateRefreshThenRerunLeavesNoStaleEvidence() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E1", "語", "ご", "N5", "word"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(onlyPair(ref, NormalizedCandidateType.VOCABULARY).getAssessment())
                .isEqualTo(NormalizedCandidateMatchAssessment.EXACT_DUPLICATE);

        // Refresh candidate 2 (same sourceRef+sourceNoteId) with a different meaning.
        store.saveVocabulary(vocab(ref, 2L, "E1", "語", "ご", "N5", "different-sense"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        List<NormalizedCandidateMatchPair> pairs = pairRepository
                .findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
        long evidenceCount = jdbcClient.sql(
                        "select count(*) from normalized_candidate_match_evidence e "
                                + "join normalized_candidate_match_pairs p on p.id = e.pair_id "
                                + "join normalized_content_candidates c on c.id = p.left_candidate_id "
                                + "where c.source_ref = ?")
                .param(ref).query(Long.class).single();
        assertThat(evidenceCount).isEqualTo(pairs.get(0).getEvidence().size());
    }

    @Test
    void p_fatalCandidateIsStillCompared() {
        String ref = ref();
        VocabularyNormalizationResult fatal = new VocabularyNormalizationResult(
                ref, 1L, "E1", null, null, null, null,
                List.of(), List.of(), new NormalizedJlptLevel(null, null, null), null, null, Map.of(), List.of(), false);
        store.saveVocabulary(fatal);
        store.saveVocabulary(vocab(ref, 2L, "E1", "語", "ご", "N5", "word"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.CONFLICT);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.SAME_ENTRY_ID,
                NormalizedCandidateMatchEvidenceCode.DIFFERENT_EXPRESSION);
    }

    @Test
    void q_analysisNeverChangesProductionOrImportedSourceRowCounts() {
        long contentItemsBefore = contentItems.count();
        long wordsBefore = tableCount("words");
        long grammarsBefore = tableCount("grammars");
        long enrichmentsBefore = grammarEnrichments.count();
        long relationsBefore = grammarRelations.count();
        long comparisonsBefore = grammarComparisons.count();
        long importedBefore = importedSourceRecords.count();

        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E1", "語", "ご", "N5", "word"));
        store.saveGrammar(grammar(ref, 3L, "U1", "〜てみる", "N4", "try", "casual", "verb-て + みる"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        assertThat(contentItems.count()).isEqualTo(contentItemsBefore);
        assertThat(tableCount("words")).isEqualTo(wordsBefore);
        assertThat(tableCount("grammars")).isEqualTo(grammarsBefore);
        assertThat(grammarEnrichments.count()).isEqualTo(enrichmentsBefore);
        assertThat(grammarRelations.count()).isEqualTo(relationsBefore);
        assertThat(grammarComparisons.count()).isEqualTo(comparisonsBefore);
        assertThat(importedSourceRecords.count()).isEqualTo(importedBefore);
    }

    // ===================================================================================
    // Independent-review follow-up (Ticket 4B hardening)
    // ===================================================================================

    @Test
    void r_grammarMeaningGlossOverflowIsBoundedAndPersistsSuccessfully() {
        String ref = ref();
        // Each side alone is well within meaningGloss's own varchar(2000) column limit, but the two
        // sides concatenated by diff() are not - this is the MAJOR overflow scenario (independent
        // review item 1/2), reproduced with a real DB round trip, not just an in-memory assertion.
        String glossA = "😀".repeat(900) + "-A";
        String glossB = "😀".repeat(900) + "-B";
        assertThat(glossA.length()).isLessThan(2000);
        store.saveGrammar(grammarWithGloss(ref, 1L, "U1", "で", "N5", glossA));
        store.saveGrammar(grammarWithGloss(ref, 2L, "U2", "で", "N5", glossB));

        var summary = analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        assertThat(summary.pairCount()).isEqualTo(1);
        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.GRAMMAR);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
        NormalizedCandidateMatchEvidence glossEvidence = evidenceOf(pair, NormalizedCandidateMatchEvidenceCode.DIFFERENT_MEANING_GLOSS);
        assertThat(glossEvidence.getDetail()).hasSizeLessThan(2000).contains("…[truncated]");
        assertThat(containsUnpairedSurrogate(glossEvidence.getDetail())).isFalse();

        // Cross-checked via plain SQL: proves the bounded value actually round-tripped through the
        // real varchar(2000) column, not merely survived in the in-memory JPA entity.
        String persistedDetail = jdbcClient.sql(
                        "select detail from normalized_candidate_match_evidence e "
                                + "join normalized_candidate_match_pairs p on p.id = e.pair_id "
                                + "join normalized_content_candidates c on c.id = p.left_candidate_id "
                                + "where c.source_ref = ? and e.evidence_code = 'DIFFERENT_MEANING_GLOSS'")
                .param(ref).query(String.class).single();
        assertThat(persistedDetail).hasSizeLessThan(2000);
    }

    @Test
    void s_grammarNuanceOverflowIsBoundedAndPersistsSuccessfully() {
        String ref = ref();
        // nuance is longtext (unbounded at the source column) - a plausible normal-length nuance can
        // exceed the excerpt cap far more easily than the varchar(2000) fields above.
        String nuanceA = "あ".repeat(3000) + "-A";
        String nuanceB = "い".repeat(3000) + "-B";
        store.saveGrammar(grammarWithNuance(ref, 1L, "U1", "に", "N5", nuanceA));
        store.saveGrammar(grammarWithNuance(ref, 2L, "U2", "に", "N5", nuanceB));

        analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.GRAMMAR);
        NormalizedCandidateMatchEvidence nuanceEvidence = evidenceOf(pair, NormalizedCandidateMatchEvidenceCode.DIFFERENT_NUANCE);
        assertThat(nuanceEvidence.getDetail()).hasSizeLessThan(2000).contains("…[truncated]");
    }

    @Test
    void t_vocabularyMeaningsAggregateOverflowIsBoundedAndPersistsSuccessfully() {
        String ref = ref();
        // No single meaning is unusually long, but several longtext meaning rows joined into one
        // aggregate comparison key (meaningsKey()) can still exceed the excerpt cap in aggregate.
        List<NormalizedMeaning> meaningsA = List.of(
                new NormalizedMeaning(1, "め".repeat(700)), new NormalizedMeaning(2, "い".repeat(700)));
        List<NormalizedMeaning> meaningsB = List.of(
                new NormalizedMeaning(1, "め".repeat(700)), new NormalizedMeaning(2, "ろ".repeat(700)));
        store.saveVocabulary(vocabWithMeaningList(ref, 1L, "E1", "語", "ご", "N5", meaningsA));
        store.saveVocabulary(vocabWithMeaningList(ref, 2L, "E1", "語", "ご", "N5", meaningsB));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        NormalizedCandidateMatchEvidence meaningEvidence = evidenceOf(pair, NormalizedCandidateMatchEvidenceCode.DIFFERENT_MEANING);
        assertThat(meaningEvidence.getDetail()).hasSizeLessThan(2000).contains("…[truncated]");
    }

    @Test
    void malformedGrammarCandidateMissingDetailDoesNotCrashAnalysis() {
        String ref = ref();
        store.saveGrammar(grammar(ref, 1L, "U1", "〜てみる", "N4", "try", "casual", "verb-て + みる"));
        store.saveGrammar(grammar(ref, 2L, "U1", "〜てみる", "N4", "try", "casual", "verb-て + みる"));
        // Simulates a malformed row that bypassed NormalizedCandidateStore entirely: an envelope with
        // no attached grammarDetail at all. The DB has no reverse FK forcing a detail to exist, so
        // blockGrammar() must tolerate this instead of throwing a NullPointerException that would
        // abort analysis for the whole scope over one bad row.
        candidateRepository.save(new NormalizedContentCandidate(
                NormalizedCandidateType.GRAMMAR, ref, 99L, null, NormalizedCandidateQualityState.INFORMATIONAL,
                Instant.now()));

        var summary = analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        assertThat(summary.candidateCount()).isEqualTo(3);
        assertThat(summary.pairCount()).isEqualTo(1);
        assertThat(onlyPair(ref, NormalizedCandidateType.GRAMMAR).getAssessment())
                .isEqualTo(NormalizedCandidateMatchAssessment.EXACT_DUPLICATE);
    }

    @Test
    void bothEntryIdsAbsentOmitsIdentityEvidenceRow() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, null, "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, null, "語", "ご", "N5", "different-sense"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
        assertThat(codes(pair)).doesNotContain(NormalizedCandidateMatchEvidenceCode.SAME_ENTRY_ID,
                NormalizedCandidateMatchEvidenceCode.DIFFERENT_ENTRY_ID);
    }

    @Test
    void bothUnitIdsAbsentOmitsIdentityEvidenceRow() {
        String ref = ref();
        store.saveGrammar(grammar(ref, 1L, null, "〜てみる", "N4", "try", "casual", "verb-て + みる"));
        store.saveGrammar(grammar(ref, 2L, null, "〜てみる", "N4", "different", "casual", "verb-て + みる"));

        analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.GRAMMAR);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
        assertThat(codes(pair)).doesNotContain(NormalizedCandidateMatchEvidenceCode.SAME_UNIT_ID,
                NormalizedCandidateMatchEvidenceCode.DIFFERENT_UNIT_ID);
    }

    @Test
    void differentPartOfSpeechPreventsExactDuplicate() {
        String ref = ref();
        store.saveVocabulary(vocabWithPartOfSpeech(ref, 1L, "E1", "語", "ご", "N5", "word", "noun"));
        store.saveVocabulary(vocabWithPartOfSpeech(ref, 2L, "E1", "語", "ご", "N5", "word", "verb"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.DIFFERENT_PART_OF_SPEECH);
    }

    @Test
    void samePartOfSpeechIsStillExactDuplicate() {
        String ref = ref();
        store.saveVocabulary(vocabWithPartOfSpeech(ref, 1L, "E1", "語", "ご", "N5", "word", "noun"));
        store.saveVocabulary(vocabWithPartOfSpeech(ref, 2L, "E1", "語", "ご", "N5", "word", "noun"));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.EXACT_DUPLICATE);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.SAME_PART_OF_SPEECH);
    }

    @Test
    void meaningSenseOrderAloneDoesNotBreakExactDuplicate() {
        String ref = ref();
        store.saveVocabulary(vocabWithMeaningList(ref, 1L, "E1", "語", "ご", "N5",
                List.of(new NormalizedMeaning(1, "word"), new NormalizedMeaning(2, "item"))));
        store.saveVocabulary(vocabWithMeaningList(ref, 2L, "E1", "語", "ご", "N5",
                List.of(new NormalizedMeaning(1, "item"), new NormalizedMeaning(2, "word"))));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        assertThat(onlyPair(ref, NormalizedCandidateType.VOCABULARY).getAssessment())
                .isEqualTo(NormalizedCandidateMatchAssessment.EXACT_DUPLICATE);
    }

    @Test
    void duplicateMeaningTextMultiplicityIsNotCollapsed() {
        String ref = ref();
        store.saveVocabulary(vocabWithMeaningList(ref, 1L, "E1", "語", "ご", "N5",
                List.of(new NormalizedMeaning(1, "word"), new NormalizedMeaning(2, "word"))));
        store.saveVocabulary(vocabWithMeaningList(ref, 2L, "E1", "語", "ご", "N5",
                List.of(new NormalizedMeaning(1, "word"))));

        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);

        NormalizedCandidateMatchPair pair = onlyPair(ref, NormalizedCandidateType.VOCABULARY);
        assertThat(pair.getAssessment()).isEqualTo(NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE);
        assertThat(codes(pair)).contains(NormalizedCandidateMatchEvidenceCode.DIFFERENT_MEANING);
    }

    @Test
    void frontExampleRawKindAndConfusablePatternsDoNotAffectExactDuplicate() {
        String ref = ref();
        store.saveGrammar(grammarVaryingExcludedFields(ref, 1L,
                new NormalizedGrammarExample(1, "食べてみる", null, "try eating"), "kind-a",
                List.of(new NormalizedConfusablePattern(1, "pattern-a", "explain-a"))));
        store.saveGrammar(grammarVaryingExcludedFields(ref, 2L,
                new NormalizedGrammarExample(1, "飲んでみる", null, "try drinking"), "kind-b",
                List.of(new NormalizedConfusablePattern(1, "pattern-b", "explain-b"))));

        analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);

        assertThat(onlyPair(ref, NormalizedCandidateType.GRAMMAR).getAssessment())
                .isEqualTo(NormalizedCandidateMatchAssessment.EXACT_DUPLICATE);
    }

    // ===================================================================================
    // helpers
    // ===================================================================================

    private NormalizedCandidateMatchPair onlyPair(String ref, NormalizedCandidateType type) {
        List<NormalizedCandidateMatchPair> pairs =
                pairRepository.findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(type, ref);
        assertThat(pairs).hasSize(1);
        return pairs.get(0);
    }

    private List<NormalizedCandidateMatchEvidenceCode> codes(NormalizedCandidateMatchPair pair) {
        return pair.getEvidence().stream().map(e -> e.getEvidenceCode()).toList();
    }

    private long tableCount(String tableName) {
        return jdbcClient.sql("select count(*) from " + tableName).query(Long.class).single();
    }

    private VocabularyNormalizationResult vocab(String ref, long noteId, String entryId, String expression,
            String reading, String level, String meaning) {
        return new VocabularyNormalizationResult(
                ref, noteId, entryId, expression, reading, "noun", null,
                List.of(new NormalizedMeaning(1, meaning)),
                List.of(), new NormalizedJlptLevel(level, level, "WordJLPT"), expression, reading, Map.of(),
                List.of(), true);
    }

    private GrammarNormalizationResult grammar(String ref, long noteId, String unitId, String pattern, String level,
            String meaningGloss, String nuance, String connection) {
        return new GrammarNormalizationResult(
                ref, noteId, unitId, pattern,
                new NormalizedGrammarExample(1, "example", null, "translation"),
                meaningGloss, nuance, connection, List.of(),
                new NormalizedJlptLevel(level, level, "Level"), "kind", Map.of(), List.of(), true);
    }

    private NormalizedCandidateMatchEvidence evidenceOf(NormalizedCandidateMatchPair pair,
            NormalizedCandidateMatchEvidenceCode code) {
        return pair.getEvidence().stream().filter(e -> e.getEvidenceCode() == code).findFirst()
                .orElseThrow(() -> new AssertionError("no " + code + " evidence found on pair " + pair.getId()));
    }

    /** True if {@code s} contains a high surrogate with no following low surrogate, or vice versa. */
    private static boolean containsUnpairedSurrogate(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    return true;
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return true;
            }
        }
        return false;
    }

    private VocabularyNormalizationResult vocabWithPartOfSpeech(String ref, long noteId, String entryId,
            String expression, String reading, String level, String meaning, String partOfSpeech) {
        return new VocabularyNormalizationResult(
                ref, noteId, entryId, expression, reading, partOfSpeech, null,
                List.of(new NormalizedMeaning(1, meaning)),
                List.of(), new NormalizedJlptLevel(level, level, "WordJLPT"), expression, reading, Map.of(),
                List.of(), true);
    }

    private VocabularyNormalizationResult vocabWithMeaningList(String ref, long noteId, String entryId,
            String expression, String reading, String level, List<NormalizedMeaning> meanings) {
        return new VocabularyNormalizationResult(
                ref, noteId, entryId, expression, reading, "noun", null, meanings,
                List.of(), new NormalizedJlptLevel(level, level, "WordJLPT"), expression, reading, Map.of(),
                List.of(), true);
    }

    private GrammarNormalizationResult grammarWithGloss(String ref, long noteId, String unitId, String pattern,
            String level, String meaningGloss) {
        return new GrammarNormalizationResult(
                ref, noteId, unitId, pattern,
                new NormalizedGrammarExample(1, "example", null, "translation"),
                meaningGloss, "nuance", "connection", List.of(),
                new NormalizedJlptLevel(level, level, "Level"), "kind", Map.of(), List.of(), true);
    }

    private GrammarNormalizationResult grammarWithNuance(String ref, long noteId, String unitId, String pattern,
            String level, String nuance) {
        return new GrammarNormalizationResult(
                ref, noteId, unitId, pattern,
                new NormalizedGrammarExample(1, "example", null, "translation"),
                "gloss", nuance, "connection", List.of(),
                new NormalizedJlptLevel(level, level, "Level"), "kind", Map.of(), List.of(), true);
    }

    private GrammarNormalizationResult grammarVaryingExcludedFields(String ref, long noteId,
            NormalizedGrammarExample frontExample, String rawKind, List<NormalizedConfusablePattern> confusablePatterns) {
        return new GrammarNormalizationResult(
                ref, noteId, "U1", "〜てみる", frontExample,
                "try", "casual", "verb-て + みる", confusablePatterns,
                new NormalizedJlptLevel("N4", "N4", "Level"), rawKind, Map.of(), List.of(), true);
    }
}
