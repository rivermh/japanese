package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupCreationRequest;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupCreationResult;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.EdgeExpectation;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.ParticipantExpectation;
import com.japanese.content.dto.NormalizedCandidateGroupPromotionModels.GroupPromotionResult;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.DecisionSubmission;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroup;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupStatus;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.importer.NormalizedExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.repository.ContentSourceRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/**
 * JLPT-MAX Ticket 4E-3B, ticket section 26: real-thread concurrency coverage for
 * {@link NormalizedCandidateGroupPromotionService#promote} racing against every other write path that
 * can touch the same rows.
 *
 * <p><b>H2-vs-MySQL fidelity limitation (stated explicitly, per the ticket's own instruction not to
 * overclaim what this proves)</b>: this suite runs against H2 (MVStore engine, {@code MODE=MySQL}), not
 * the real MySQL production database. H2's MVStore engine does implement real row-level
 * {@code SELECT ... FOR UPDATE} blocking for a {@code PESSIMISTIC_WRITE} lock - which is what every
 * scenario below actually exercises and can therefore genuinely prove - but its lock-wait/deadlock
 * -detection timing, isolation-level nuances, and exact exception types on contention are not
 * guaranteed identical to MySQL/InnoDB's. What these tests prove: given this environment's H2 engine,
 * every listed race completes without deadlock and never leaves more than one production
 * {@code ContentItem} behind for the same group. What they do not prove: that MySQL's own InnoDB
 * locking behaves identically under the exact same races (a MySQL-specific concern out of reach of any
 * H2-backed test in this repository).
 *
 * <p>Scenarios B (vs. Ticket 4B reanalysis) and D (vs. an ordinary Ticket 4E-1 promotion attempt) have
 * a single deterministically-correct outcome regardless of thread interleaving, because the shared
 * candidate-row locks fully serialize the two competing transactions end-to-end (see each method's own
 * javadoc for the specific reasoning) - those tests assert that single outcome directly. Scenarios A
 * (vs. dissolution) and C (vs. Ticket 4C re-review) do NOT have group-promotion-owned locks covering the
 * other side's write until late in {@code promote(...)}'s own transaction, so which side commits first
 * is genuinely racy; those tests assert only the safety invariants that must hold under either
 * interleaving (never more than one production write, no partial/corrupted state). Scenario E (two
 * concurrent promotion attempts on the very same group) asserts the same "exactly one winner" invariant
 * the ordinary single-candidate concurrency test already establishes for Ticket 4E-1.
 *
 * <p>{@code @DirtiesContext} (class-level, after all tests): every scenario here permanently commits
 * VOCABULARY candidates against the shared canonical {@code JLPT-MAX-Deck-2.1.1.apkg} sourceRef
 * (required - {@code NormalizedVocabularyMeaningLanguagePolicy} only resolves a {@code Meaning}
 * language tag for that exact ref, and every scenario needs {@code promote(...)} to actually reach
 * production-write to meaningfully race). Several pre-existing, unrelated test classes (e.g.
 * {@code NormalizedCandidatePromotionReadinessServiceTest}) assert an EXACT candidate count for that
 * same shared sourceRef and would otherwise see this class's leftover rows if Spring happened to reuse
 * the same cached {@code ApplicationContext} (and therefore the same H2 instance) for a later test
 * class - see {@code NormalizedCandidateCanonicalGroupConcurrencyTest}'s own javadoc for this same,
 * already-documented class of fragility. Marking this context dirty forces Spring to discard and
 * rebuild it afterward, guaranteeing every later test class gets a fresh, empty database regardless of
 * run order.
 */
