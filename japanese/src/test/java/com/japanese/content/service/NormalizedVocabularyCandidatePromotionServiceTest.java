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
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.ReviewStatus;
import com.japanese.content.entity.Word;
import com.japanese.content.importer.NormalizedExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationIssue;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.importer.VocabularyNormalizationWarning;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentSourceRepository;
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
 * JLPT-MAX Ticket 4E-1: scenario coverage for {@link NormalizedVocabularyCandidatePromotionService}.
 * Every candidate is built through {@code NormalizedCandidateStore}/{@code NormalizedCandidateConflictAnalyzer}/
 * {@code NormalizedCandidatePairReviewService}, matching {@code NormalizedCandidatePromotionReadinessServiceTest}'s
 * (Ticket 4D) own fixture conventions - this class never constructs a candidate by any other means.
 *
 * <p>Rejection scenarios each assert the exact production-write inventory (ContentItem/Word/Meaning/
 * Example/ImportedSourceRecord/Grammar counts) is unchanged, proving the rejection happened before
 * any write - not merely that the returned/thrown outcome looked like a rejection.
 *
 * <p>Post-hardening (MAJOR 1/2 fixes), every {@code promote(...)} call now takes a third
 * {@code expectedNormalizedAt} argument; scenarios that are not specifically testing staleness always
 * pass the candidate's own current {@code normalizedAt} so they still exercise exactly the rejection
 * reason each test name describes, not an incidental staleness rejection.
 */
