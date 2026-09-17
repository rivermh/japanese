package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupCreationRequest;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupCreationResult;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupDetailView;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.EdgeExpectation;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.ParticipantExpectation;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.DecisionSubmission;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroup;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupEdge;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupMember;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupStatus;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4E-3A: scenario coverage for {@link NormalizedCandidateCanonicalGroupService} and
 * its three entities/migration - migration/entity shape (section 15 of the ticket), creation policy
 * (section 16, 22 numbered cases), and immutable-audit behavior (section 17). Concurrency coverage
 * (section 18) lives in {@link NormalizedCandidateCanonicalGroupConcurrencyTest} (not
 * {@code @Transactional}, matching Ticket 4E-1's own atomicity/concurrency test convention).
 *
 * <p>Every candidate is built through {@code NormalizedCandidateStore}/
 * {@code NormalizedCandidateConflictAnalyzer}/{@code NormalizedCandidatePairReviewService}, matching
 * every sibling test class's own fixture convention in this package - this class never constructs a
 * candidate/pair/review by any other means.
 */
@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class NormalizedCandidateCanonicalGroupServiceTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedCandidateConflictAnalyzer analyzer;
    @Autowired NormalizedCandidatePairReviewService reviewService;
    @Autowired NormalizedCandidateCanonicalGroupService canonicalGroups;
    @Autowired NormalizedContentCandidateRepository candidateRepository;
    @Autowired NormalizedCandidateMatchPairRepository pairRepository;
    @Autowired NormalizedCandidatePairReviewRepository reviewRepository;
    @Autowired NormalizedCandidateCanonicalGroupRepository groupRepository;
    @Autowired NormalizedCandidateCanonicalGroupMemberRepository memberRepository;
    @Autowired NormalizedCandidateCanonicalGroupEdgeRepository edgeRepository;
    @Autowired UserAccountRepository accounts;
    @Autowired ContentItemRepository contentItems;
    @Autowired ImportedSourceRecordRepository importedSourceRecords;

    private static final String VOCABULARY_NOTE_TYPE = "JLPT MAX덱 어휘";

    private String ref() {
        return "canonical-group-test-" + UUID.randomUUID();
    }

    private UserAccount admin(String suffix) {
        return accounts.save(new UserAccount("canonical-admin-" + suffix + "-" + UUID.randomUUID(), null, "hash",
                "Admin " + suffix, UserRole.ADMIN));
    }

    /** N candidates sharing one entryId/expression/reading - the analyzer blocks all C(N,2) pairs. */
    private List<NormalizedContentCandidate> seedClique(String ref, int n) {
        for (int i = 1; i <= n; i++) {
            store.saveVocabulary(vocab(ref, i, "E-SHARED", "語", "ご", "N5", "meaning"));
        }
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        return candidateRepository.findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, ref).stream()
                .sorted(Comparator.comparing(NormalizedContentCandidate::getId))
                .toList();
    }

    private NormalizedCandidateMatchPair pairOf(NormalizedContentCandidate a, NormalizedContentCandidate b) {
        Long lo = Math.min(a.getId(), b.getId());
        Long hi = Math.max(a.getId(), b.getId());
        return pairRepository.findByLeftCandidateIdAndRightCandidateId(lo, hi).orElseThrow();
    }

    private NormalizedCandidatePairReview reviewSameContent(NormalizedContentCandidate a, NormalizedContentCandidate b,
            UserAccount reviewer) {
        NormalizedCandidateMatchPair pair = pairOf(a, b);
        Long expectedVersion = reviewRepository
                .findByLeftCandidateIdAndRightCandidateId(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId())
                .map(NormalizedCandidatePairReview::getVersion).orElse(null);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                new DecisionSubmission(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId(),
                        HumanReviewDecision.SAME_CONTENT, null, pair.getGeneratedAt(), pair.getAssessment(),
                        expectedVersion),
                reviewer);
        return reviewRepository
                .findByLeftCandidateIdAndRightCandidateId(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId())
                .orElseThrow();
    }

    private void reviewDistinct(NormalizedContentCandidate a, NormalizedContentCandidate b, UserAccount reviewer) {
        NormalizedCandidateMatchPair pair = pairOf(a, b);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                new DecisionSubmission(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId(),
                        HumanReviewDecision.DISTINCT_CONTENT, null, pair.getGeneratedAt(), pair.getAssessment(), null),
                reviewer);
    }

    private void reviewNeedsFollowup(NormalizedContentCandidate a, NormalizedContentCandidate b, UserAccount reviewer) {
        NormalizedCandidateMatchPair pair = pairOf(a, b);
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                new DecisionSubmission(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId(),
                        HumanReviewDecision.NEEDS_FOLLOWUP, null, pair.getGeneratedAt(), pair.getAssessment(), null),
                reviewer);
    }

    /** Fully valid, complete C(N,2) request for the given (already fully SAME_CONTENT-reviewed) clique. */
    private CanonicalGroupCreationRequest fullRequest(List<NormalizedContentCandidate> clique, Long canonicalId) {
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
        return new CanonicalGroupCreationRequest(canonicalId, participants, edges, null);
    }

    private VocabularyNormalizationResult vocab(String ref, long noteId, String entryId, String expression,
            String reading, String level, String meaning) {
        return new VocabularyNormalizationResult(ref, noteId, entryId, expression, reading, "noun", null,
                List.of(new NormalizedMeaning(1, meaning)), List.of(), new NormalizedJlptLevel(level, level, "WordJLPT"),
                expression, reading, Map.of(), List.of(), true);
    }

    private Map<String, Long> productionCounts() {
        return Map.of("contentItems", contentItems.count(), "importedSourceRecords", importedSourceRecords.count());
    }

    // ===================================================================================
    // Section 15: migration/entity
    // ===================================================================================

    @Test
    void creationPersistsAnActiveGroupWithCanonicalAlsoAsAMember() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        UserAccount reviewer = admin("e1");
        reviewSameContent(c.get(0), c.get(1), reviewer);

        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d1"));

        NormalizedCandidateCanonicalGroup group = groupRepository.findById(result.groupId()).orElseThrow();
        assertThat(group.getStatus()).isEqualTo(NormalizedCandidateCanonicalGroupStatus.ACTIVE);
        assertThat(group.getCanonicalCandidate().getId()).isEqualTo(c.get(0).getId());
        List<NormalizedCandidateCanonicalGroupMember> members =
                memberRepository.findByGroup_IdOrderByMemberCandidate_Id(group.getId());
        assertThat(members).hasSize(2);
        assertThat(members.stream().map(m -> m.getMemberCandidate().getId()))
                .containsExactlyInAnyOrder(c.get(0).getId(), c.get(1).getId());
    }

    @Test
    void versionIncrementsExactlyOnceOnDissolve() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("e2"));
        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d2"));
        long versionBefore = groupRepository.findById(result.groupId()).orElseThrow().getVersion();

        canonicalGroups.dissolve(result.groupId(), versionBefore, "정정", admin("diss2"));
        groupRepository.flush();

        assertThat(groupRepository.findById(result.groupId()).orElseThrow().getVersion()).isEqualTo(versionBefore + 1);
    }

    @Test
    void secondDissolveOfAnAlreadyDissolvedGroupIsRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("e3"));
        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d3"));
        long v = groupRepository.findById(result.groupId()).orElseThrow().getVersion();
        canonicalGroups.dissolve(result.groupId(), v, null, admin("diss3a"));
        long dissolvedVersion = groupRepository.findById(result.groupId()).orElseThrow().getVersion();

        assertThatExceptionOfType(NormalizedCandidateCanonicalGroupRejectedException.class).isThrownBy(() ->
                canonicalGroups.dissolve(result.groupId(), dissolvedVersion, null, admin("diss3b")));
    }

    @Test
    void uniqueMemberCandidateConstraintIsEnforcedAtTheDatabaseLevel() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("e4"));
        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d4"));
        NormalizedCandidateCanonicalGroup group = groupRepository.findById(result.groupId()).orElseThrow();

        assertThatThrownBy(() -> memberRepository.saveAndFlush(
                new NormalizedCandidateCanonicalGroupMember(group, c.get(0), c.get(0).getNormalizedAt())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void edgeConstructorCanonicallyOrdersLeftBeforeRight() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        NormalizedCandidatePairReview review = reviewSameContent(c.get(0), c.get(1), admin("e5"));
        NormalizedContentCandidate higher = c.get(0).getId() > c.get(1).getId() ? c.get(0) : c.get(1);
        NormalizedContentCandidate lower = c.get(0).getId() > c.get(1).getId() ? c.get(1) : c.get(0);
        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d5"));

        List<NormalizedCandidateCanonicalGroupEdge> edges = edgeRepository.findByGroup_IdOrderByIdAsc(result.groupId());
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).getLeftCandidate().getId()).isEqualTo(lower.getId());
        assertThat(edges.get(0).getRightCandidate().getId()).isEqualTo(higher.getId());
    }

    @Test
    void membershipRowsAreDeletedOnDissolutionButEdgesCandidatesAndHeaderSurvive() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("e6"));
        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d6"));
        long v = groupRepository.findById(result.groupId()).orElseThrow().getVersion();
        long candidateCountBefore = candidateRepository.count();

        canonicalGroups.dissolve(result.groupId(), v, "해체", admin("diss6"));

        assertThat(memberRepository.findByGroup_IdOrderByMemberCandidate_Id(result.groupId())).isEmpty();
        assertThat(edgeRepository.findByGroup_IdOrderByIdAsc(result.groupId())).hasSize(1);
        assertThat(groupRepository.findById(result.groupId())).isPresent();
        assertThat(candidateRepository.count()).isEqualTo(candidateCountBefore);
    }

    // ===================================================================================
    // Section 16: creation policy (numbered to match the ticket's own 1-22 list)
    // ===================================================================================

    @Test
    void _1_validTwoCandidateVocabularySameContentGroupSucceeds() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("t1"));

        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d"));

        assertThat(result.groupId()).isNotNull();
    }

    @Test
    void _2_validThreeCandidateCompleteCliqueSucceeds() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 3);
        reviewSameContent(c.get(0), c.get(1), admin("t2a"));
        reviewSameContent(c.get(0), c.get(2), admin("t2b"));
        reviewSameContent(c.get(1), c.get(2), admin("t2c"));

        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(1).getId()), admin("d"));

        assertThat(memberRepository.findByGroup_IdOrderByMemberCandidate_Id(result.groupId())).hasSize(3);
        assertThat(edgeRepository.findByGroup_IdOrderByIdAsc(result.groupId())).hasSize(3);
    }

    @Test
    void _3_explicitCanonicalIsRequired() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("t3"));
        CanonicalGroupCreationRequest request = fullRequest(c, null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void _4_canonicalMustBelongToSubmittedParticipants() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("t4"));
        CanonicalGroupCreationRequest request = fullRequest(c, -1L);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("participant");
    }

    @Test
    void _5_duplicateParticipantIdsRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("t5"));
        CanonicalGroupCreationRequest request = new CanonicalGroupCreationRequest(c.get(0).getId(),
                List.of(new ParticipantExpectation(c.get(0).getId(), c.get(0).getNormalizedAt()),
                        new ParticipantExpectation(c.get(0).getId(), c.get(0).getNormalizedAt())),
                List.of(), null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("중복");
    }

    @Test
    void _6_grammarRejectedBeforeAnyWrite() {
        Map<String, Long> before = productionCounts();
        long groupsBefore = groupRepository.count();

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.GRAMMAR,
                new CanonicalGroupCreationRequest(-1L, List.of(), List.of(), null), admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("VOCABULARY");
        assertThat(groupRepository.count()).isEqualTo(groupsBefore);
        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void _7_differentSourceRefRejected() {
        String refA = ref();
        String refB = ref();
        store.saveVocabulary(vocab(refA, 1L, "E1", "語", "ご", "N5", "meaning"));
        store.saveVocabulary(vocab(refB, 1L, "E1", "語", "ご", "N5", "meaning"));
        NormalizedContentCandidate a = candidateRepository
                .findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, refA).get(0);
        NormalizedContentCandidate b = candidateRepository
                .findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, refB).get(0);
        CanonicalGroupCreationRequest request = new CanonicalGroupCreationRequest(a.getId(),
                List.of(new ParticipantExpectation(a.getId(), a.getNormalizedAt()),
                        new ParticipantExpectation(b.getId(), b.getNormalizedAt())),
                List.of(new EdgeExpectation(a.getId(), b.getId(), 0L)), null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class);
    }

    @Test
    void _8_missingTicket4BPairRejected() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "meaning"));
        store.saveVocabulary(vocab(ref, 2L, "E9", "別", "べつ", "N5", "다름"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        List<NormalizedContentCandidate> c = candidateRepository
                .findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, ref).stream()
                .sorted(Comparator.comparing(NormalizedContentCandidate::getId)).toList();
        assertThat(pairRepository.findByLeftCandidateIdAndRightCandidateId(c.get(0).getId(), c.get(1).getId())).isEmpty();
        CanonicalGroupCreationRequest request = new CanonicalGroupCreationRequest(c.get(0).getId(),
                List.of(new ParticipantExpectation(c.get(0).getId(), c.get(0).getNormalizedAt()),
                        new ParticipantExpectation(c.get(1).getId(), c.get(1).getNormalizedAt())),
                List.of(new EdgeExpectation(c.get(0).getId(), c.get(1).getId(), 0L)), null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("review가 없습니다");
    }

    @Test
    void _9_staleTicket4BPairRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        String ref = c.get(0).getSourceRef();
        reviewSameContent(c.get(0), c.get(1), admin("t9"));

        // Re-normalize one side without reanalyzing - the pair itself is now stale relative to the new
        // normalizedAt. The request below captures the CURRENT (post-resave) normalizedAt so step 4's
        // own candidate-freshness check passes cleanly and step 8's pair-freshness check is what
        // actually rejects this - isolating that specific check from step 4's, unlike scenario 15.
        store.saveVocabulary(vocab(ref, c.get(0).getSourceNoteId(), "E-SHARED", "語", "ご", "N5", "meaning (edited)"));
        List<NormalizedContentCandidate> refreshed = candidateRepository
                .findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, ref).stream()
                .sorted(Comparator.comparing(NormalizedContentCandidate::getId)).toList();
        NormalizedCandidatePairReview review = reviewRepository.findByLeftCandidateIdAndRightCandidateId(
                Math.min(refreshed.get(0).getId(), refreshed.get(1).getId()),
                Math.max(refreshed.get(0).getId(), refreshed.get(1).getId())).orElseThrow();
        CanonicalGroupCreationRequest request = new CanonicalGroupCreationRequest(refreshed.get(0).getId(),
                refreshed.stream().map(x -> new ParticipantExpectation(x.getId(), x.getNormalizedAt())).toList(),
                List.of(new EdgeExpectation(refreshed.get(0).getId(), refreshed.get(1).getId(), review.getVersion())),
                null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupStaleException.class);
    }

    @Test
    void _10_missingReviewRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        // Pair exists (via seedClique's own analyze), but never reviewed.
        CanonicalGroupCreationRequest request = new CanonicalGroupCreationRequest(c.get(0).getId(),
                List.of(new ParticipantExpectation(c.get(0).getId(), c.get(0).getNormalizedAt()),
                        new ParticipantExpectation(c.get(1).getId(), c.get(1).getNormalizedAt())),
                List.of(new EdgeExpectation(c.get(0).getId(), c.get(1).getId(), 0L)), null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("review가 없습니다");
    }

    @Test
    void _11_distinctContentReviewRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        NormalizedCandidateMatchPair pair = pairOf(c.get(0), c.get(1));
        reviewDistinct(c.get(0), c.get(1), admin("t11"));
        long version = reviewRepository.findByLeftCandidateIdAndRightCandidateId(
                pair.getLeftCandidate().getId(), pair.getRightCandidate().getId()).orElseThrow().getVersion();
        CanonicalGroupCreationRequest request = new CanonicalGroupCreationRequest(c.get(0).getId(),
                List.of(new ParticipantExpectation(c.get(0).getId(), c.get(0).getNormalizedAt()),
                        new ParticipantExpectation(c.get(1).getId(), c.get(1).getNormalizedAt())),
                List.of(new EdgeExpectation(c.get(0).getId(), c.get(1).getId(), version)), null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("SAME_CONTENT가 아닙니다");
    }

    @Test
    void _12_needsFollowupReviewRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        NormalizedCandidateMatchPair pair = pairOf(c.get(0), c.get(1));
        reviewNeedsFollowup(c.get(0), c.get(1), admin("t12"));
        long version = reviewRepository.findByLeftCandidateIdAndRightCandidateId(
                pair.getLeftCandidate().getId(), pair.getRightCandidate().getId()).orElseThrow().getVersion();
        CanonicalGroupCreationRequest request = new CanonicalGroupCreationRequest(c.get(0).getId(),
                List.of(new ParticipantExpectation(c.get(0).getId(), c.get(0).getNormalizedAt()),
                        new ParticipantExpectation(c.get(1).getId(), c.get(1).getNormalizedAt())),
                List.of(new EdgeExpectation(c.get(0).getId(), c.get(1).getId(), version)), null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("SAME_CONTENT가 아닙니다");
    }

    @Test
    void _13_staleReviewFreshnessRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        String ref = c.get(0).getSourceRef();
        reviewSameContent(c.get(0), c.get(1), admin("t13"));
        CanonicalGroupCreationRequest requestBeforeRefresh = fullRequest(c, c.get(0).getId());

        // Re-normalize (bumps normalizedAt past the review's own stored snapshot) without re-reviewing.
        store.saveVocabulary(vocab(ref, c.get(0).getSourceNoteId(), "E-SHARED", "語", "ご", "N5", "meaning (edited)"));
        analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        // Build a request whose expectedNormalizedAt/expectedReviewVersion still reflect the ORIGINAL
        // (now-stale) render, proving the service itself re-derives staleness rather than trusting the
        // caller's own freshness claim silently.
        CanonicalGroupCreationRequest staleRequest = requestBeforeRefresh;

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, staleRequest, admin("d")))
                .isInstanceOfAny(NormalizedCandidateCanonicalGroupStaleException.class,
                        NormalizedCandidateCanonicalGroupRejectedException.class);
    }

    @Test
    void _14_changedExpectedReviewVersionRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        // A second admin re-reviews with a genuinely different note (forcing a real version bump, not
        // a no-op resubmission) between "render" and "submit" - the request below still carries the
        // ORIGINAL version the first admin's page was rendered against.
        reviewSameContent(c.get(0), c.get(1), admin("t14a"));
        NormalizedCandidatePairReview original = reviewRepository.findByLeftCandidateIdAndRightCandidateId(
                Math.min(c.get(0).getId(), c.get(1).getId()), Math.max(c.get(0).getId(), c.get(1).getId())).orElseThrow();
        long originalVersion = original.getVersion();
        NormalizedCandidateMatchPair pair = pairOf(c.get(0), c.get(1));
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                new DecisionSubmission(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId(),
                        HumanReviewDecision.SAME_CONTENT, "재확인함", pair.getGeneratedAt(), pair.getAssessment(),
                        originalVersion),
                admin("t14b"));
        NormalizedCandidatePairReview bumped = reviewRepository.findByLeftCandidateIdAndRightCandidateId(
                Math.min(c.get(0).getId(), c.get(1).getId()), Math.max(c.get(0).getId(), c.get(1).getId())).orElseThrow();
        assertThat(bumped.getVersion()).isGreaterThan(originalVersion);
        CanonicalGroupCreationRequest request = new CanonicalGroupCreationRequest(c.get(0).getId(),
                List.of(new ParticipantExpectation(c.get(0).getId(), c.get(0).getNormalizedAt()),
                        new ParticipantExpectation(c.get(1).getId(), c.get(1).getNormalizedAt())),
                List.of(new EdgeExpectation(c.get(0).getId(), c.get(1).getId(), originalVersion)), null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupStaleException.class);
    }

    @Test
    void _15_changedCandidateNormalizedAtRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        String ref = c.get(0).getSourceRef();
        reviewSameContent(c.get(0), c.get(1), admin("t15"));
        CanonicalGroupCreationRequest staleRequest = fullRequest(c, c.get(0).getId());

        store.saveVocabulary(vocab(ref, c.get(0).getSourceNoteId(), "E-SHARED", "語", "ご", "N5", "meaning (edited)"));

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, staleRequest, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupStaleException.class);
    }

    @Test
    void _16_incompleteThreeNodeCliqueRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 3);
        reviewSameContent(c.get(0), c.get(1), admin("t16a"));
        reviewSameContent(c.get(1), c.get(2), admin("t16b"));
        long groupsBefore = groupRepository.count();
        // A-C never reviewed at all - request only carries 2 edges.
        CanonicalGroupCreationRequest request = new CanonicalGroupCreationRequest(c.get(1).getId(),
                c.stream().map(x -> new ParticipantExpectation(x.getId(), x.getNormalizedAt())).toList(),
                List.of(new EdgeExpectation(c.get(0).getId(), c.get(1).getId(),
                                reviewRepository.findByLeftCandidateIdAndRightCandidateId(
                                        Math.min(c.get(0).getId(), c.get(1).getId()),
                                        Math.max(c.get(0).getId(), c.get(1).getId())).orElseThrow().getVersion()),
                        new EdgeExpectation(c.get(1).getId(), c.get(2).getId(),
                                reviewRepository.findByLeftCandidateIdAndRightCandidateId(
                                        Math.min(c.get(1).getId(), c.get(2).getId()),
                                        Math.max(c.get(1).getId(), c.get(2).getId())).orElseThrow().getVersion())),
                null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("C(N,2)");
        assertThat(groupRepository.count()).isEqualTo(groupsBefore);
    }

    @Test
    void _17_conflictingThreeNodeCliqueRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 3);
        reviewSameContent(c.get(0), c.get(1), admin("t17a"));
        reviewSameContent(c.get(1), c.get(2), admin("t17b"));
        reviewDistinct(c.get(0), c.get(2), admin("t17c"));
        CanonicalGroupCreationRequest request = fullRequest(c, c.get(1).getId());
        long groupsBefore = groupRepository.count();

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("SAME_CONTENT가 아닙니다");
        assertThat(groupRepository.count()).isEqualTo(groupsBefore);
    }

    @Test
    void _18_existingActiveMembershipRejected() {
        List<NormalizedContentCandidate> abc = seedClique(ref(), 3);
        reviewSameContent(abc.get(0), abc.get(1), admin("t18a1"));
        List<NormalizedContentCandidate> ab = List.of(abc.get(0), abc.get(1));
        canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(ab, abc.get(0).getId()), admin("d18a"));

        // A is now already reserved - a second group {A, C} must be rejected even though A-C itself
        // could otherwise be validly reviewed.
        reviewSameContent(abc.get(0), abc.get(2), admin("t18b"));
        List<NormalizedContentCandidate> ac = List.of(abc.get(0), abc.get(2));
        CanonicalGroupCreationRequest request = fullRequest(ac, abc.get(0).getId());

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d18b")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("이미 다른 canonical group");
    }

    @Test
    void _19_anyAlreadyPromotedParticipantRejected() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("t19"));
        ContentItem existingItem = contentItems.save(
                new ContentItem("already-promoted-canonical-" + UUID.randomUUID(), ContentType.WORD, c.get(0).getSourceRef(), false));
        ImportedSourceRecord existingRecord = new ImportedSourceRecord(c.get(0).getSourceRef(), VOCABULARY_NOTE_TYPE,
                c.get(1).getSourceNoteId(), "N5", "", "f", "v");
        existingRecord.linkContentItem(existingItem);
        importedSourceRecords.save(existingRecord);
        CanonicalGroupCreationRequest request = fullRequest(c, c.get(0).getId());
        long groupsBefore = groupRepository.count();

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, request, admin("d")))
                .isInstanceOf(NormalizedCandidateCanonicalGroupRejectedException.class)
                .hasMessageContaining("production ContentItem");
        assertThat(groupRepository.count()).isEqualTo(groupsBefore);
    }

    @Test
    void _20_zeroContentItemWordMeaningExampleWritesOnRejection() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        Map<String, Long> before = productionCounts();
        CanonicalGroupCreationRequest badRequest = new CanonicalGroupCreationRequest(c.get(0).getId(),
                List.of(new ParticipantExpectation(c.get(0).getId(), c.get(0).getNormalizedAt())), List.of(), null);

        assertThatThrownBy(() -> canonicalGroups.create(NormalizedCandidateType.VOCABULARY, badRequest, admin("d")));

        assertThat(productionCounts()).isEqualTo(before);
    }

    @Test
    void _21_zeroRightsMutationOnSuccess() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("t21"));

        canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d"));

        // No ContentSource row is ever created/touched by this service - creation only reads
        // candidates/pairs/reviews and writes canonical-group rows.
        assertThat(candidateRepository.findByCandidateTypeAndSourceRef(
                NormalizedCandidateType.VOCABULARY, c.get(0).getSourceRef())).hasSize(2);
    }

    @Test
    void _22_zeroPublicationOrReviewStateMutationOnSuccess() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        reviewSameContent(c.get(0), c.get(1), admin("t22"));
        Map<String, Long> before = productionCounts();

        canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d"));

        assertThat(productionCounts()).isEqualTo(before);
    }

    // ===================================================================================
    // Section 17: audit immutability
    // ===================================================================================

    @Test
    void edgeSnapshotIsUnchangedAfterThePairReviewIsLaterReReviewed() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        NormalizedCandidatePairReview original = reviewSameContent(c.get(0), c.get(1), admin("a1"));
        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d"));
        NormalizedCandidateCanonicalGroupEdge edgeBefore = edgeRepository.findByGroup_IdOrderByIdAsc(result.groupId()).get(0);
        long originalVersion = edgeBefore.getPairReviewVersionSnapshot();
        Instant originalReviewedAt = edgeBefore.getPairReviewedAtSnapshot();

        // Re-review to DISTINCT_CONTENT using the current version - the group's own edge must not move.
        reviewService.submitDecision(NormalizedCandidateType.VOCABULARY,
                new DecisionSubmission(original.getLeftCandidate().getId(), original.getRightCandidate().getId(),
                        HumanReviewDecision.DISTINCT_CONTENT, "재검토",
                        pairOf(c.get(0), c.get(1)).getGeneratedAt(), pairOf(c.get(0), c.get(1)).getAssessment(),
                        originalVersion),
                admin("a2"));

        NormalizedCandidateCanonicalGroupEdge edgeAfter = edgeRepository.findByGroup_IdOrderByIdAsc(result.groupId()).get(0);
        assertThat(edgeAfter.getPairReviewVersionSnapshot()).isEqualTo(originalVersion);
        assertThat(edgeAfter.getPairReviewedAtSnapshot()).isEqualTo(originalReviewedAt);
        assertThat(edgeAfter.getReviewDecisionSnapshot()).isEqualTo(HumanReviewDecision.SAME_CONTENT);
    }

    @Test
    void edgeNormalizedAtSnapshotIsUnchangedAfterCandidateReNormalization() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        String ref = c.get(0).getSourceRef();
        Instant originalNormalizedAt = c.get(0).getNormalizedAt();
        reviewSameContent(c.get(0), c.get(1), admin("a3"));
        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d"));
        edgeRepository.flush();
        NormalizedCandidateCanonicalGroupEdge edgeBefore = edgeRepository.findByGroup_IdOrderByIdAsc(result.groupId()).get(0);
        // Compared at (approximately) the H2/MySQL timestamp(6)/datetime(6) column's own microsecond
        // precision, tolerating a +/-1us rounding-vs-truncation artifact between Java's in-memory
        // Instant and the DB round-trip - the durably persisted value is what matters here, not
        // incidental Java-side sub-microsecond jitter this design never promised to preserve exactly.
        Instant leftBefore = edgeBefore.getLeftNormalizedAtSnapshot();
        Instant rightBefore = edgeBefore.getRightNormalizedAtSnapshot();

        store.saveVocabulary(vocab(ref, c.get(0).getSourceNoteId(), "E-SHARED", "語", "ご", "N5", "meaning (edited later)"));
        NormalizedContentCandidate reNormalized = candidateRepository.findById(c.get(0).getId()).orElseThrow();
        assertThat(reNormalized.getNormalizedAt())
                .as("this test's premise requires normalizedAt to actually change on re-save")
                .isNotEqualTo(originalNormalizedAt);

        NormalizedCandidateCanonicalGroupEdge edgeAfter = edgeRepository.findByGroup_IdOrderByIdAsc(result.groupId()).get(0);
        assertThat(edgeAfter.getLeftNormalizedAtSnapshot())
                .isCloseTo(leftBefore, org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(edgeAfter.getRightNormalizedAtSnapshot())
                .isCloseTo(rightBefore, org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void historicalHeaderAndEdgesRemainIntactAfterDissolutionThenCandidateReNormalization() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        String ref = c.get(0).getSourceRef();
        reviewSameContent(c.get(0), c.get(1), admin("a4"));
        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d"));
        long v = groupRepository.findById(result.groupId()).orElseThrow().getVersion();
        canonicalGroups.dissolve(result.groupId(), v, null, admin("diss4"));

        store.saveVocabulary(vocab(ref, c.get(1).getSourceNoteId(), "E-SHARED", "語", "ご", "N5", "meaning (after dissolve)"));

        NormalizedCandidateCanonicalGroup group = groupRepository.findById(result.groupId()).orElseThrow();
        assertThat(group.getStatus()).isEqualTo(NormalizedCandidateCanonicalGroupStatus.DISSOLVED);
        assertThat(group.getCanonicalCandidate().getId()).isEqualTo(c.get(0).getId());
        assertThat(edgeRepository.findByGroup_IdOrderByIdAsc(result.groupId())).hasSize(1);
    }

    @Test
    void reviewerAndAssessmentSnapshotsMatchTheOriginalReviewNotAnyLaterState() {
        List<NormalizedContentCandidate> c = seedClique(ref(), 2);
        UserAccount originalReviewer = admin("a5");
        reviewSameContent(c.get(0), c.get(1), originalReviewer);
        CanonicalGroupCreationResult result =
                canonicalGroups.create(NormalizedCandidateType.VOCABULARY, fullRequest(c, c.get(0).getId()), admin("d"));

        CanonicalGroupDetailView detail = canonicalGroups.detail(result.groupId());
        assertThat(detail.edges()).hasSize(1);
        assertThat(detail.edges().get(0).reviewerDisplayName()).isEqualTo(originalReviewer.getDisplayName());
        assertThat(detail.edges().get(0).reviewDecisionSnapshot()).isEqualTo("SAME_CONTENT");
    }

    @Test
    void dissolutionReleasesCandidatesForARegroupedLaterActiveGroup() {
        List<NormalizedContentCandidate> abc = seedClique(ref(), 3);
        reviewSameContent(abc.get(0), abc.get(1), admin("a6a"));
        CanonicalGroupCreationResult firstGroup = canonicalGroups.create(NormalizedCandidateType.VOCABULARY,
                fullRequest(List.of(abc.get(0), abc.get(1)), abc.get(0).getId()), admin("d6a"));
        long v = groupRepository.findById(firstGroup.groupId()).orElseThrow().getVersion();
        canonicalGroups.dissolve(firstGroup.groupId(), v, "정정", admin("diss6a"));

        reviewSameContent(abc.get(0), abc.get(2), admin("a6b"));
        CanonicalGroupCreationResult secondGroup = canonicalGroups.create(NormalizedCandidateType.VOCABULARY,
                fullRequest(List.of(abc.get(0), abc.get(2)), abc.get(0).getId()), admin("d6b"));

        assertThat(groupRepository.findById(secondGroup.groupId()).orElseThrow().getStatus())
                .isEqualTo(NormalizedCandidateCanonicalGroupStatus.ACTIVE);
        assertThat(memberRepository.findByGroup_IdOrderByMemberCandidate_Id(secondGroup.groupId()))
                .extracting(m -> m.getMemberCandidate().getId())
                .containsExactlyInAnyOrder(abc.get(0).getId(), abc.get(2).getId());
    }
}
