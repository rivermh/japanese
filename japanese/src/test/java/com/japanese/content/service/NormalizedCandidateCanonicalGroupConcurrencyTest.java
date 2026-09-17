package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupMember;
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
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupEdge;
import com.japanese.content.repository.NormalizedCandidateCanonicalGroupEdgeRepository;
import com.japanese.content.repository.NormalizedCandidateCanonicalGroupMemberRepository;
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
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/**
 * JLPT-MAX Ticket 4E-3A, test plan section 18: real, independent-thread concurrency coverage for
 * {@link NormalizedCandidateCanonicalGroupService}, mirroring
 * {@code NormalizedVocabularyCandidatePromotionServiceConcurrencyTest}'s own convention exactly - NOT
 * {@code @Transactional} (each thread gets its own real transaction), permanently committed fixtures
 * (unique refs per test), latch-synchronized simultaneous starts.
 *
 * <p><b>H2-vs-MySQL fidelity limitation (stated explicitly, matching every other concurrency test in
 * this codebase)</b>: this suite runs against H2 (MVStore engine, {@code MODE=MySQL}), not the real
 * MySQL production database. It proves this environment's H2 engine never lets two racing transactions
 * corrupt this feature's invariants; it does not independently prove MySQL/InnoDB behaves identically
 * under the exact same race.
 */