@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class NormalizedVocabularyCandidatePromotionServiceTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired NormalizedCandidatePairReviewService reviewService;
    @Autowired NormalizedVocabularyCandidatePromotionService promotion;
    @Autowired NormalizedContentCandidateRepository candidateRepository;
    @Autowired NormalizedCandidateMatchPairRepository pairRepository;
    @Autowired UserAccountRepository accounts;
    @Autowired ContentSourceRepository contentSources;
    @Autowired ContentItemRepository contentItems;
    @Autowired ImportedSourceRecordRepository importedSourceRecords;
    @Autowired LevelRepository levels;
    @Autowired JdbcClient jdbcClient;
    @Autowired ObjectMapper objectMapper;

    /** Matches {@code ApkgVocabularyImporter.SOURCE_REF} / {@code NormalizedVocabularyMeaningLanguagePolicy}. */
    private static final String CANONICAL_JLPT_MAX_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";
    private static final String VOCABULARY_NOTE_TYPE = "JLPT MAX덱 어휘";

    private String ref() {
        return "promotion-write-test-" + UUID.randomUUID();
    }

    private UserAccount admin(String suffix) {
        return accounts.save(new UserAccount("promotion-admin-" + suffix + "-" + UUID.randomUUID(), null, "hash",
                "Admin " + suffix, UserRole.ADMIN));
    }

    /** See {@code NormalizedCandidatePromotionReadinessServiceTest.seedN5Level} for why this is idempotent. */
    private void seedN5Level() {
        levels.findBySystemAndCode("JLPT", "N5").orElseGet(() -> levels.save(new Level("JLPT", "N5", "JLPT N5")));
    }

    private void registerAllowedSource(String ref) {
        ContentSource source = contentSources.save(new ContentSource(ref, "test source", "1", null, null, null, null));
        source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 시작", false, null);
        source.reviewRights(ContentSourceRightsStatus.ALLOWED, "허용", false, null);
    }

    /** See {@code NormalizedCandidatePromotionReadinessServiceTest.registerAllowedCanonicalSource} for the full rationale. */
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
                .findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(c -> c.getSourceRef().equals(ref))
                .toList();
        assertThat(candidates).hasSize(1);
        return candidates.get(0);
    }

    /**
     * Unlike {@link #onlyCandidate(String)}, also filters by {@code sourceNoteId} - required for the
     * canonical JLPT-MAX sourceRef, which this class's own (non-{@code @Transactional}) atomicity/
     * concurrency test classes in this same suite run also legitimately use (with different note ids)
     * and permanently commit rows for, so more than one candidate can genuinely exist for that shared
     * sourceRef across the whole test JVM run.
     */
    private NormalizedContentCandidate onlyCandidate(String ref, long sourceNoteId) {
        return candidateRepository.findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(c -> c.getSourceRef().equals(ref) && c.getSourceNoteId() == sourceNoteId)
                .findFirst()
                .orElseThrow();
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

    private DecisionSubmission submission(NormalizedCandidateMatchPair pair, HumanReviewDecision decision) {
        return new DecisionSubmission(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId(), decision, null,
                pair.getGeneratedAt(), pair.getAssessment(), null);
    }

    private VocabularyNormalizationResult vocab(String ref, long noteId, String entryId, String expression,
            String reading, String partOfSpeech, String level, String meaning) {
        return new VocabularyNormalizationResult(
                ref, noteId, entryId, expression, reading, partOfSpeech, null,
                List.of(new NormalizedMeaning(1, meaning)),
                List.of(new NormalizedExample(1, meaning, expression + "の例文です",
                        reading + "のよみ", meaning + " translation")),
                new NormalizedJlptLevel(level, level, "WordJLPT"), expression, reading, Map.of(),
                List.of(), true);
    }

    /**
     * Seeds a {@code private_apkg_notes} row with genuinely raw-shaped field names/values - the
     * ground truth {@link NormalizedVocabularyCandidatePromotionService} must use (via
     * {@link PrivateApkgNoteProvenanceReader}) instead of any normalized-candidate-derived synthetic
     * substitute, whenever a promotion must create a brand-new {@code ImportedSourceRecord}.
     */
    private void seedPrivateApkgNote(String sourceRef, long sourceNoteId, String tags,
            List<String> fieldNames, List<String> fieldValues) {
        jdbcClient.sql("insert into private_apkg_notes (source_ref, source_file, source_version, source_note_id, "
                        + "model_id, note_type, category, anki_guid, deck_paths, card_metadata, tags, field_names, "
                        + "field_values, normalized_values, audio_reference_count, extracted_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .param(sourceRef).param("fixture.apkg").param("1").param(sourceNoteId).param(1L)
                .param(VOCABULARY_NOTE_TYPE).param("VOCABULARY").param("guid-" + sourceNoteId)
                .param("[]").param("{}").param(tags)
                .param(objectMapper.writeValueAsString(fieldNames))
                .param(objectMapper.writeValueAsString(fieldValues))
                .param("{}").param(0).param(Instant.now())
                .update();
    }

    private ProductionCounts productionCounts() {
        return new ProductionCounts(
                contentItems.count(),
                jdbcClient.sql("select count(*) from words").query(Long.class).single(),
                jdbcClient.sql("select count(*) from meanings").query(Long.class).single(),
                jdbcClient.sql("select count(*) from examples").query(Long.class).single(),
                jdbcClient.sql("select count(*) from grammars").query(Long.class).single(),
                importedSourceRecords.count());
    }

    private record ProductionCounts(long contentItems, long words, long meanings, long examples, long grammars,
            long importedSourceRecords) {
    }

    // ===================================================================================
    // 1. successful clean canonical-source N5 Vocabulary promotion (also covers MAJOR-2
    //    requirement 7: GET/render revision == current locked revision -> may proceed when READY)
    // ===================================================================================

    @Test
    void cleanCanonicalSourceVocabularyCandidateIsPromotedToAProductionDraft() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, 900001L, "E1", "本", "ほん", "noun", "N5", "책"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900001L);
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, 900001L, " jlpt::n5 raw::tag ",
                List.of("EntryID", "Word", "Reading", "Meaning"), List.of("E1", "本", "ほん", "책"));
        // "sample" profile's SampleContentDataLoader already seeds a few ContentItem/Grammar rows at
        // startup (shared, committed state across this whole test JVM run) - so every assertion below
        // is scoped to the one row this call itself created, or a before/after delta, never an
        // absolute count.
        ProductionCounts before = productionCounts();

        NormalizedVocabularyCandidatePromotionResult result = promotion.promote(NormalizedCandidateType.VOCABULARY,
                candidate.getId(), candidate.getNormalizedAt());

        assertThat(result.slug()).matches("word-[0-9a-f-]{36}");

        ProductionCounts after = productionCounts();
        assertThat(after.contentItems()).isEqualTo(before.contentItems() + 1);
        assertThat(after.words()).isEqualTo(before.words() + 1);
        assertThat(after.meanings()).isEqualTo(before.meanings() + 1);
        assertThat(after.examples()).isEqualTo(before.examples() + 1);
        assertThat(after.importedSourceRecords()).isEqualTo(before.importedSourceRecords() + 1);
        assertThat(after.grammars()).isEqualTo(before.grammars());

        ContentItem item = contentItems.findById(result.contentItemId()).orElseThrow();
        assertThat(item.getSlug()).isEqualTo(result.slug());
        assertThat(item.getType()).isEqualTo(ContentType.WORD);
        assertThat(item.isPublished()).isFalse();
        assertThat(item.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(item.getSourceRef()).isEqualTo(CANONICAL_JLPT_MAX_SOURCE_REF);

        Word word = item.getWord();
        assertThat(word).isNotNull();
        assertThat(word.getExpression()).isEqualTo("本");
        assertThat(word.getReading()).isEqualTo("ほん");
        assertThat(word.getPartOfSpeech()).isEqualTo("noun");
        assertThat(word.getMeanings()).hasSize(1);
        assertThat(word.getMeanings().get(0).getText()).isEqualTo("책");
        assertThat(word.getMeanings().get(0).getLanguageTag()).isEqualTo("ko");

        assertThat(item.getExamples()).hasSize(1);
        assertThat(item.getExamples().get(0).getJapaneseText()).isEqualTo("本の例文です");
        assertThat(item.getExamples().get(0).getMeaning()).isEqualTo(word.getMeanings().get(0));

        assertThat(item.getLevels()).hasSize(1);
        assertThat(item.getLevels().iterator().next().getCode()).isEqualTo("N5");

        List<ImportedSourceRecord> records = importedSourceRecords.findByContentItemIdOrderById(item.getId());
        assertThat(records).hasSize(1);
        ImportedSourceRecord record = records.get(0);
        assertThat(record.getSourceRef()).isEqualTo(CANONICAL_JLPT_MAX_SOURCE_REF);
        assertThat(record.getNoteType()).isEqualTo(VOCABULARY_NOTE_TYPE);
        assertThat(record.getSourceNoteId()).isEqualTo(900001L);
        assertThat(record.getContentItem()).isEqualTo(item);
    }

    // ===================================================================================
    // MAJOR 1 hardening: truthful provenance (test requirements 1-2)
    // ===================================================================================

    @Test
    void newlyCreatedImportedSourceRecordUsesGenuineRawSourceDataNotSyntheticNormalizedFields() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, 900010L, "E-truthful", "食べる", "たべる", "verb", "N5",
                "먹다"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900010L);
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, 900010L, " raw::provenance::tag ",
                List.of("EntryID", "Word", "Reading", "PitchAccent", "Meaning"),
                List.of("E-truthful", "食べる", "たべる", "0", "먹다 / to eat"));

        promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(), candidate.getNormalizedAt());

        ImportedSourceRecord record = importedSourceRecords
                .findBySourceRefAndNoteTypeAndSourceNoteId(CANONICAL_JLPT_MAX_SOURCE_REF, VOCABULARY_NOTE_TYPE, 900010L)
                .orElseThrow();
        // Genuine raw payload from private_apkg_notes, verbatim (re-joined with the established
        // U+001F delimiter) - never the old synthetic 6-field NormalizedCandidateId/.../LevelCode list.
        assertThat(record.getTags()).isEqualTo(" raw::provenance::tag ");
        assertThat(record.getFieldNames()).isEqualTo("EntryIDWordReadingPitchAccentMeaning");
        assertThat(record.getFieldValues()).isEqualTo("E-truthful食べるたべる0먹다 / to eat");
        // Never falsely labeled as though it were the normalized candidate's own field set.
        assertThat(record.getFieldNames()).doesNotContain("NormalizedCandidateId", "LevelCode");
        assertThat(record.getFieldValues()).doesNotContain(candidate.getId().toString());
    }

    @Test
    void missingTruthfulProvenanceBlocksPromotionWithNoProductionWrites() {
        // READY_FOR_DRAFT_PROMOTION in every other respect, but deliberately NO private_apkg_notes row
        // seeded and no pre-existing ImportedSourceRecord - the exact "information genuinely missing"
        // case the hardening instructions require blocking rather than fabricating a substitute for.
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, 900011L, "E-missing", "語", "ご", "noun", "N5",
                "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900011L);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("private_apkg_notes");

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // 2. candidate not READY
    // ===================================================================================

    @Test
    void notReadyCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        // No Level seeded, no ContentSource registered - definitely BLOCKED.
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("READY_FOR_DRAFT_PROMOTION");

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // 3. rights not allowed
    // ===================================================================================

    @Test
    void sourceRightsBlockedCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        ContentSource source = contentSources.save(new ContentSource(ref, "test source", "1", null, null, null, null));
        source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 시작", false, null);
        source.reviewRights(ContentSourceRightsStatus.BLOCKED, "허용 불가", false, null);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // 4. unknown Vocabulary source / unresolved meaning language
    // ===================================================================================

    @Test
    void unknownSourceMeaningLanguageUnresolvedCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        registerAllowedSource(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // 5. unmappable Level
    // ===================================================================================

    @Test
    void unmappableLevelCandidateIsRejectedWithNoProductionWrites() {
        seedN5Level();
        registerAllowedCanonicalSource();
        // N9 is never seeded by anything (see NormalizedCandidatePromotionReadinessServiceTest.r_unmappableJlptLevelIsBlocked).
        store.saveVocabulary(new VocabularyNormalizationResult(CANONICAL_JLPT_MAX_SOURCE_REF, 900002L, "E1", "語", "ご",
                "noun", null, List.of(new NormalizedMeaning(1, "meaning")), List.of(),
                new NormalizedJlptLevel("N9", "N9", "WordJLPT"), "語", "ご", Map.of(), List.of(), true));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900002L);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // 6. normalization fatal / field mapping blocker
    // ===================================================================================

    @Test
    void fatalNormalizationCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        VocabularyNormalizationResult base = vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning");
        store.saveVocabulary(new VocabularyNormalizationResult(base.sourceRef(), base.sourceNoteId(), base.entryId(),
                base.expression(), base.reading(), base.partOfSpeech(), base.pitchAccent(), base.meanings(),
                base.examples(), base.level(), base.normalizedSearchExpression(), base.normalizedSearchReading(),
                base.preservedExtraFields(),
                List.of(new VocabularyNormalizationWarning(VocabularyNormalizationIssue.MISSING_ENTRY_ID, "fatal")),
                base.validForPromotion()));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        registerAllowedSource(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // 7. unresolved pair
    // ===================================================================================

    @Test
    void unreviewedPairCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        registerAllowedSource(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, pair.getLeftCandidate().getId(),
                pair.getLeftCandidate().getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // 8. SAME_CONTENT canonical-selection-required candidate
    // ===================================================================================

    @Test
    void sameContentPairCandidateIsRejectedWithNoProductionWrites() {
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        registerAllowedSource(ref);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY, submission(pair, HumanReviewDecision.SAME_CONTENT),
                admin("same-content"));
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, pair.getLeftCandidate().getId(),
                pair.getLeftCandidate().getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);
        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, pair.getRightCandidate().getId(),
                pair.getRightCandidate().getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // 9. already-promoted candidate / linked provenance -> no duplicate ContentItem
    // ===================================================================================

    @Test
    void alreadyPromotedCandidateIsRejectedAndNeverDuplicatesTheContentItem() {
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 42L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        registerAllowedSource(ref);

        ContentItem existingItem = contentItems.save(new ContentItem("already-promoted-" + UUID.randomUUID(),
                ContentType.WORD, ref, false));
        ImportedSourceRecord existingRecord = new ImportedSourceRecord(ref, VOCABULARY_NOTE_TYPE, 42L, "N5", "", "f", "v");
        existingRecord.linkContentItem(existingItem);
        importedSourceRecords.save(existingRecord);
        long contentItemsBefore = contentItems.count();

        // ALREADY_PROMOTED short-circuits readiness's own overallStatus regardless of any other
        // issue (see NormalizedCandidatePromotionReadinessService.evaluate's `overall` ternary), so
        // this rejection is caught by promote()'s general "not exactly READY_FOR_DRAFT_PROMOTION"
        // check - the exact same generic message every BLOCKED rejection uses, just with this
        // overallStatus value inside it.
        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("ALREADY_PROMOTED");

        assertThat(contentItems.count()).isEqualTo(contentItemsBefore);
        assertThat(importedSourceRecords.findByContentItemIdOrderById(existingItem.getId())).hasSize(1);
    }

    @Test
    void anUnlinkedExistingImportedSourceRecordIsReusedNotDuplicatedOnPromotion() {
        // Must be the canonical sourceRef so this candidate can actually reach
        // READY_FOR_DRAFT_PROMOTION (an unlinked record alone does not set ALREADY_PROMOTED - see
        // NormalizedCandidatePromotionReadinessService's ExistingProductionLinkStatus logic - so every
        // other axis, including meaning-language, must still resolve for promote() to succeed here).
        // No private_apkg_notes row is seeded here on purpose: an existing (even unlinked)
        // ImportedSourceRecord must never trigger a private-staging lookup at all - see
        // NormalizedVocabularyCandidatePromotionService's own "Provenance" javadoc.
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, 900003L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900003L);

        ImportedSourceRecord bareRecord = importedSourceRecords.save(
                new ImportedSourceRecord(CANONICAL_JLPT_MAX_SOURCE_REF, VOCABULARY_NOTE_TYPE, 900003L, "N5", "", "f", "v"));
        Long bareRecordId = bareRecord.getId();
        long recordsBefore = importedSourceRecords.count();
        long itemsBefore = contentItems.count();

        promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(), candidate.getNormalizedAt());

        // Exactly one new row, not two - the bare row above must have been reused/linked, never
        // left in place alongside a second, freshly-inserted ImportedSourceRecord for the same
        // (sourceRef, noteType, sourceNoteId) identity (which uk_imported_source_record would reject
        // anyway, but this proves the code path never even attempts that insert).
        assertThat(importedSourceRecords.count()).isEqualTo(recordsBefore);
        assertThat(contentItems.count()).isEqualTo(itemsBefore + 1);
        ImportedSourceRecord reused = importedSourceRecords.findById(bareRecordId).orElseThrow();
        assertThat(reused.getContentItem()).isNotNull();
        // Untouched - still the placeholder "f"/"v" seeded above, never overwritten by any lookup.
        assertThat(reused.getFieldNames()).isEqualTo("f");
        assertThat(reused.getFieldValues()).isEqualTo("v");
    }

    // ===================================================================================
    // 10. wrong candidate type / Grammar
    // ===================================================================================

    @Test
    void grammarCandidateIsRejectedSafelyWithoutAnyRepositoryAccess() {
        // Deliberately an id that does not exist at all: promote() must reject on candidateType
        // alone, before ever attempting to look the id up - proving no Grammar mapping is attempted.
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.GRAMMAR, -1L, Instant.now()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("VOCABULARY");

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // 11. missing candidate
    // ===================================================================================

    @Test
    void missingCandidateIsRejectedCleanly() {
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, -1L, Instant.now()))
                .isInstanceOf(java.util.NoSuchElementException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // MAJOR 2 hardening: stale admin intent (test requirements 8-11)
    // ===================================================================================

    @Test
    void staleCandidateRevisionIsRejectedWithNoProductionWritesEvenWhenStillReady() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, 900012L, "E-stale", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900012L);
        Instant renderedNormalizedAt = candidate.getNormalizedAt();

        // Re-normalize the SAME (sourceRef, sourceNoteId, VOCABULARY) candidate in place - simulates
        // another process re-normalizing it after the admin's GET render, but before their POST. The
        // new content is deliberately ALSO fully READY_FOR_DRAFT_PROMOTION-eligible (still N5, still
        // has a meaning) - this must still be rejected on staleness alone, proving readiness passing
        // is never sufficient by itself.
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, 900012L, "E-stale", "変わった語", "かわったご", "noun",
                "N5", "meaning"));
        NormalizedContentCandidate reNormalized = onlyCandidate(CANONICAL_JLPT_MAX_SOURCE_REF, 900012L);
        assertThat(reNormalized.getId()).isEqualTo(candidate.getId());
        assertThat(reNormalized.getNormalizedAt())
                .as("this test's premise requires normalizedAt to actually change on re-save - if this "
                        + "fails, the test environment's clock resolution is too coarse to prove staleness here")
                .isNotEqualTo(renderedNormalizedAt);
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, 900012L, "", List.of("Word"), List.of("変わった語"));
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(),
                renderedNormalizedAt))
                .isInstanceOf(NormalizedCandidatePromotionStaleException.class);

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void freshnessPassingAloneDoesNotBypassReadinessRecomputation() {
        // normalizedAt matches (freshness passes) but the candidate is otherwise BLOCKED (no rights
        // registered) - must still be rejected via the readiness path, not silently promoted.
        String ref = ref();
        seedN5Level();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "noun", "N5", "meaning"));
        NormalizedContentCandidate candidate = onlyCandidate(ref);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("READY_FOR_DRAFT_PROMOTION");

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void pairReviewStateChangeBetweenRenderAndPostIsCaughtByFreshReadinessRecomputationNotAFreshnessToken() {
        // A NEEDS_FOLLOWUP decision submitted on this candidate's pair between "render" and "POST"
        // never touches the candidate's own normalizedAt - so the freshness token this hardening adds
        // still matches (proving that token is NOT what catches this), and the rejection instead comes
        // purely from readiness's own always-fresh (never GET-cached) pair/review recomputation - see
        // NormalizedVocabularyCandidatePromotionService's class javadoc "Freshness" section.
        String ref = ref();
        seedN5Level();
        NormalizedCandidateMatchPair pair = seedDuplicatePair(ref);
        registerAllowedSource(ref);
        Instant renderedNormalizedAt = pair.getLeftCandidate().getNormalizedAt();

        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY, submission(pair, HumanReviewDecision.NEEDS_FOLLOWUP),
                admin("pair-change"));
        NormalizedContentCandidate afterReview = candidateRepository.findById(pair.getLeftCandidate().getId()).orElseThrow();
        assertThat(afterReview.getNormalizedAt())
                .as("submitting a pair review decision must never itself change the candidate's own "
                        + "normalizedAt - only re-normalization does")
                .isEqualTo(renderedNormalizedAt);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, pair.getLeftCandidate().getId(),
                renderedNormalizedAt))
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("PAIR_NEEDS_FOLLOWUP");

        assertThat(productionCounts()).isEqualTo(before);
    }
}