@SpringBootTest
@ActiveProfiles("sample")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NormalizedCandidateGroupPromotionConcurrencyTest {

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
    @Autowired LevelRepository levels;
    @Autowired JdbcClient jdbcClient;
    @Autowired ObjectMapper objectMapper;

    private static final String CANONICAL_JLPT_MAX_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";
    private static final String VOCABULARY_NOTE_TYPE = "JLPT MAX덱 어휘";

    private UserAccount admin(String suffix) {
        return accounts.save(new UserAccount("group-promo-cc-admin-" + suffix + "-" + UUID.randomUUID(), null,
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

    /**
     * Deliberately takes its OWN unique {@code expression}/{@code reading} per call (never the shared
     * "語"/"ご" text the main policy test class uses) - unlike that class, this class has no
     * class-level {@code @Transactional} (real races need real, independently-committed transactions,
     * see class javadoc), so every scenario's rows genuinely accumulate in the same table across the
     * whole test run instead of being rolled back between methods. If two scenarios' candidates shared
     * identical expression/reading text, {@code analyzer.analyze} could legitimately treat them as a
     * newly-discovered candidate match across scenarios and create a fresh, unrelated
     * {@code PAIR_UNREVIEWED} obligation for one of them - a self-inflicted test-data collision, not a
     * real bug in the service under test. Mirrors
     * {@code NormalizedVocabularyCandidatePromotionServiceAtomicityTest}'s own choice of a distinct
     * expression ("並"/"なみ") for exactly this reason.
     */
    private List<NormalizedContentCandidate> seedPromotableClique(String ref, int n, long baseNoteId, String entryId,
            String expression, String reading) {
        seedN5Level();
        for (int i = 0; i < n; i++) {
            long noteId = baseNoteId + i;
            store.saveVocabulary(vocab(ref, noteId, entryId, expression, reading, "N5", "meaning-" + entryId));
            seedPrivateApkgNote(ref, noteId, expression + i);
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

    private long contentItemCount() {
        return jdbcClient.sql("select count(*) from content_items").query(Long.class).single();
    }

    // ===================================================================================
    // Scenario A: group promotion vs. concurrent group dissolution.
    // ===================================================================================

    /**
     * Genuinely racy (see class javadoc): {@code dissolve(...)} only acquires the group row's exclusive
     * lock lazily, at its own {@code save(group)} flush point, not up front - so either side can reach
     * the row lock first. Both outcomes are safe and are asserted here: if dissolve's status flip
     * commits first, {@code promote(...)} must see {@code status != ACTIVE} and write nothing; if
     * promote's lock wins first, it commits its ONE {@code ContentItem} and dissolve (unblocked
     * afterwards) is free to mark the now-historical group dissolved without touching that already
     * -committed production content.
     */
    @Test
    void groupPromotionRacingDissolutionNeverProducesMoreThanOneContentItemOrCorruptedGroupState() throws Exception {
        long baseNoteId = 930_000L + (System.nanoTime() % 100_000L);
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, baseNoteId,
                "E-CONC-A", "並A", "なみA");
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("ra"), admin("da"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        long itemsBefore = contentItemCount();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Outcome> promoteAttempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    GroupPromotionResult r = groupPromotion.promote(group.groupId(), groupVersion, admin("pa"));
                    return Outcome.success(r.contentItemId());
                } catch (RuntimeException e) {
                    return Outcome.failure(e);
                }
            };
            Callable<Outcome> dissolveAttempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    canonicalGroups.dissolve(group.groupId(), groupVersion, "concurrency test", admin("diss-a"));
                    return Outcome.success(null);
                } catch (RuntimeException e) {
                    return Outcome.failure(e);
                }
            };

            Future<Outcome> promoteFuture = pool.submit(promoteAttempt);
            Future<Outcome> dissolveFuture = pool.submit(dissolveAttempt);
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();

            Outcome promoteOutcome = promoteFuture.get(30, TimeUnit.SECONDS);
            Outcome dissolveOutcome = dissolveFuture.get(30, TimeUnit.SECONDS);

            // dissolve() never fails here regardless of interleaving: promote() never bumps the group's
            // own @Version (see class javadoc "Transaction/lock order"), so dissolve's own
            // expectedVersion always still matches whichever of the two commits first or second -
            // asserting this (not just calling .get() to drain the thread) is what proves the race
            // never corrupts dissolve's own outcome either.
            assertThat(dissolveOutcome.succeeded())
                    .as("dissolve must always succeed here regardless of which side wins the race - outcome: %s",
                            dissolveOutcome)
                    .isTrue();

            long itemsAfter = contentItemCount();
            assertThat(itemsAfter - itemsBefore)
                    .as("at most one ContentItem must ever exist for this group, whichever side won the race")
                    .isBetween(0L, 1L);
            if (promoteOutcome.succeeded()) {
                assertThat(itemsAfter - itemsBefore).isEqualTo(1L);
            } else {
                assertThat(itemsAfter - itemsBefore)
                        .as("if group promotion lost the race it must not have written a ContentItem")
                        .isEqualTo(0L);
                assertThat(promoteOutcome.failure())
                        .as("a lost race must fail because the group is no longer ACTIVE, not for any other reason")
                        .isInstanceOf(NormalizedCandidateGroupPromotionRejectedException.class)
                        .hasMessageContaining("ACTIVE");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ===================================================================================
    // Scenario B: group promotion vs. concurrent Ticket 4B reanalysis of the same sourceRef.
    // ===================================================================================

    /**
     * Deterministic outcome: both {@code promote(...)}'s Step 3 and
     * {@code NormalizedCandidateConflictAnalyzer.analyze}'s own Ticket 4E-3A hardening lock the SAME
     * member-candidate rows before either mutates anything pair-related, so the two fully serialize -
     * whichever acquires the candidate locks first runs its entire operation to completion before the
     * other can even start. Reanalyzing unchanged data is idempotent (same left/right/assessment,
     * merely a later {@code generatedAt}), so group promotion succeeds regardless of which side goes
     * first.
     */
    @Test
    void groupPromotionRacingReanalysisOfTheSameSourceRefStillSucceedsExactlyOnce() throws Exception {
        long baseNoteId = 930_100L + (System.nanoTime() % 100_000L);
        // Must be the canonical sourceRef (not a synthetic per-test ref): NormalizedVocabularyMeaningLanguagePolicy
        // only resolves a Meaning.languageTag for this exact sourceRef, and this scenario needs the group to
        // reach the write phase (past readiness) so the reanalysis race actually has something to interleave with.
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, baseNoteId,
                "E-CONC-B", "並B", "なみB");
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("rb"), admin("db"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        long itemsBefore = contentItemCount();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Outcome> promoteAttempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    GroupPromotionResult r = groupPromotion.promote(group.groupId(), groupVersion, admin("pb"));
                    return Outcome.success(r.contentItemId());
                } catch (RuntimeException e) {
                    return Outcome.failure(e);
                }
            };
            Callable<Outcome> reanalyzeAttempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    analyzer.analyze(NormalizedCandidateType.VOCABULARY, CANONICAL_JLPT_MAX_SOURCE_REF);
                    return Outcome.success(null);
                } catch (RuntimeException e) {
                    return Outcome.failure(e);
                }
            };

            Future<Outcome> promoteFuture = pool.submit(promoteAttempt);
            Future<Outcome> reanalyzeFuture = pool.submit(reanalyzeAttempt);
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();

            Outcome promoteOutcome = promoteFuture.get(30, TimeUnit.SECONDS);
            Outcome reanalyzeOutcome = reanalyzeFuture.get(30, TimeUnit.SECONDS);

            assertThat(reanalyzeOutcome.succeeded()).as("reanalysis of unrelated-to-promotion data must never fail")
                    .isTrue();
            assertThat(promoteOutcome.succeeded())
                    .as("group promotion must succeed regardless of reanalysis interleaving - outcome: %s",
                            promoteOutcome)
                    .isTrue();
            assertThat(contentItemCount() - itemsBefore).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    // ===================================================================================
    // Scenario C: group promotion vs. a concurrent Ticket 4C re-review changing the decision.
    // ===================================================================================

    /**
     * Genuinely racy (see class javadoc): {@code submitDecision(...)} only acquires the review row's
     * exclusive lock lazily, at its own flush point, not up front. Either side can reach the row lock
     * first. Both outcomes are safe and are asserted here: if the re-review's DISTINCT_CONTENT flip
     * commits first, {@code promote(...)}'s Step 8 freshness check must see it and reject with a stale
     * exception, writing nothing; if promote's lock wins first, it commits and the re-review (unblocked
     * afterwards) is free to record its own decision change without corrupting the already-committed
     * production content.
     */
    @Test
    void groupPromotionRacingAConcurrentReReviewNeverProducesMoreThanOneContentItemOrCorruptedGroupState()
            throws Exception {
        long baseNoteId = 930_200L + (System.nanoTime() % 100_000L);
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, baseNoteId,
                "E-CONC-C", "並C", "なみC");
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("rc"), admin("dc"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        Long lo = Math.min(c.get(0).getId(), c.get(1).getId());
        Long hi = Math.max(c.get(0).getId(), c.get(1).getId());
        NormalizedCandidatePairReview review = reviewRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi)
                .orElseThrow();
        NormalizedCandidateMatchPair pair = pairRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi)
                .orElseThrow();
        long itemsBefore = contentItemCount();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Outcome> promoteAttempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    GroupPromotionResult r = groupPromotion.promote(group.groupId(), groupVersion, admin("pc"));
                    return Outcome.success(r.contentItemId());
                } catch (RuntimeException e) {
                    return Outcome.failure(e);
                }
            };
            Callable<Outcome> reReviewAttempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                            new DecisionSubmission(lo, hi, HumanReviewDecision.DISTINCT_CONTENT, "재검토",
                                    pair.getGeneratedAt(), pair.getAssessment(), review.getVersion()),
                            admin("rereview-c"));
                    return Outcome.success(null);
                } catch (RuntimeException e) {
                    return Outcome.failure(e);
                }
            };

            Future<Outcome> promoteFuture = pool.submit(promoteAttempt);
            Future<Outcome> reReviewFuture = pool.submit(reReviewAttempt);
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();

            Outcome promoteOutcome = promoteFuture.get(30, TimeUnit.SECONDS);
            Outcome reReviewOutcome = reReviewFuture.get(30, TimeUnit.SECONDS);

            assertThat(reReviewOutcome.succeeded()).as("the re-review itself must always be able to record its decision")
                    .isTrue();
            long itemsAfter = contentItemCount();
            assertThat(itemsAfter - itemsBefore).isBetween(0L, 1L);
            if (promoteOutcome.succeeded()) {
                assertThat(itemsAfter - itemsBefore).isEqualTo(1L);
            } else {
                assertThat(itemsAfter - itemsBefore).isEqualTo(0L);
                assertThat(promoteOutcome.failure())
                        .as("a lost race must fail via the freshness/stale path, not any other rejection")
                        .isInstanceOf(NormalizedCandidateGroupPromotionStaleException.class);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ===================================================================================
    // Scenario D: group promotion vs. a concurrent ordinary Ticket 4E-1 single-candidate promotion
    // attempt on the SAME (canonical) candidate.
    // ===================================================================================

    /**
     * Deterministic outcome: both services lock the SAME canonical-candidate row first
     * ({@code findByIdAndCandidateTypeForGroupPromotion} / {@code findByIdAndCandidateTypeForPromotion}
     * - distinct dedicated lock methods, same underlying row), so the two fully serialize. The ordinary
     * path's own readiness recomputation always finds
     * {@code SAME_CONTENT_CANONICAL_SELECTION_REQUIRED} for this candidate (an unresolved SAME_CONTENT
     * pair, by design never resolved by group creation/promotion - see
     * {@code NormalizedCandidateGroupPromotionServiceTest._37_38}), so it is rejected before any write
     * regardless of which side wins the row lock; group promotion is therefore never blocked by it and
     * always succeeds.
     */
    @Test
    void groupPromotionIsNeverBlockedByAConcurrentOrdinarySingleCandidatePromotionAttempt() throws Exception {
        long baseNoteId = 930_300L + (System.nanoTime() % 100_000L);
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, baseNoteId,
                "E-CONC-D", "並D", "なみD");
        registerAllowedCanonicalSource();
        Long canonicalId = c.get(0).getId();
        CanonicalGroupCreationResult group = createFullGroup(c, canonicalId, admin("rd"), admin("dd"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        Instant canonicalNormalizedAt = candidateRepository.findById(canonicalId).orElseThrow().getNormalizedAt();
        long itemsBefore = contentItemCount();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Outcome> promoteAttempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    GroupPromotionResult r = groupPromotion.promote(group.groupId(), groupVersion, admin("pd"));
                    return Outcome.success(r.contentItemId());
                } catch (RuntimeException e) {
                    return Outcome.failure(e);
                }
            };
            Callable<Outcome> ordinaryAttempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    ordinaryPromotion.promote(NormalizedCandidateType.VOCABULARY, canonicalId, canonicalNormalizedAt);
                    return Outcome.success(null);
                } catch (RuntimeException e) {
                    return Outcome.failure(e);
                }
            };

            Future<Outcome> promoteFuture = pool.submit(promoteAttempt);
            Future<Outcome> ordinaryFuture = pool.submit(ordinaryAttempt);
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();

            Outcome promoteOutcome = promoteFuture.get(30, TimeUnit.SECONDS);
            Outcome ordinaryOutcome = ordinaryFuture.get(30, TimeUnit.SECONDS);

            assertThat(promoteOutcome.succeeded())
                    .as("group promotion must never be blocked by the ordinary path's own always-rejected attempt - "
                            + "outcome: %s", promoteOutcome)
                    .isTrue();
            assertThat(ordinaryOutcome.succeeded())
                    .as("the ordinary single-candidate path must remain blocked even under this race")
                    .isFalse();
            assertThat(ordinaryOutcome.failure())
                    .isInstanceOf(NormalizedCandidatePromotionRejectedException.class)
                    .hasMessageContaining("SAME_CONTENT_CANONICAL_SELECTION_REQUIRED");
            assertThat(contentItemCount() - itemsBefore).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    // ===================================================================================
    // Scenario E: two concurrent group-promotion attempts on the SAME group.
    // ===================================================================================

    /**
     * Mirrors {@code NormalizedVocabularyCandidatePromotionServiceConcurrencyTest}'s own single
     * -candidate scenario exactly: the group row's {@code PESSIMISTIC_WRITE} lock (acquired first, per
     * class javadoc "Transaction/lock order") fully serializes the two attempts. Whichever wins commits
     * the group's ONE {@code ContentItem} and links every member's provenance; the loser, unblocked
     * afterwards, re-reads a group whose status is still ACTIVE (promotion never bumps the group's own
     * version) but whose member candidates are now already provenance-linked, so it is rejected by the
     * Step 9 "already linked" check - exactly one success.
     */
    @Test
    void twoConcurrentGroupPromotionAttemptsOnTheSameGroupNeverBothSucceed() throws Exception {
        long baseNoteId = 930_400L + (System.nanoTime() % 100_000L);
        List<NormalizedContentCandidate> c = seedPromotableClique(CANONICAL_JLPT_MAX_SOURCE_REF, 2, baseNoteId,
                "E-CONC-E", "並E", "なみE");
        registerAllowedCanonicalSource();
        CanonicalGroupCreationResult group = createFullGroup(c, c.get(0).getId(), admin("re"), admin("de"));
        long groupVersion = groupRepository.findById(group.groupId()).orElseThrow().getVersion();
        long itemsBefore = contentItemCount();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Outcome> attempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    GroupPromotionResult r = groupPromotion.promote(group.groupId(), groupVersion, admin("pe"));
                    return Outcome.success(r.contentItemId());
                } catch (RuntimeException e) {
                    return Outcome.failure(e);
                }
            };

            Future<Outcome> first = pool.submit(attempt);
            Future<Outcome> second = pool.submit(attempt);
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();

            Outcome a = first.get(30, TimeUnit.SECONDS);
            Outcome b = second.get(30, TimeUnit.SECONDS);

            long successCount = List.of(a, b).stream().filter(Outcome::succeeded).count();
            assertThat(successCount)
                    .as("exactly one of the two concurrent group-promotion attempts must succeed - outcomes: %s, %s",
                            a, b)
                    .isEqualTo(1);
            assertThat(contentItemCount() - itemsBefore).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(Long contentItemId, RuntimeException failure) {
        static Outcome success(Long contentItemId) {
            return new Outcome(contentItemId, null);
        }

        static Outcome failure(RuntimeException failure) {
            return new Outcome(null, failure);
        }

        boolean succeeded() {
            return failure == null;
        }

        @Override
        public String toString() {
            return succeeded() ? "SUCCESS(contentItemId=" + contentItemId + ")" : "FAILURE(" + failure + ")";
        }
    }
}
