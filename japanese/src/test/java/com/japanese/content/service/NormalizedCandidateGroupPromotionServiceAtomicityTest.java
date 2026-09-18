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
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.DecisionSubmission;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.importer.NormalizedExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

/**
 * JLPT-MAX Ticket 4E-3B, ticket section 20: proves a failure that happens <em>after</em> the
 * production {@code ContentItem}/{@code Word}/{@code Meaning}/{@code Example} rows have already been
 * flushed (group promotion's own "Step 13" - see {@link NormalizedCandidateGroupPromotionService}'s
 * class javadoc) - but before every member's provenance link ("Step 14") finishes - rolls back
 * everything for the WHOLE group, leaving no partial production row, no partial provenance link, and
 * no group-version bump behind.
 *
 * <p>Mirrors {@link NormalizedVocabularyCandidatePromotionServiceAtomicityTest}'s own approach exactly:
 * uses a {@link MockitoSpyBean} (test-only, this file only) to wrap the real
 * {@link ImportedSourceRecordRepository} bean and make its {@code save} throw unconditionally,
 * simulating the kind of failure a real constraint violation or DB outage would cause on the very FIRST
 * iteration of Step 14's per-member loop - i.e. strictly after Step 13's {@code ContentItem}/
 * {@code Word}/{@code Meaning}/{@code Example} rows are already flushed (their {@code @Id} columns use
 * {@code GenerationType.IDENTITY}, which forces Hibernate to execute those {@code INSERT} statements
 * synchronously at {@code persist()} time, not deferred to a later flush), but strictly before ANY
 * member's provenance link is written. A three-member clique is used (not two) so the assertions below
 * also prove no member's row was left half-linked, not just that a single pair's worth of rows rolled
 * back.
 *
 * <p>Deliberately has no class-level {@code @Transactional}, matching the single-candidate atomicity
 * test: {@code promote(...)}'s own {@code @Transactional} must be a real, independently-committed-or
 * -rolled-back transaction here, not a participant in a surrounding test transaction that would roll
 * back regardless of whether {@code promote(...)} itself is atomic.
 *
 * <p>{@code @DirtiesContext} (class-level, after all tests): this class permanently commits VOCABULARY
 * candidates against the shared canonical {@code JLPT-MAX-Deck-2.1.1.apkg} sourceRef (required - see
 * "Mirrors" above, the meaning-language policy only resolves for that exact ref). Several pre-existing,
 * unrelated test classes (e.g. {@code NormalizedCandidatePromotionReadinessServiceTest}) assert an
 * EXACT candidate count for that same shared sourceRef and would otherwise see this class's leftover
 * rows if Spring happened to reuse the same cached {@code ApplicationContext} (and therefore the same
 * H2 instance) for a later test class - see
 * {@code NormalizedCandidateCanonicalGroupConcurrencyTest}'s own javadoc for this same, already
 * -documented class of fragility. Marking this context dirty forces Spring to discard and rebuild it
 * afterward, guaranteeing every later test class gets a fresh, empty database regardless of run order.
 */
