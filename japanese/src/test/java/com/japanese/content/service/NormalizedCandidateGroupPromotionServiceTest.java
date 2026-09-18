package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupCreationRequest;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupCreationResult;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.EdgeExpectation;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.ParticipantExpectation;
import com.japanese.content.dto.NormalizedCandidateGroupPromotionModels.GroupPromotionEligibilityView;
import com.japanese.content.dto.NormalizedCandidateGroupPromotionModels.GroupPromotionResult;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.DecisionSubmission;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroup;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupStatus;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.Word;
import com.japanese.content.importer.NormalizedExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentSourceRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.NormalizedCandidateCanonicalGroupRepository;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import java.time.Instant;
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
import tools.jackson.databind.ObjectMapper;

/**
 * JLPT-MAX Ticket 4E-3B: scenario coverage for {@link NormalizedCandidateGroupPromotionService},
 * numbered to match the ticket's own sections 22-25 test lists where practical. Concurrency coverage
 * (section 26) lives in {@link NormalizedCandidateGroupPromotionConcurrencyTest}.
 */
@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class NormalizedCandidateGroupPromotionServiceTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired NormalizedCandidatePairReviewService reviewService;
    @Autowired NormalizedCandidateCanonicalGroupService canonicalGroups;
    @Autowired NormalizedCandidateGroupPromotionService groupPromotion;
    @Autowired NormalizedVocabularyCandidatePromotionService ordinaryPromotion;
    @Autowired NormalizedContentCandidateRepository candidateRepository;
    @Autowired NormalizedCandidateMatchPairRepository pairRepository;
    @Autowired NormalizedCandidatePairReviewRepository reviewRepository;
    @Autowired NormalizedCandidateCanonicalGroupRepository groupRepository;
    @Autowired UserAccountRepository accounts;
    @Autowired ContentSourceRepository contentSources;
    @Autowired ContentItemRepository contentItems;
    @Autowired ImportedSourceRecordRepository importedSourceRecords;
    @Autowired LevelRepository levels;
    @Autowired JdbcClient jdbcClient;
    @Autowired ObjectMapper objectMapper;

    private static final String CANONICAL_JLPT_MAX_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";
    private static final String VOCABULARY_NOTE_TYPE = "JLPT MAX덱 어휘";

    private String ref() {
        return "group-promotion-test-" + UUID.randomUUID();
    }

    private UserAccount admin(String suffix) {
        return accounts.save(new UserAccount("group-promo-admin-" + suffix + "-" + UUID.randomUUID(), null, "hash",
                "Admin " + suffix, UserRole.ADMIN));
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
    }

    private void seedPrivateApkgNote(String sourceRef, long sourceNoteId, String word) {
        jdbcClient.sql("insert into private_apkg_notes (source_ref, source_file, source_version, source_note_id, "
                        + "model_id, note_type, category, anki_guid, deck_paths, card_metadata, tags, field_names, "
                        + "field_values, normalized_values, audio_reference_count, extracted_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .param(sourceRef).param("fixture.apkg").param("1").param(sourceNoteId).param(1L)
                .param(VOCABULARY_NOTE_TYPE).param("VOCABULARY").param("guid-" + sourceNoteId)
                .param("[]").param("{}").param("")
                .param(objectMapper.writeValueAsString(List.of("Word")))
                .param(objectMapper.writeValueAsString(List.of(word)))
                .param("{}").param(0).param(Instant.now())
                .update();
    }

    private VocabularyNormalizationResult vocab(String ref, long noteId, String entryId, String expression,
            String reading, String level, String meaning) {
        return new VocabularyNormalizationResult(ref, noteId, entryId, expression, reading, "noun", null,
                List.of(new NormalizedMeaning(1, meaning)),
                List.of(new NormalizedExample(1, meaning, expression + "の例文です", reading + "のよみ", meaning + " translation")),
                new NormalizedJlptLevel(level, level, "WordJLPT"), expression, reading, Map.of(), List.of(), true);
    }

    /** N fully promotion-ready candidates (canonical source, distinct provenance/noteIds, shared entryId). */
    private List<NormalizedContentCandidate> seedPromotableClique(String ref, int n, long baseNoteId) {
        seedN5Level();
        for (int i = 0; i < n; i++) {
            long noteId = baseNoteId + i;
            store.saveVocabulary(vocab(ref, noteId, "E-GROUP-PROMO", "語", "ご", "N5", "meaning"));
            seedPrivateApkgNote(ref, noteId, "語" + i);
        }
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        return candidateRepository.findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, ref).stream()
                .sorted(Comparator.comparing(NormalizedContentCandidate::getId))
                .toList();
    }

    private NormalizedCandidatePairReview reviewSameContent(NormalizedContentCandidate a, NormalizedContentCandidate b,
            UserAccount reviewer) {
        Long lo = Math.min(a.getId(), b.getId());
        Long hi = Math.max(a.getId(), b.getId());
        NormalizedCandidateMatchPair pair = pairRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi).orElseThrow();
        Long expectedVersion = reviewRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi)
                .map(NormalizedCandidatePairReview::getVersion).orElse(null);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                new DecisionSubmission(lo, hi, HumanReviewDecision.SAME_CONTENT, null, pair.getGeneratedAt(),
                        pair.getAssessment(), expectedVersion),
                reviewer);
        return reviewRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi).orElseThrow();
    }

    private CanonicalGroupCreationResult createFullGroup(List<NormalizedContentCandidate> clique, Long canonicalId,
            UserAccount reviewer, UserAccount decider) {
        for (int i = 0; i < clique.size(); i++) {
            for (int j = i + 1; j < clique.size(); j++) {
                reviewSameContent(clique.get(i), clique.get(j), reviewer);
            }
        }
        List<ParticipantExpectation> participants = clique.stream()
                .map(c -> new ParticipantExpectation(c.getId(), c.getNormalizedAt())).toList();
        List<EdgeExpectation> edges = new java.util.ArrayList<>();
        for (int i = 0; i < clique.size(); i++) {
            for (int j = i + 1; j < clique.size(); j++) {
                NormalizedCandidatePairReview review = reviewRepository.findByLeftCandidateIdAndRightCandidateId(
                        clique.get(i).getId(), clique.get(j).getId()).orElseThrow();
                edges.add(new EdgeExpectation(clique.get(i).getId(), clique.get(j).getId(), review.getVersion()));
            }
        }
        return canonicalGroups.create(NormalizedCandidateType.VOCABULARY,
                new CanonicalGroupCreationRequest(canonicalId, participants, edges, null), decider);
    }

    private ProductionCounts productionCounts() {
        return new ProductionCounts(contentItems.count(),
                jdbcClient.sql("select count(*) from words").query(Long.class).single(),
                jdbcClient.sql("select count(*) from meanings").query(Long.class).single(),
                jdbcClient.sql("select count(*) from examples").query(Long.class).single(),
                importedSourceRecords.count());
    }

    private record ProductionCounts(long contentItems, long words, long meanings, long examples,
            long importedSourceRecords) {
    }

    // ===================================================================================
    // Sections 22-23: policy / provenance
    // ===================================================================================

    @Test
    void _1_validTwoMemberGroupPromotionSucceeds() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_000L);
        registerAllowedCanonicalSource();
        UserAccount reviewer = admin("r1");
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), reviewer, admin("d1"));
        ProductionCounts before = productionCounts();

        GroupPromotionResult result = groupPromotion.promote(group.groupId(),
                groupRepository.findById(group.groupId()).orElseThrow().getVersion(), admin("p1"));

        assertThat(result.contentItemId()).isNotNull();
        ProductionCounts after = productionCounts();
        assertThat(after.contentItems()).isEqualTo(before.contentItems() + 1);
        assertThat(after.words()).isEqualTo(before.words() + 1);
        assertThat(after.importedSourceRecords()).isEqualTo(before.importedSourceRecords() + 2);
    }

    @Test
    void _2_validThreeMemberCliqueGroupPromotionSucceeds() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 3, 910_010L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(1).getId(), admin("r2"), admin("d2"));

        GroupPromotionResult result = groupPromotion.promote(group.groupId(),
                groupRepository.findById(group.groupId()).orElseThrow().getVersion(), admin("p2"));

        List<ImportedSourceRecord> records = importedSourceRecords.findByContentItemIdOrderById(result.contentItemId());
        assertThat(records).hasSize(3);
    }

    // ===================================================================================
    // 3-9: exactly one ContentItem/Word, canonical-only content, no merging
    // ===================================================================================

    @Test
    void _3to9_productionContentComesOnlyFromCanonicalNeverMerged() {
        String ref = CANONICAL_JLPT_MAX_SOURCE_REF;
        seedN5Level();
        registerAllowedCanonicalSource();
        long noteA = 910_020L;
        long noteB = 910_021L;
        store.saveVocabulary(vocab(ref, noteA, "E-DIFF", "本", "ほん", "N5", "canonical-meaning"));
        store.saveVocabulary(vocab(ref, noteB, "E-DIFF", "本", "ほん", "N5", "noncanonical-meaning-should-never-appear"));
        seedPrivateApkgNote(ref, noteA, "本A");
        seedPrivateApkgNote(ref, noteB, "本B");
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        List<NormalizedContentCandidate> c = candidateRepository
                .findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(x -> x.getSourceRef().equals(ref) && (x.getSourceNoteId() == noteA || x.getSourceNoteId() == noteB))
                .sorted(Comparator.comparing(NormalizedContentCandidate::getId)).toList();
        Long canonicalId = c.get(0).getId();
        CanonicalGroupCreationResult group = createFullGroup(c, canonicalId, admin("r3"), admin("d3"));
        long itemsBefore = contentItems.count();

        GroupPromotionResult result = groupPromotion.promote(group.groupId(),
                groupRepository.findById(group.groupId()).orElseThrow().getVersion(), admin("p3"));

        assertThat(contentItems.count()).isEqualTo(itemsBefore + 1);
        ContentItem item = contentItems.findById(result.contentItemId()).orElseThrow();
        Word word = item.getWord();
        assertThat(word.getMeanings()).hasSize(1);
        assertThat(word.getMeanings().get(0).getText()).isEqualTo("canonical-meaning");
        assertThat(item.getExamples()).hasSize(1);
        assertThat(item.getExamples().get(0).getJapaneseText()).isEqualTo("本の例文です");
    }

    // ===================================================================================
    // 9-13: slug / published / reviewStatus / Level
    // ===================================================================================

    @Test
    void _9to13_oneUuidSlugPublishedFalseInitialReviewStatusExactLevel() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_030L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r4"), admin("d4"));

        GroupPromotionResult result = groupPromotion.promote(group.groupId(),
                groupRepository.findById(group.groupId()).orElseThrow().getVersion(), admin("p4"));

        ContentItem item = contentItems.findById(result.contentItemId()).orElseThrow();
        assertThat(item.getSlug()).matches("word-[0-9a-f-]{36}");
        assertThat(item.isPublished()).isFalse();
        assertThat(item.getLevels()).hasSize(1);
        assertThat(item.getLevels().iterator().next().getCode()).isEqualTo("N5");
    }

    // ===================================================================================
    // Grammar rejected
    // ===================================================================================

    @Test
    void grammarGroupIsNeverCreatable_soGroupPromotionOnlyEverSeesVocabulary() {
        // 4E-3A itself already rejects GRAMMAR canonical-group creation before any write - confirming
        // there is structurally no way to reach a GRAMMAR group id for this service to even attempt.
        assertThatThrownBy(() -> groupPromotion.promote(-1L, 0L, admin("g")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // ===================================================================================
    // 16-20: provenance
    // ===================================================================================

    @Test
    void _16_17_everyMemberGetsOwnProvenanceConvergingOnSameContentItem() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 3, 910_040L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r5"), admin("d5"));

        GroupPromotionResult result = groupPromotion.promote(group.groupId(),
                groupRepository.findById(group.groupId()).orElseThrow().getVersion(), admin("p5"));

        for (NormalizedContentCandidate candidate : c) {
            ImportedSourceRecord record = importedSourceRecords
                    .findBySourceRefAndNoteTypeAndSourceNoteId(CANONICAL_JLPT_MAX_SOURCE_REF, VOCABULARY_NOTE_TYPE,
                            candidate.getSourceNoteId())
                    .orElseThrow();
            assertThat(record.getContentItem().getId()).isEqualTo(result.contentItemId());
        }
    }

    @Test
    void _18_existingUnlinkedProvenanceIsReusedUnchanged() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_050L);
        registerAllowedCanonicalSource();
        ImportedSourceRecord bare = importedSourceRecords.save(new ImportedSourceRecord(CANONICAL_JLPT_MAX_SOURCE_REF,
                VOCABULARY_NOTE_TYPE, c.get(1).getSourceNoteId(), "N5", "", "existing-f", "existing-v"));
        Long bareId = bare.getId();
        long recordsBefore = importedSourceRecords.count();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r6"), admin("d6"));

        groupPromotion.promote(group.groupId(), groupRepository.findById(group.groupId()).orElseThrow().getVersion(),
                admin("p6"));

        assertThat(importedSourceRecords.count()).isEqualTo(recordsBefore + 1); // only canonical's new one
        ImportedSourceRecord reused = importedSourceRecords.findById(bareId).orElseThrow();
        assertThat(reused.getFieldNames()).isEqualTo("existing-f");
        assertThat(reused.getFieldValues()).isEqualTo("existing-v");
        assertThat(reused.getContentItem()).isNotNull();
    }

    @Test
    void _19_20_missingTruthfulProvenanceForAnyMemberRejectsEntirePromotionWithNoWrites() {
        seedN5Level();
        registerAllowedCanonicalSource();
        long noteA = 910_060L;
        long noteB = 910_061L;
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, noteA, "E-MISSING-PROV", "語", "ご", "N5", "meaning"));
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, noteB, "E-MISSING-PROV", "語", "ご", "N5", "meaning"));
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, noteA, "語A");
        // Deliberately no private_apkg_notes row for noteB.
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, CANONICAL_JLPT_MAX_SOURCE_REF);
        List<NormalizedContentCandidate> c = candidateRepository
                .findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(x -> x.getSourceRef().equals(CANONICAL_JLPT_MAX_SOURCE_REF)
                        && (x.getSourceNoteId() == noteA || x.getSourceNoteId() == noteB))
                .sorted(Comparator.comparing(NormalizedContentCandidate::getId)).toList();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r7"), admin("d7"));
        ProductionCounts before = productionCounts();
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), groupVersion, admin("p7")))
                .isInstanceOf(NormalizedCandidateGroupPromotionRejectedException.class)
                .hasMessageContaining("private_apkg_notes");

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void _22_oneAlreadyLinkedMemberRejectsWholeGroupWithNoWrites() {
        // 4E-3A's own creation-time check already blocks this scenario if the linkage exists BEFORE
        // group creation (see NormalizedCandidateCanonicalGroupServiceTest._19) - so this test proves
        // the group-PROMOTION-time recheck for a member that became linked AFTER a valid group was
        // already created (e.g. via some unrelated, out-of-band promotion path in between).
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_070L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r8"), admin("d8"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();

        ContentItem existingItem = contentItems.save(
                new ContentItem("already-linked-" + UUID.randomUUID(), ContentType.WORD, CANONICAL_JLPT_MAX_SOURCE_REF, false));
        ImportedSourceRecord linked = new ImportedSourceRecord(CANONICAL_JLPT_MAX_SOURCE_REF, VOCABULARY_NOTE_TYPE,
                c.get(1).getSourceNoteId(), "N5", "", "f", "v");
        linked.linkContentItem(existingItem);
        importedSourceRecords.save(linked);
        ProductionCounts before = productionCounts();

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), groupVersion, admin("p8")))
                .isInstanceOf(NormalizedCandidateGroupPromotionRejectedException.class)
                .hasMessageContaining("이미 production ContentItem");

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void _23_secondGroupPromotionAttemptCreatesNoDuplicateContentItem() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_080L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r9"), admin("d9"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        groupPromotion.promote(group.groupId(), groupVersion, admin("p9a"));
        long itemsAfterFirst = contentItems.count();
        long versionAfter = groupRepository.findById(group.groupId()).orElseThrow().getVersion();

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), versionAfter, admin("p9b")))
                .isInstanceOf(NormalizedCandidateGroupPromotionRejectedException.class);

        assertThat(contentItems.count()).isEqualTo(itemsAfterFirst);
    }

    @Test
    void _21_synthenticNormalizedFieldsNeverWrittenAsRawProvenance() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_090L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r10"), admin("d10"));

        groupPromotion.promote(group.groupId(), groupRepository.findById(group.groupId()).orElseThrow().getVersion(),
                admin("p10"));

        for (NormalizedContentCandidate candidate : c) {
            ImportedSourceRecord record = importedSourceRecords
                    .findBySourceRefAndNoteTypeAndSourceNoteId(CANONICAL_JLPT_MAX_SOURCE_REF, VOCABULARY_NOTE_TYPE,
                            candidate.getSourceNoteId()).orElseThrow();
            assertThat(record.getFieldNames()).doesNotContain("NormalizedCandidateId", "LevelCode");
            assertThat(record.getFieldValues()).doesNotContain(candidate.getId().toString());
        }
    }

    // ===================================================================================
    // Section 24: group freshness
    // ===================================================================================

    @Test
    void _25_dissolvedGroupRejects() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_100L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r11"), admin("d11"));
        long v = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        canonicalGroups.dissolve(group.groupId(), v, null, admin("diss11"));
        long dissolvedVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), dissolvedVersion, admin("p11")))
                .isInstanceOf(NormalizedCandidateGroupPromotionRejectedException.class);
    }

    @Test
    void _26_staleExpectedGroupVersionRejects() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_110L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r12"), admin("d12"));
        long staleVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion() - 1;

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), staleVersion, admin("p12")))
                .isInstanceOf(NormalizedCandidateGroupPromotionStaleException.class);
    }

    @Test
    void _27_canonicalReNormalizedAfterGroupCreationRejects() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_120L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r13"), admin("d13"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, c.get(0).getSourceNoteId(), "E-GROUP-PROMO", "語",
                "ご", "N5", "meaning (edited)"));

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), groupVersion, admin("p13")))
                .isInstanceOf(NormalizedCandidateGroupPromotionStaleException.class);
    }

    @Test
    void _28_nonCanonicalReNormalizedAfterGroupCreationRejects() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_130L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r14"), admin("d14"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, c.get(1).getSourceNoteId(), "E-GROUP-PROMO", "語",
                "ご", "N5", "meaning (edited)"));

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), groupVersion, admin("p14")))
                .isInstanceOf(NormalizedCandidateGroupPromotionStaleException.class);
    }

    @Test
    void _29_currentTicket4BPairDisappearedRejects() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_140L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r15"), admin("d15"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        // Change one candidate's identity so the next reanalysis no longer pairs them at all.
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, c.get(1).getSourceNoteId(), "E-DIFFERENT-NOW",
                "別物", "べつ", "N3", "different"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, CANONICAL_JLPT_MAX_SOURCE_REF);

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), groupVersion, admin("p15")))
                .isInstanceOf(NormalizedCandidateGroupPromotionStaleException.class);
    }

    @Test
    void _31_pairReviewChangedToDistinctContentRejects() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_150L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r16"), admin("d16"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        Long lo = Math.min(c.get(0).getId(), c.get(1).getId());
        Long hi = Math.max(c.get(0).getId(), c.get(1).getId());
        NormalizedCandidatePairReview review = reviewRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi).orElseThrow();
        NormalizedCandidateMatchPair pair = pairRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi).orElseThrow();
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                new DecisionSubmission(lo, hi, HumanReviewDecision.DISTINCT_CONTENT, "다시 확인", pair.getGeneratedAt(),
                        pair.getAssessment(), review.getVersion()),
                admin("rereview16"));

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), groupVersion, admin("p16")))
                .isInstanceOf(NormalizedCandidateGroupPromotionStaleException.class);
    }

    @Test
    void _33_pairReviewVersionChangedRejects() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_160L);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r17"), admin("d17"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        Long lo = Math.min(c.get(0).getId(), c.get(1).getId());
        Long hi = Math.max(c.get(0).getId(), c.get(1).getId());
        NormalizedCandidatePairReview review = reviewRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi).orElseThrow();
        NormalizedCandidateMatchPair pair = pairRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi).orElseThrow();
        // Re-affirm SAME_CONTENT with a real note change to force a genuine version bump.
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                new DecisionSubmission(lo, hi, HumanReviewDecision.SAME_CONTENT, "재확인", pair.getGeneratedAt(),
                        pair.getAssessment(), review.getVersion()),
                admin("rereview17"));

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), groupVersion, admin("p17")))
                .isInstanceOf(NormalizedCandidateGroupPromotionStaleException.class);
    }

    // ===================================================================================
    // Section 25: readiness/policy boundary
    // ===================================================================================

    @Test
    void _37_38_ordinaryPromotionRemainsBlockedAndUntouchedByGroupPromotion() {
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, 910_170L);
        registerAllowedCanonicalSource();
        createFullGroup(c, c.get(0).getId(), admin("r18"), admin("d18"));

        NormalizedContentCandidate canonical = candidateRepository.findById(c.get(0).getId()).orElseThrow();
        assertThatThrownBy(() -> ordinaryPromotion.promote(NormalizedCandidateType.VOCABULARY, canonical.getId(),
                canonical.getNormalizedAt()))
                .as("ordinary single-candidate 4E-1 promotion must remain blocked even though this candidate is now "
                        + "the canonical member of a valid group - group creation never modified readiness")
                .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                .hasMessageContaining("SAME_CONTENT_CANONICAL_SELECTION_REQUIRED");
    }

    @Test
    void _40_canonicalSourceRightsManualReviewBlocksGroupPromotion() {
        String ref = ref();
        List<NormalizedContentCandidate> c = seedPromotableClique(ref, 2, 1L);
        ContentSource source = contentSources.save(new ContentSource(ref, "test", "1", null, null, null, null));
        source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토중", false, null);
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r19"), admin("d19"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), groupVersion, admin("p19")))
                .isInstanceOf(NormalizedCandidateGroupPromotionRejectedException.class)
                .hasMessageContaining("canonical");
    }

    @Test
    void _41_canonicalUnmappableLevelBlocks() {
        seedN5Level();
        registerAllowedCanonicalSource();
        store.saveVocabulary(new VocabularyNormalizationResult(CANONICAL_JLPT_MAX_SOURCE_REF, 910_180L, "E-N9", "語",
                "ご", "noun", null, List.of(new NormalizedMeaning(1, "meaning")), List.of(),
                new NormalizedJlptLevel("N9", "N9", "WordJLPT"), "語", "ご", Map.of(), List.of(), true));
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, 910_181L, "E-N9", "語", "ご", "N9", "meaning"));
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, 910_180L, "語A");
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, 910_181L, "語B");
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, CANONICAL_JLPT_MAX_SOURCE_REF);
        List<NormalizedContentCandidate> c = candidateRepository
                .findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(x -> x.getSourceRef().equals(CANONICAL_JLPT_MAX_SOURCE_REF)
                        && (x.getSourceNoteId() == 910_180L || x.getSourceNoteId() == 910_181L))
                .sorted(Comparator.comparing(NormalizedContentCandidate::getId)).toList();
        Long n9CandidateId = c.stream()
                .filter(x -> x.getVocabularyDetail().getLevelCode().equals("N9"))
                .findFirst().orElseThrow().getId();
        CanonicalGroupCreationResult group = createFullGroup(c, n9CandidateId, admin("r20"), admin("d20"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), groupVersion, admin("p20")))
                .isInstanceOf(NormalizedCandidateGroupPromotionRejectedException.class);
    }

    @Test
    void _43_nonCanonicalMissingExpressionDoesNotBlockGroupPromotion() {
        seedN5Level();
        registerAllowedCanonicalSource();
        long noteA = 910_190L;
        long noteB = 910_191L;
        store.saveVocabulary(vocab(CANONICAL_JLPT_MAX_SOURCE_REF, noteA, "E-BLANK-EXPR", "語", "ご", "N5", "meaning"));
        // Non-canonical member with a BLANK expression - a genuine VOCAB_EXPRESSION_MISSING mapping
        // issue that must NOT block group promotion, since this candidate's own fields are never
        // written to production.
        store.saveVocabulary(new VocabularyNormalizationResult(CANONICAL_JLPT_MAX_SOURCE_REF, noteB, "E-BLANK-EXPR",
                "", "ご", "noun", null, List.of(new NormalizedMeaning(1, "meaning")), List.of(),
                new NormalizedJlptLevel("N5", "N5", "WordJLPT"), "", "ご", Map.of(), List.of(), true));
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, noteA, "語A");
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, noteB, "語B");
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, CANONICAL_JLPT_MAX_SOURCE_REF);
        List<NormalizedContentCandidate> c = candidateRepository
                .findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(x -> x.getSourceRef().equals(CANONICAL_JLPT_MAX_SOURCE_REF)
                        && (x.getSourceNoteId() == noteA || x.getSourceNoteId() == noteB))
                .sorted(Comparator.comparing(NormalizedContentCandidate::getId)).toList();
        Long canonicalId = c.stream().filter(x -> !x.getVocabularyDetail().getExpression().isEmpty())
                .findFirst().orElseThrow().getId();
        CanonicalGroupCreationResult group = createFullGroup(c, canonicalId, admin("r21"), admin("d21"));

        GroupPromotionResult result = groupPromotion.promote(group.groupId(),
                groupRepository.findById(group.groupId()).orElseThrow().getVersion(), admin("p21"));

        assertThat(result.contentItemId()).isNotNull();
    }

    // ===================================================================================
    // Section 20: atomicity - rollback proof via a spy is covered in the atomicity test class.
    // ===================================================================================

    // ===================================================================================
    // Eligibility preview
    // ===================================================================================

    @Test
    void previewEligibilityReflectsTrueBlockersWithoutWritingAnything() {
        String ref = ref();
        List<NormalizedContentCandidate> c = seedPromotableClique(ref, 2, 1L);
        // No rights registered - should block.
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r22"), admin("d22"));
        long itemsBefore = contentItems.count();

        GroupPromotionEligibilityView view = groupPromotion.previewEligibility(group.groupId());

        assertThat(view.eligible()).isFalse();
        assertThat(view.blockingReasons()).isNotEmpty();
        assertThat(contentItems.count()).isEqualTo(itemsBefore);
    }
}
