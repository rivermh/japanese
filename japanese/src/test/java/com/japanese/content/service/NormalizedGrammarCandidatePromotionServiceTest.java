package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.DecisionSubmission;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Grammar;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.ReviewStatus;
import com.japanese.content.importer.GrammarNormalizationIssue;
import com.japanese.content.importer.GrammarNormalizationResult;
import com.japanese.content.importer.GrammarNormalizationWarning;
import com.japanese.content.importer.NormalizedConfusablePattern;
import com.japanese.content.importer.NormalizedGrammarExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentSourceRepository;
import com.japanese.content.repository.GrammarComparisonRepository;
import com.japanese.content.repository.GrammarRelationRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.LevelRepository;
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
import tools.jackson.databind.ObjectMapper;

/**
 * JLPT-MAX Ticket 4E-8: scenario coverage for {@link NormalizedGrammarCandidatePromotionService}.
 * Mirrors {@code NormalizedVocabularyCandidatePromotionServiceTest}'s (Ticket 4E-1) own fixture
 * conventions and scenario set exactly - the "unknown source / policy unresolved" slot is replaced by
 * an explanation-too-long scenario, since Grammar has no meaning-language-policy axis but does have
 * the {@code GRAMMAR_EXPLANATION_TOO_LONG} mapping-length axis instead.
 *
 * <p>Rejection scenarios each assert the exact production-write inventory (ContentItem/Grammar/
 * Example/ImportedSourceRecord counts, plus GrammarRelation/GrammarComparison, which this class must
 * never create) is unchanged, proving the rejection happened before any write.
 */