@SpringBootTest
@ActiveProfiles("sample")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NormalizedCandidateGroupPromotionServiceAtomicityTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired NormalizedCandidatePairReviewService reviewService;
    @Autowired NormalizedCandidateCanonicalGroupService canonicalGroups;
    @Autowired NormalizedCandidateGroupPromotionService groupPromotion;
    @Autowired NormalizedContentCandidateRepository candidateRepository;
    @Autowired NormalizedCandidateMatchPairRepository pairRepository;
    @Autowired NormalizedCandidatePairReviewRepository reviewRepository;
    @Autowired NormalizedCandidateCanonicalGroupRepository groupRepository;
    @Autowired UserAccountRepository accounts;
    @Autowired ContentSourceRepository contentSources;
    @Autowired LevelRepository levels;
    @MockitoSpyBean ImportedSourceRecordRepository importedSourceRecords;
    @Autowired JdbcClient jdbcClient;
    @Autowired ObjectMapper objectMapper;

    private static final String CANONICAL_JLPT_MAX_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";
    private static final String VOCABULARY_NOTE_TYPE = "JLPT MAX덱 어휘";

    private UserAccount admin(String suffix) {
        return accounts.save(new UserAccount("group-promo-atomic-admin-" + suffix + "-" + UUID.randomUUID(), null,
                "hash", "Admin " + suffix, UserRole.ADMIN));
    }

    private void seedN5Level() {
        levels.findBySystemAndCode("JLPT", "N5").orElseGet(() -> levels.save(new Level("JLPT", "N5", "JLPT N5")));
    }

    private void registerAllowedCanonicalSource() {
        ContentSource source = contentSources.findBySourceRef(CANONICAL_JLPT_MAX_SOURCE_REF).orElseThrow();
        if (source.getRightsStatus() == ContentSourceRightsStatus.UNKNOWN) {
            source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 시작", false, null);
        }
        if (source.getRightsStatus() == ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED) {
            source.reviewRights(ContentSourceRightsStatus.ALLOWED, "허용", false, null);
        }
        contentSources.save(source);
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
                List.of(new NormalizedExample(1, meaning, expression + "の例文です", reading + "のよみ",
                        meaning + " translation")),
                new NormalizedJlptLevel(level, level, "WordJLPT"), expression, reading, Map.of(), List.of(), true);
    }

    private List<NormalizedContentCandidate> seedPromotableClique(String ref, int n, long baseNoteId) {
        seedN5Level();
        for (int i = 0; i < n; i++) {
            long noteId = baseNoteId + i;
            store.saveVocabulary(vocab(ref, noteId, "E-GROUP-PROMO-ATOMIC", "語", "ご", "N5", "meaning"));
            seedPrivateApkgNote(ref, noteId, "語" + i);
        }
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        return candidateRepository.findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, ref).stream()
                .filter(c -> c.getSourceNoteId() >= baseNoteId && c.getSourceNoteId() < baseNoteId + n)
                .sorted(Comparator.comparing(NormalizedContentCandidate::getId))
                .toList();
    }

    private void reviewSameContent(NormalizedContentCandidate a, NormalizedContentCandidate b, UserAccount reviewer) {
        Long lo = Math.min(a.getId(), b.getId());
        Long hi = Math.max(a.getId(), b.getId());
        NormalizedCandidateMatchPair pair = pairRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi).orElseThrow();
        Long expectedVersion = reviewRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi)
                .map(NormalizedCandidatePairReview::getVersion).orElse(null);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                new DecisionSubmission(lo, hi, HumanReviewDecision.SAME_CONTENT, null, pair.getGeneratedAt(),
                        pair.getAssessment(), expectedVersion),
                reviewer);
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

    @Test
    void aFailedProvenanceLinkSaveDuringGroupPromotionRollsBackTheWholeGroupIncludingAlreadyFlushedContentItem() {
        long baseNoteId = 920_000L + (System.nanoTime() % 100_000L);
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 3, baseNoteId);
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("r1"), admin("d1"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();

        // Snapshot counts only now (test setup above is done) - this single test method runs on one
        // thread with no concurrent writer, so a before/after delta across the one promote() call below
        // faithfully isolates exactly what that call did or did not commit.
        long contentItemsBefore = jdbcClient.sql("select count(*) from content_items").query(Long.class).single();
        long wordsBefore = jdbcClient.sql("select count(*) from words").query(Long.class).single();
        long meaningsBefore = jdbcClient.sql("select count(*) from meanings").query(Long.class).single();
        long examplesBefore = jdbcClient.sql("select count(*) from examples").query(Long.class).single();
        long recordsBefore = jdbcClient.sql("select count(*) from imported_source_records").query(Long.class).single();

        Mockito.doThrow(new RuntimeException("simulated persistence failure while linking group member provenance"))
                .when(importedSourceRecords).save(Mockito.any());

        assertThatThrownBy(() -> groupPromotion.promote(group.groupId(), groupVersion, admin("p1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated persistence failure while linking group member provenance");

        // Read back with fresh queries (the failed call's transaction is gone): if the ContentItem/
        // Word/Meaning/Example inserts, or even the FIRST member's provenance link, had actually
        // survived while a later member's ImportedSourceRecord save failed, these counts would be
        // higher than the snapshot above instead of exactly matching it - and the group's version must
        // also be untouched, proving the group row itself was rolled back too.
        assertThat(jdbcClient.sql("select count(*) from content_items").query(Long.class).single())
                .isEqualTo(contentItemsBefore);
        assertThat(jdbcClient.sql("select count(*) from words").query(Long.class).single()).isEqualTo(wordsBefore);
        assertThat(jdbcClient.sql("select count(*) from meanings").query(Long.class).single())
                .isEqualTo(meaningsBefore);
        assertThat(jdbcClient.sql("select count(*) from examples").query(Long.class).single())
                .isEqualTo(examplesBefore);
        assertThat(jdbcClient.sql("select count(*) from imported_source_records").query(Long.class).single())
                .isEqualTo(recordsBefore);
        assertThat(groupRepository.findById(group.groupId()).orElseThrow().getVersion()).isEqualTo(groupVersion);
    }
}