@SpringBootTest
@ActiveProfiles("sample")
class NormalizedCandidateCanonicalGroupConcurrencyTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired NormalizedCandidatePairReviewService reviewService;
    @Autowired NormalizedCandidateCanonicalGroupService canonicalGroups;
    @Autowired NormalizedVocabularyCandidatePromotionService promotion;
    @Autowired NormalizedContentCandidateRepository candidateRepository;
    @Autowired NormalizedCandidateMatchPairRepository pairRepository;
    @Autowired NormalizedCandidatePairReviewRepository reviewRepository;
    @Autowired NormalizedCandidateCanonicalGroupRepository groupRepository;
    @Autowired NormalizedCandidateCanonicalGroupMemberRepository memberRepository;
    @Autowired NormalizedCandidateCanonicalGroupEdgeRepository edgeRepository;
    @Autowired UserAccountRepository accounts;
    @Autowired ContentSourceRepository contentSources;
    @Autowired LevelRepository levels;
    @Autowired JdbcClient jdbcClient;
    @Autowired ObjectMapper objectMapper;

    private static final String CANONICAL_JLPT_MAX_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";

    private String ref() {
        return "canonical-group-concurrency-" + UUID.randomUUID();
    }

    private UserAccount admin(String suffix) {
        return accounts.save(new UserAccount("canonical-concurrency-admin-" + suffix + "-" + UUID.randomUUID(), null,
                "hash", "Admin " + suffix, UserRole.ADMIN));
    }

    private VocabularyNormalizationResult vocab(String ref, long noteId) {
        return new VocabularyNormalizationResult(ref, noteId, "E-SHARED", "語", "ご", "noun", null,
                List.of(new NormalizedMeaning(1, "meaning")), List.of(),
                new NormalizedJlptLevel("N5", "N5", "WordJLPT"), "語", "ご", Map.of(), List.of(), true);
    }

    private List<NormalizedContentCandidate> seedClique(String ref, int n) {
        for (int i = 1; i <= n; i++) {
            store.saveVocabulary(vocab(ref, i));
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

    private CanonicalGroupCreationRequest twoWayRequest(NormalizedContentCandidate a, NormalizedContentCandidate b,
            Long canonicalId) {
        Long lo = Math.min(a.getId(), b.getId());
        Long hi = Math.max(a.getId(), b.getId());
        long version = reviewRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi).orElseThrow().getVersion();
        return new CanonicalGroupCreationRequest(canonicalId,
                List.of(new ParticipantExpectation(a.getId(), a.getNormalizedAt()),
                        new ParticipantExpectation(b.getId(), b.getNormalizedAt())),
                List.of(new EdgeExpectation(a.getId(), b.getId(), version)), null);
    }

    private record Outcome(CanonicalGroupCreationResult result, RuntimeException failure) {
        static Outcome success(CanonicalGroupCreationResult result) {
            return new Outcome(result, null);
        }

        static Outcome failure(RuntimeException failure) {
            return new Outcome(null, failure);
        }

        boolean succeeded() {
            return result != null;
        }

        @Override
        public String toString() {
            return succeeded() ? "SUCCESS(" + result + ")" : "FAILURE(" + failure + ")";
        }
    }

    private Outcome runOne(Callable<CanonicalGroupCreationResult> call) {
        try {
            return Outcome.success(call.call());
        } catch (RuntimeException e) {
            return Outcome.failure(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===================================================================================
    // A. Two group creations sharing candidate A, different partners
    // ===================================================================================

    @Test
    void twoGroupCreationsSharingOneCandidateNeverBothSucceed() throws Exception {
        List<NormalizedContentCandidate> abc = seedClique(ref(), 3);
        NormalizedContentCandidate a = abc.get(0);
        NormalizedContentCandidate b = abc.get(1);
        NormalizedContentCandidate c = abc.get(2);
        reviewSameContent(a, b, admin("ab"));
        reviewSameContent(a, c, admin("ac"));
        CanonicalGroupCreationRequest requestAB = twoWayRequest(a, b, a.getId());
        CanonicalGroupCreationRequest requestAC = twoWayRequest(a, c, a.getId());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Outcome> attemptAB = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                return runOne(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, requestAB, admin("dab")));
            };
            Callable<Outcome> attemptAC = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                return runOne(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, requestAC, admin("dac")));
            };
            Future<Outcome> f1 = pool.submit(attemptAB);
            Future<Outcome> f2 = pool.submit(attemptAC);
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();
            Outcome o1 = f1.get(30, TimeUnit.SECONDS);
            Outcome o2 = f2.get(30, TimeUnit.SECONDS);

            long successCount = List.of(o1, o2).stream().filter(Outcome::succeeded).count();
            assertThat(successCount).as("exactly one of the two overlapping group creations must succeed: %s, %s", o1, o2)
                    .isEqualTo(1);
            List<NormalizedCandidateCanonicalGroupMember> aMemberships =
                    memberRepository.findByMemberCandidate_IdIn(List.of(a.getId()));
            assertThat(aMemberships).as("candidate A must end up in exactly one current membership row").hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ===================================================================================
    // B. Same candidate set, different canonical selections
    // ===================================================================================

    @Test
    void sameCandidateSetWithDifferentCanonicalSelectionsNeverBothSucceed() throws Exception {
        List<NormalizedContentCandidate> ab = seedClique(ref(), 2);
        NormalizedContentCandidate a = ab.get(0);
        NormalizedContentCandidate b = ab.get(1);
        reviewSameContent(a, b, admin("ab2"));
        CanonicalGroupCreationRequest requestCanonicalA = twoWayRequest(a, b, a.getId());
        CanonicalGroupCreationRequest requestCanonicalB = twoWayRequest(a, b, b.getId());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Outcome> attemptA = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                return runOne(() ->
                        canonicalGroups.create(NormalizedCandidateType.VOCABULARY, requestCanonicalA, admin("dca")));
            };
            Callable<Outcome> attemptB = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                return runOne(() ->
                        canonicalGroups.create(NormalizedCandidateType.VOCABULARY, requestCanonicalB, admin("dcb")));
            };
            Future<Outcome> f1 = pool.submit(attemptA);
            Future<Outcome> f2 = pool.submit(attemptB);
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();
            Outcome o1 = f1.get(30, TimeUnit.SECONDS);
            Outcome o2 = f2.get(30, TimeUnit.SECONDS);

            long successCount = List.of(o1, o2).stream().filter(Outcome::succeeded).count();
            assertThat(successCount)
                    .as("exactly one canonical choice for the same pair must win: %s, %s", o1, o2)
                    .isEqualTo(1);
            assertThat(groupRepository.count()).isGreaterThanOrEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ===================================================================================
    // C. Ordinary Ticket 4E-1 promotion races with canonical-group creation on the same candidate lock
    // ===================================================================================

    @Test
    void ticket4E1PromotionAndCanonicalGroupCreationSerializeOnTheSharedCandidateLock() throws Exception {
        // Deliberately NOT the shared canonical JLPT-MAX sourceRef: promote() is expected to fail here
        // regardless of source (it is permanently blocked by SAME_CONTENT_CANONICAL_SELECTION_REQUIRED
        // the moment X is in a fresh SAME_CONTENT pair - a business-rule fact, not a race outcome; see
        // this class's javadoc), so no rights/meaning-language/provenance setup is needed to make
        // promotion *reachable* - only the shared candidate-row lock matters for this boundary test.
        // Using a fresh, unique sourceRef also avoids permanently polluting the shared canonical
        // sourceRef other non-@Transactional test classes in this suite rely on having a predictable
        // candidate count for (the same class of fragility already documented for
        // NormalizedVocabularyCandidatePromotionServiceConcurrencyTest's own fixture choices).
        String raceRef = ref();
        long noteA = 1L;
        long noteB = 2L;
        store.saveVocabulary(vocab(raceRef, noteA));
        store.saveVocabulary(vocab(raceRef, noteB));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, raceRef);
        List<NormalizedContentCandidate> pairCandidates = candidateRepository
                .findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, raceRef).stream()
                .sorted(Comparator.comparing(NormalizedContentCandidate::getId))
                .toList();
        assertThat(pairCandidates).hasSize(2);
        NormalizedContentCandidate x = pairCandidates.get(0);
        NormalizedContentCandidate y = pairCandidates.get(1);
        // X is now permanently blocked from individual 4E-1 promotion by
        // SAME_CONTENT_CANONICAL_SELECTION_REQUIRED once this review is recorded - by business rule
        // alone, not merely by lock timing - proving promote() never succeeds here regardless of the
        // race is the actual point of this boundary test (see class javadoc).
        reviewSameContent(x, y, admin("race-review"));
        CanonicalGroupCreationRequest groupRequest = twoWayRequest(x, y, x.getId());
        Long xId = x.getId();
        Instant xNormalizedAt = x.getNormalizedAt();
        long contentItemsBefore = jdbcClient.sql("select count(*) from content_items").query(Long.class).single();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Boolean> promoteAttempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    promotion.promote(NormalizedCandidateType.VOCABULARY, xId, xNormalizedAt);
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            };
            Callable<Outcome> createAttempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                return runOne(() ->
                        canonicalGroups.create(NormalizedCandidateType.VOCABULARY, groupRequest, admin("race-create")));
            };
            Future<Boolean> promoteFuture = pool.submit(promoteAttempt);
            Future<Outcome> createFuture = pool.submit(createAttempt);
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();
            boolean promoted = promoteFuture.get(30, TimeUnit.SECONDS);
            Outcome created = createFuture.get(30, TimeUnit.SECONDS);

            assertThat(promoted).as("promote() must never succeed while X is in a fresh SAME_CONTENT pair").isFalse();
            long contentItemsAfter = jdbcClient.sql("select count(*) from content_items").query(Long.class).single();
            assertThat(contentItemsAfter).as("the racing (always-doomed) promote attempt must never create a ContentItem")
                    .isEqualTo(contentItemsBefore);
            assertThat(created.succeeded())
                    .as("canonical-group creation itself must still succeed despite the concurrent promote attempt: %s",
                            created)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    // ===================================================================================
    // D. Pair review change races with group creation (repeated)
    // ===================================================================================

    @Test
    void pairReviewChangeRaceNeverProducesAGroupWithNonSameContentEvidence() throws Exception {
        for (int iter = 0; iter < 5; iter++) {
            final int i = iter;
            List<NormalizedContentCandidate> ab = seedClique(ref(), 2);
            NormalizedContentCandidate a = ab.get(0);
            NormalizedContentCandidate b = ab.get(1);
            NormalizedCandidatePairReview review = reviewSameContent(a, b, admin("d-review-" + i));
            CanonicalGroupCreationRequest request = twoWayRequest(a, b, a.getId());
            long reviewedVersion = review.getVersion();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch bothReady = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            try {
                Callable<Outcome> createAttempt = () -> {
                    bothReady.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    return runOne(() ->
                            canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d-create-" + i)));
                };
                Callable<Boolean> reReviewAttempt = () -> {
                    bothReady.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    try {
                        Long lo = Math.min(a.getId(), b.getId());
                        Long hi = Math.max(a.getId(), b.getId());
                        NormalizedCandidateMatchPair pair =
                                pairRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi).orElseThrow();
                        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                                new DecisionSubmission(lo, hi, HumanReviewDecision.DISTINCT_CONTENT, "재검토",
                                        pair.getGeneratedAt(), pair.getAssessment(), reviewedVersion),
                                admin("d-rereview-" + i));
                        return true;
                    } catch (RuntimeException e) {
                        return false;
                    }
                };
                Future<Outcome> createFuture = pool.submit(createAttempt);
                Future<Boolean> reReviewFuture = pool.submit(reReviewAttempt);
                bothReady.await(10, TimeUnit.SECONDS);
                go.countDown();
                Outcome created = createFuture.get(30, TimeUnit.SECONDS);
                reReviewFuture.get(30, TimeUnit.SECONDS);

                if (created.succeeded()) {
                    // If creation won the race, its immutable edge must genuinely record SAME_CONTENT -
                    // never a torn/half-applied DISTINCT_CONTENT state, regardless of what the
                    // concurrent re-review thread did afterward.
                    List<NormalizedCandidateCanonicalGroupEdge> edges =
                            edgeRepository.findByGroup_IdOrderByIdAsc(created.result().groupId());
                    assertThat(edges).hasSize(1);
                    assertThat(edges.get(0).getReviewDecisionSnapshot()).isEqualTo(HumanReviewDecision.SAME_CONTENT);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    // ===================================================================================
    // E. Ticket 4B analyzer reanalysis races with group creation (repeated)
    // ===================================================================================

    @Test
    void analyzerReanalysisRaceNeverCorruptsGroupCreationOrDeadlocks() throws Exception {
        for (int iter = 0; iter < 5; iter++) {
            final int i = iter;
            String ref = ref();
            List<NormalizedContentCandidate> ab = seedClique(ref, 2);
            NormalizedContentCandidate a = ab.get(0);
            NormalizedContentCandidate b = ab.get(1);
            reviewSameContent(a, b, admin("e-review-" + i));
            CanonicalGroupCreationRequest request = twoWayRequest(a, b, a.getId());

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch bothReady = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            try {
                Callable<Outcome> createAttempt = () -> {
                    bothReady.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    return runOne(() ->
                            canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("e-create-" + i)));
                };
                Callable<Boolean> reanalyzeAttempt = () -> {
                    bothReady.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    try {
                        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
                        return true;
                    } catch (RuntimeException e) {
                        return false;
                    }
                };
                Future<Outcome> createFuture = pool.submit(createAttempt);
                Future<Boolean> reanalyzeFuture = pool.submit(reanalyzeAttempt);
                bothReady.await(10, TimeUnit.SECONDS);
                go.countDown();
                // No deadlock/timeout under either ordering - the actual invariant this scenario proves.
                Outcome created = createFuture.get(30, TimeUnit.SECONDS);
                boolean reanalyzed = reanalyzeFuture.get(30, TimeUnit.SECONDS);
                assertThat(reanalyzed).as("Ticket 4B reanalysis itself must never fail/deadlock because of a concurrent "
                        + "canonical-group creation").isTrue();

                int groupCount = memberRepository.findByMemberCandidate_IdIn(List.of(a.getId(), b.getId())).size();
                assertThat(groupCount)
                        .as("iteration %d: at most one of A/B's membership rows may exist after the race (0 if creation "
                                + "lost/rejected, 2 if it won - never a partial 1)", i)
                        .isIn(0, 2);
                if (created.succeeded()) {
                    assertThat(groupCount).isEqualTo(2);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