@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class NormalizedGrammarCandidatePromotionServiceTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired NormalizedCandidatePairReviewService reviewService;
    @Autowired NormalizedGrammarCandidatePromotionService promotion;
    @Autowired NormalizedContentCandidateRepository candidateRepository;
    @Autowired NormalizedCandidateMatchPairRepository pairRepository;
    @Autowired UserAccountRepository accounts;
    @Autowired ContentSourceRepository contentSources;
    @Autowired ContentItemRepository contentItems;
    @Autowired ImportedSourceRecordRepository importedSourceRecords;
    @Autowired LevelRepository levels;
    @Autowired GrammarRelationRepository grammarRelations;
    @Autowired GrammarComparisonRepository grammarComparisons;
    @Autowired JdbcClient jdbcClient;
    @Autowired ObjectMapper objectMapper;

    /** Matches {@code ApkgVocabularyImporter.SOURCE_REF} - the same file-level source covers both note types. */
    private static final String CANONICAL_JLPT_MAX_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";
    private static final String GRAMMAR_NOTE_TYPE = "JLPT MAX덱 문법";

    private String ref() {
        return "grammar-promotion-write-test-" + UUID.randomUUID();
    }

    private UserAccount admin(String suffix) {
        return accounts.save(new UserAccount("grammar-promotion-admin-" + suffix + "-" + UUID.randomUUID(), null,
                "hash", "Admin " + suffix, UserRole.ADMIN));
    }

    private void seedN5Level() {
        levels.findBySystemAndCode("JLPT", "N5").orElseGet(() -> levels.save(new Level("JLPT", "N5", "JLPT N5")));
    }

    private void registerAllowedSource(String ref) {
        ContentSource source = contentSources.save(new ContentSource(ref, "test source", "1", null, null, null, null));
        source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 시작", false, null);
        source.reviewRights(ContentSourceRightsStatus.ALLOWED, "허용", false, null);
    }

    private void registerAllowedCanonicalSource() {
        ContentSource source = contentSources.findBySourceRef(CANONICAL_JLPT_MAX_SOURCE_REF).orElseThrow();
        if (source.getRightsStatus() == ContentSourceRightsStatus.UNKNOWN) {
            source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 시작", false, null);
        }
        if (source.getRightsStatus() == ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED) {
            source.reviewRights(ContentSourceRightsStatus.ALLOWED, "허용", false, null);
        }
        assertThat(source.getRightsStatus()).isEqualTo(ContentSourceRightsStatus.ALLOWED);
    }

    private NormalizedContentCandidate onlyCandidate(String ref) {
        List<NormalizedContentCandidate> candidates = candidateRepository
                .findByCandidateType(NormalizedCandidateType.GRAMMAR).stream()
                .filter(c -> c.getSourceRef().equals(ref))
                .toList();
        assertThat(candidates).hasSize(1);
        return candidates.get(0);
    }

    private NormalizedContentCandidate onlyCandidate(String ref, long sourceNoteId) {
        return candidateRepository.findByCandidateType(NormalizedCandidateType.GRAMMAR).stream()
                .filter(c -> c.getSourceRef().equals(ref) && c.getSourceNoteId() == sourceNoteId)
                .findFirst()
                .orElseThrow();
    }

    private NormalizedCandidateMatchPair seedDuplicatePair(String ref) {
        store.saveGrammar(grammar(ref, 1L, "U1", "文型", "接続", "N5"));
        store.saveGrammar(grammar(ref, 2L, "U1", "文型", "接続", "N5"));
        analyzer.analyze(NormalizedCandidateType.GRAMMAR, ref);
        List<NormalizedCandidateMatchPair> pairs = pairRepository
                .findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(NormalizedCandidateType.GRAMMAR, ref);
        assertThat(pairs).hasSize(1);
        return pairs.get(0);
    }

    private DecisionSubmission submission(NormalizedCandidateMatchPair pair, HumanReviewDecision decision) {
        return new DecisionSubmission(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId(), decision, null,
                pair.getGeneratedAt(), pair.getAssessment(), null);
    }

    private GrammarNormalizationResult grammar(String ref, long noteId, String unitId, String pattern,
            String connection, String level) {
        return new GrammarNormalizationResult(ref, noteId, unitId, pattern,
                new NormalizedGrammarExample(1, pattern + "の前文です", null, pattern + "の번역"), "gloss", "nuance",
                connection, List.of(new NormalizedConfusablePattern(1, "헷갈리는 문형", "설명")),
                new NormalizedJlptLevel(level, level, "Level"), null, Map.of(), List.of(), true);
    }

    /** Seeds a {@code private_apkg_notes} row with genuinely raw-shaped field names/values. */
    private void seedPrivateApkgNote(String sourceRef, long sourceNoteId, String tags,
            List<String> fieldNames, List<String> fieldValues) {
        jdbcClient.sql("insert into private_apkg_notes (source_ref, source_file, source_version, source_note_id, "
                        + "model_id, note_type, category, anki_guid, deck_paths, card_metadata, tags, field_names, "
                        + "field_values, normalized_values, audio_reference_count, extracted_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .param(sourceRef).param("fixture.apkg").param("1").param(sourceNoteId).param(1L)
                .param(GRAMMAR_NOTE_TYPE).param("GRAMMAR").param("guid-" + sourceNoteId)
                .param("[]").param("{}").param(tags)
                .param(objectMapper.writeValueAsString(fieldNames))
                .param(objectMapper.writeValueAsString(fieldValues))
                .param("{}").param(0).param(Instant.now())
                .update();
    }

    private ProductionCounts productionCounts() {
        return new ProductionCounts(
                contentItems.count(),
                jdbcClient.sql("select count(*) from grammars").query(Long.class).single(),
                jdbcClient.sql("select count(*) from examples").query(Long.class).single(),
                importedSourceRecords.count(),
                grammarRelations.count(),
                grammarComparisons.count());
    }

    private record ProductionCounts(long contentItems, long grammars, long examples, long importedSourceRecords,
            long grammarRelations, long grammarComparisons) {
    }

    // ===================================================================================
    // 1. successful clean canonical-source N5 Grammar promotion
    // ===================================================================================

    @Test
    void cleanCanonicalSourceGrammarCandidateIsPromotedToAProductionDraft() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveGrammar(grammar(CANONICAL_JLPT_MAX_SOURCE_REF, 900001L, "U-N5-1", "〜てみる", "동사 て형 + みる", "N5"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900001L);
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, 900001L, " jlpt::n5 raw::tag ",
                List.of("UnitID", "Pattern"), List.of("U-N5-1", "〜てみる"));
        // "sample" profile's SampleContentDataLoader already seeds a few ContentItem/Grammar rows at
        // startup (shared, committed state across this whole test JVM run) - so every assertion below
        // is scoped to a before/after delta, never an absolute count.
        ProductionCounts before = productionCounts();

        NormalizedGrammarCandidatePromotionResult result = promotion.promote(NormalizedCandidateType.GRAMMAR,
                candidate.getId(), candidate.getNormalizedAt());

        assertThat(result.slug()).matches("grammar-[0-9a-f-]{36}");

        ProductionCounts after = productionCounts();
        assertThat(after.contentItems()).isEqualTo(before.contentItems() + 1);
        assertThat(after.grammars()).isEqualTo(before.grammars() + 1);
        assertThat(after.examples()).isEqualTo(before.examples() + 1);
        assertThat(after.importedSourceRecords()).isEqualTo(before.importedSourceRecords() + 1);
        // confusablePatterns is present on this candidate, but must never be resolved into either.
        assertThat(after.grammarRelations()).isEqualTo(before.grammarRelations());
        assertThat(after.grammarComparisons()).isEqualTo(before.grammarComparisons());

        ContentItem item = contentItems.findById(result.contentItemId()).orElseThrow();
        assertThat(item.getSlug()).isEqualTo(result.slug());
        assertThat(item.getType()).isEqualTo(ContentType.GRAMMAR);
        assertThat(item.isPublished()).isFalse();
        assertThat(item.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(item.getSourceRef()).isEqualTo(CANONICAL_JLPT_MAX_SOURCE_REF);

        Grammar grammar = item.getGrammar();
        assertThat(grammar).isNotNull();
        assertThat(grammar.getPattern()).isEqualTo("〜てみる");
        assertThat(grammar.getExplanation()).isEqualTo("gloss\n\nnuance");
        assertThat(grammar.getConnection()).isEqualTo("동사 て형 + みる");

        assertThat(item.getExamples()).hasSize(1);
        assertThat(item.getExamples().get(0).getJapaneseText()).isEqualTo("〜てみるの前文です");
        assertThat(item.getExamples().get(0).getTranslation()).isEqualTo("〜てみるの번역");
        assertThat(item.getExamples().get(0).getReading()).isNull();

        assertThat(item.getLevels()).hasSize(1);
        assertThat(item.getLevels().iterator().next().getCode()).isEqualTo("N5");

        List<ImportedSourceRecord> records = importedSourceRecords.findByContentItemIdOrderById(item.getId());
        assertThat(records).hasSize(1);
        ImportedSourceRecord record = records.get(0);
        assertThat(record.getSourceRef()).isEqualTo(CANONICAL_JLPT_MAX_SOURCE_REF);
        assertThat(record.getNoteType()).isEqualTo(GRAMMAR_NOTE_TYPE);
        assertThat(record.getSourceNoteId()).isEqualTo(900001L);
        assertThat(record.getContentItem()).isEqualTo(item);
    }

    // ===================================================================================
    // truthful provenance
    // ===================================================================================

    @Test
    void newlyCreatedImportedSourceRecordUsesGenuineRawSourceDataNotSyntheticNormalizedFields() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveGrammar(grammar(CANONICAL_JLPT_MAX_SOURCE_REF, 900010L, "U-truthful", "〜たことがある", "た형+ことがある",
                "N5"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900010L);
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, 900010L, " raw::provenance::tag ",
                List.of("UnitID", "Pattern", "Level"), List.of("U-truthful", "〜たことがある", "N5"));

        promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(), candidate.getNormalizedAt());

        ImportedSourceRecord record = importedSourceRecords
                .findBySourceRefAndNoteTypeAndSourceNoteId(CANONICAL_JLPT_MAX_SOURCE_REF, GRAMMAR_NOTE_TYPE, 900010L)
                .orElseThrow();
        assertThat(record.getTags()).isEqualTo(" raw::provenance::tag ");
        assertThat(record.getFieldNames()).isEqualTo("UnitIDPatternLevel");
        assertThat(record.getFieldValues()).isEqualTo("U-truthful〜たことがあるN5");
        assertThat(record.getFieldNames()).doesNotContain("NormalizedCandidateId");
        assertThat(record.getFieldValues()).doesNotContain(candidate.getId().toString());
    }

    @Test
    void multipleRawFieldNamesAndValuesSurvivePromotionWithFieldBoundariesPreservedExactly() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveGrammar(grammar(CANONICAL_JLPT_MAX_SOURCE_REF, 900013L, "U-boundary", "〜べきだ", "動詞普通形+べきだ", "N5"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900013L);
        // Chosen so a delimiter-less join is genuinely ambiguous on split: "ab"+"c" and "a"+"bc" both
        // naively concatenate to "abc" - only a real per-field delimiter can round-trip this back into
        // the original four-element list, proving field boundaries (not just the whole joined string)
        // survive promotion.
        List<String> fieldNames = List.of("UnitID", "ab", "c", "Level");
        List<String> fieldValues = List.of("U-boundary", "a", "bc", "N5");
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, 900013L, "", fieldNames, fieldValues);

        promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(), candidate.getNormalizedAt());

        ImportedSourceRecord record = importedSourceRecords
                .findBySourceRefAndNoteTypeAndSourceNoteId(CANONICAL_JLPT_MAX_SOURCE_REF, GRAMMAR_NOTE_TYPE, 900013L)
                .orElseThrow();
        assertThat(record.getFieldNames().split("\\u001f", -1)).containsExactlyElementsOf(fieldNames);
        assertThat(record.getFieldValues().split("\\u001f", -1)).containsExactlyElementsOf(fieldValues);
    }

    @Test
    void missingTruthfulProvenanceBlocksPromotionWithNoProductionWrites() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveGrammar(grammar(CANONICAL_JLPT_MAX_SOURCE_REF, 900011L, "U-missing", "〜ようにする", "동사 기본형+ようにする",
                "N5"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900011L);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("private_apkg_notes");

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // not ready / rights
    // ===================================================================================

    @Test
    void notReadyCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        // No Level seeded, no ContentSource registered - definitely BLOCKED.
        store.saveGrammar(grammar(ref, 1L, "U1", "文型", "接続", "N5"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("READY_FOR_DRAFT_PROMOTION");

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void sourceRightsBlockedCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        store.saveGrammar(grammar(ref, 1L, "U1", "文型", "接続", "N5"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        ContentSource source = contentSources.save(new ContentSource(ref, "test source", "1", null, null, null, null));
        source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 시작", false, null);
        source.reviewRights(ContentSourceRightsStatus.BLOCKED, "허용 불가", false, null);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // explanation too long (Grammar's mapping-length axis - no meaning-language equivalent exists)
    // ===================================================================================

    @Test
    void explanationTooLongCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        registerAllowedSource(ref);
        store.saveGrammar(new GrammarNormalizationResult(ref, 1L, "U1", "文型",
                new NormalizedGrammarExample(1, "前文です", null, "번역"), "g".repeat(1000), "n".repeat(1000),
                "接続", List.of(), new NormalizedJlptLevel("N5", "N5", "Level"), null, Map.of(), List.of(), true));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("READY_FOR_DRAFT_PROMOTION");

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // frontExample too long for production Example (Ticket 4E-8 hardening, MAJOR 1)
    // ===================================================================================

    @Test
    void frontExampleJapaneseTextOverTheProductionExampleMaxIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        registerAllowedSource(ref);
        store.saveGrammar(new GrammarNormalizationResult(ref, 1L, "U1", "文型",
                new NormalizedGrammarExample(1, "あ".repeat(1001), null, "번역"), "gloss", "nuance",
                "接続", List.of(), new NormalizedJlptLevel("N5", "N5", "Level"), null, Map.of(), List.of(), true));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("READY_FOR_DRAFT_PROMOTION");

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void frontExampleTranslationOverTheProductionExampleMaxIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        registerAllowedSource(ref);
        store.saveGrammar(new GrammarNormalizationResult(ref, 1L, "U1", "文型",
                new NormalizedGrammarExample(1, "前文です", null, "번".repeat(1001)), "gloss", "nuance",
                "接続", List.of(), new NormalizedJlptLevel("N5", "N5", "Level"), null, Map.of(), List.of(), true));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("READY_FOR_DRAFT_PROMOTION");

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void frontExampleReadingOverTheProductionExampleMaxIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        registerAllowedSource(ref);
        store.saveGrammar(new GrammarNormalizationResult(ref, 1L, "U1", "文型",
                new NormalizedGrammarExample(1, "前文です", "ま".repeat(1001), "번역"), "gloss", "nuance",
                "接続", List.of(), new NormalizedJlptLevel("N5", "N5", "Level"), null, Map.of(), List.of(), true));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("READY_FOR_DRAFT_PROMOTION");

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // frontExample reading preservation (Ticket 4E-8 hardening, MAJOR 2)
    // ===================================================================================

    @Test
    void frontExampleReadingIsPreservedVerbatimOnTheProductionExampleWhenPresent() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveGrammar(new GrammarNormalizationResult(CANONICAL_JLPT_MAX_SOURCE_REF, 900020L, "U-reading", "文型",
                new NormalizedGrammarExample(1, "前文です", "ぜんぶんです", "번역"), "gloss", "nuance",
                "接続", List.of(), new NormalizedJlptLevel("N5", "N5", "Level"), null, Map.of(), List.of(), true));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900020L);
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, 900020L, "", List.of("UnitID"), List.of("U-reading"));

        NormalizedGrammarCandidatePromotionResult result = promotion.promote(NormalizedCandidateType.GRAMMAR,
                candidate.getId(), candidate.getNormalizedAt());

        ContentItem item = contentItems.findById(result.contentItemId()).orElseThrow();
        assertThat(item.getExamples()).hasSize(1);
        assertThat(item.getExamples().get(0).getReading()).isEqualTo("ぜんぶんです");
    }

    // ===================================================================================
    // unmappable Level
    // ===================================================================================

    @Test
    void unmappableLevelCandidateIsRejectedWithNoProductionWrites() {
        seedN5Level();
        registerAllowedCanonicalSource();
        // N9 is never seeded by anything.
        store.saveGrammar(grammar(CANONICAL_JLPT_MAX_SOURCE_REF, 900002L, "U1", "文型", "接続", "N9"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900002L);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // normalization fatal
    // ===================================================================================

    @Test
    void fatalNormalizationCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        GrammarNormalizationResult base = grammar(ref, 1L, "U1", "文型", "接続", "N5");
        store.saveGrammar(new GrammarNormalizationResult(base.sourceRef(), base.sourceNoteId(), base.unitId(),
                base.pattern(), base.frontExample(), base.meaningGloss(), base.nuance(), base.connection(),
                base.confusablePatterns(), base.level(), base.rawKind(), base.unknownFields(),
                List.of(new GrammarNormalizationWarning(GrammarNormalizationIssue.MISSING_MEANING_GLOSS, "fatal")),
                false));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        registerAllowedSource(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // unresolved / SAME_CONTENT pair
    // ===================================================================================

    @Test
    void unreviewedPairCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        registerAllowedSource(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, pair.getLeftCandidate().getId(),
                pair.getLeftCandidate().getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void sameContentPairCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        registerAllowedSource(ref);
        reviewService.submitDecision(NormalizedCandidateType.GRAMMAR, submission(pair, HumanReviewDecision.SAME_CONTENT),
                admin("same-content"));
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, pair.getLeftCandidate().getId(),
                pair.getLeftCandidate().getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);
        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, pair.getRightCandidate().getId(),
                pair.getRightCandidate().getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // already-promoted / unlinked-record-reuse
    // ===================================================================================

    @Test
    void alreadyPromotedCandidateIsRejectedAndNeverDuplicatesTheContentItem() {
        String ref = ref();
        seedN5Level();
        store.saveGrammar(grammar(ref, 42L, "U1", "文型", "接続", "N5"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        registerAllowedSource(ref);

        ContentItem existingItem = contentItems.save(new ContentItem("already-promoted-" + UUID.randomUUID(),
                ContentType.GRAMMAR, ref, false));
        ImportedSourceRecord existingRecord = new ImportedSourceRecord(ref, GRAMMAR_NOTE_TYPE, 42L, "N5", "", "f", "v");
        existingRecord.linkContentItem(existingItem);
        importedSourceRecords.save(existingRecord);
        long contentItemsBefore = contentItems.count();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("ALREADY_PROMOTED");

        assertThat(contentItems.count()).isEqualTo(contentItemsBefore);
        assertThat(importedSourceRecords.findByContentItemIdOrderById(existingItem.getId())).hasSize(1);
    }

    @Test
    void anUnlinkedExistingImportedSourceRecordIsReusedNotDuplicatedOnPromotion() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveGrammar(grammar(CANONICAL_JLPT_MAX_SOURCE_REF, 900003L, "U1", "文型", "接続", "N5"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900003L);

        ImportedSourceRecord bareRecord = importedSourceRecords.save(
                new ImportedSourceRecord(CANONICAL_JLPT_MAX_SOURCE_REF, GRAMMAR_NOTE_TYPE, 900003L, "N5", "", "f", "v"));
        Long bareRecordId = bareRecord.getId();
        long recordsBefore = importedSourceRecords.count();
        long itemsBefore = contentItems.count();

        promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(), candidate.getNormalizedAt());

        assertThat(importedSourceRecords.count()).isEqualTo(recordsBefore);
        assertThat(contentItems.count()).isEqualTo(itemsBefore + 1);
        ImportedSourceRecord reused = importedSourceRecords.findById(bareRecordId).orElseThrow();
        assertThat(reused.getContentItem()).isNotNull();
        assertThat(reused.getFieldNames()).isEqualTo("f");
        assertThat(reused.getFieldValues()).isEqualTo("v");
    }

    // ===================================================================================
    // wrong candidate type / missing candidate
    // ===================================================================================

    @Test
    void vocabularyCandidateIsRejectedSafelyWithoutAnyRepositoryAccess() {
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, -1L, Instant.now()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("GRAMMAR");

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void missingCandidateIsRejectedCleanly() {
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, -1L, Instant.now()))
                .isInstanceOf(java.util.NoSuchElementException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // stale admin intent
    // ===================================================================================

    @Test
    void staleCandidateRevisionIsRejectedWithNoProductionWritesEvenWhenStillReady() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveGrammar(grammar(CANONICAL_JLPT_MAX_SOURCE_REF, 900012L, "U-stale", "文型", "接続", "N5"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900012L);
        Instant renderedNormalizedAt = candidate.getNormalizedAt();

        store.saveGrammar(grammar(CANONICAL_JLPT_MAX_SOURCE_REF, 900012L, "U-stale", "変わった文型", "変わった接続", "N5"));
        NormalizedContentCandidate reNormalized = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900012L);
        assertThat(reNormalized.getId()).isEqualTo(candidate.getId());
        assertThat(reNormalized.getNormalizedAt())
                .as("this test's premise requires normalizedAt to actually change on re-save - if this "
                        + "fails, the test environment's clock resolution is too coarse to prove staleness here")
                .isNotEqualTo(renderedNormalizedAt);
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, 900012L, "", List.of("Pattern"), List.of("変わった文型"));
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                renderedNormalizedAt))
                .isInstanceOf(NormalizedCandidatePromotionStaleException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void freshnessPassingAloneDoesNotBypassReadinessRecomputation() {
        String ref = ref();
        seedN5Level();
        store.saveGrammar(grammar(ref, 1L, "U1", "文型", "接続", "N5"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("READY_FOR_DRAFT_PROMOTION");

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void pairReviewStateChangeBetweenRenderAndPostIsCaughtByFreshReadinessRecomputationNotAFreshnessToken() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        registerAllowedSource(ref);
        Instant renderedNormalizedAt = pair.getLeftCandidate().getNormalizedAt();

        reviewService.submitDecision(NormalizedCandidateType.GRAMMAR, submission(pair, HumanReviewDecision.NEEDS_FOLLOWUP),
                admin("pair-change"));
        NormalizedContentCandidate afterReview = candidateRepository.findById(pair.getLeftCandidate().getId()).orElseThrow();
        assertThat(afterReview.getNormalizedAt())
                .as("submitting a pair review decision must never itself change the candidate's own "
                        + "normalizedAt - only re-normalization does")
                .isEqualTo(renderedNormalizedAt);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, pair.getLeftCandidate().getId(),
                renderedNormalizedAt))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("PAIR_NEEDS_FOLLOWUP");

        assertThat(productionCounts()).isEqualTo(before);
    }
}
