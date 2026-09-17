package com.japanese.content.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupCreationRequest;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupCreationResult;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupDetailView;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupEdgeView;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupListResult;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupListRow;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.CanonicalGroupMemberView;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.EdgeExpectation;
import com.japanese.content.dto.NormalizedCandidateCanonicalGroupModels.ParticipantExpectation;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroup;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupEdge;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupMember;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupStatus;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.NormalizedGrammarCandidateDetail;
import com.japanese.content.entity.NormalizedVocabularyCandidateDetail;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.NormalizedCandidateCanonicalGroupEdgeRepository;
import com.japanese.content.repository.NormalizedCandidateCanonicalGroupMemberRepository;
import com.japanese.content.repository.NormalizedCandidateCanonicalGroupRepository;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4E-3A: persists an explicit HUMAN canonical-selection decision over a complete,
 * freshly reviewed SAME_CONTENT Vocabulary group - the private-candidate-domain decision of which
 * member candidate's normalized fields will eventually become one production {@code ContentItem}'s
 * content representation, and which other candidate identities share that same eventual production
 * provenance. This class NEVER writes, reads, or references any production entity ({@code ContentItem}/
 * {@code Word}/{@code Meaning}/{@code Example}/{@code ImportedSourceRecord} beyond the read-only
 * already-promoted check below) - actually converging production content/provenance onto one
 * {@code ContentItem} is Ticket 4E-3B's separate, later, not-yet-implemented scope.
 *
 * <p><b>Absolute boundary (never done here)</b>: no production entity is ever created or updated; no
 * candidate is ever deleted or has its source identity mutated; {@code ContentSource.rightsStatus} is
 * never touched; no {@code ContentItem} publication/review-state transition occurs; software never
 * selects the canonical candidate itself (it is always the caller-supplied
 * {@link CanonicalGroupCreationRequest#canonicalCandidateId()}) and never infers SAME_CONTENT via
 * transitivity - every required pairwise relationship must be an actual, currently fresh, currently
 * SAME_CONTENT {@link NormalizedCandidatePairReview} row.
 *
 * <p><b>Readiness is untouched</b>: recording a canonical group here has zero effect on
 * {@link NormalizedCandidatePromotionReadinessService} - every participating candidate keeps carrying
 * {@code SAME_CONTENT_CANONICAL_SELECTION_REQUIRED} from its own fresh SAME_CONTENT pair(s) exactly as
 * before, so {@link NormalizedVocabularyCandidatePromotionService#promote} continues to reject the
 * canonical candidate (or any member) via the exact same existing code path. Nothing in this class is
 * read by, or writes anything read by, that readiness service.
 *
 * <p><b>Transaction/lock protocol</b> (JLPT-MAX Ticket 4E-3A, final design-review round 4 - resource
 * hierarchy: CANDIDATES &rarr; PAIR REVIEWS &rarr; CANONICAL GROUP WRITES; Ticket 4B match-pair rows are
 * deliberately never separately locked - see {@code NormalizedCandidateConflictAnalyzer}'s own class
 * javadoc "Cross-service concurrency invariant" for why candidate-level locking alone is sufficient):
 * <ol>
 *   <li>Validate request shape with zero queries: {@code candidateType == VOCABULARY}, at least two
 *   distinct participant ids, the canonical id is among them, and the submitted edges correspond
 *   exactly to the participants' C(N,2) unordered pairs (no missing pair, no extra pair).</li>
 *   <li>Sort participant ids ascending.</li>
 *   <li>Lock every participating {@link NormalizedContentCandidate} row, {@code PESSIMISTIC_WRITE},
 *   one at a time, in that ascending order
 *   ({@link NormalizedContentCandidateRepository#findByIdAndCandidateTypeForCanonicalGroupCreation}).</li>
 *   <li>Revalidate from the now-locked candidate state: all exist and are VOCABULARY, all share one
 *   {@code sourceRef}, and every submitted {@code expectedNormalizedAt} still matches.</li>
 *   <li>Build the C(N,2) pair-key set (lowerCandidateId, higherCandidateId) from the sorted
 *   participant list.</li>
 *   <li>Lock every corresponding {@link NormalizedCandidatePairReview} row, {@code PESSIMISTIC_WRITE},
 *   in that same pair-key order
 *   ({@link NormalizedCandidatePairReviewRepository#findByLeftCandidateIdAndRightCandidateIdForCanonicalGroupCreation}) -
 *   reject if any is missing.</li>
 *   <li>Only after every review lock above is held, read the CURRENT
 *   {@link NormalizedCandidateMatchPair} for each pair identity via the existing plain (non-locking)
 *   lookup - safe only because, by this point, every participating candidate row is already locked by
 *   this transaction, and {@code NormalizedCandidateConflictAnalyzer.analyze} (Ticket 4B) now also locks
 *   those same candidate rows, in the same order, before it can mutate any pair row for them - so no
 *   reanalysis can be racing this read.</li>
 *   <li>Revalidate every edge from the now-locked/re-read state: current pair exists and is FRESH;
 *   review decision is exactly {@code SAME_CONTENT}; review is FRESH against the current pair/candidates;
 *   the submitted {@code expectedReviewVersion} still matches the locked review's current
 *   {@code @Version}.</li>
 *   <li>Verify no participant already carries a current canonical-group membership row.</li>
 *   <li>Verify no participant is already linked to a production {@code ContentItem} via
 *   {@code ImportedSourceRecord} - reject unconditionally on any hit (no B/E carve-outs; Ticket 4E-3B's
 *   own later, separate scope).</li>
 *   <li>Only then insert: one group header row, N membership rows, C(N,2) immutable edge rows.</li>
 *   <li>Commit atomically, or roll back the entire transaction on any failure.</li>
 * </ol>
 */
@Service
public class NormalizedCandidateCanonicalGroupService {

    /** Matches {@code NormalizedCandidatePromotionReadinessService.VOCABULARY_NOTE_TYPE}. */
    private static final String VOCABULARY_NOTE_TYPE = "JLPT MAX덱 어휘";

    private final NormalizedContentCandidateRepository candidateRepository;
    private final NormalizedCandidatePairReviewRepository reviewRepository;
    private final NormalizedCandidateMatchPairRepository pairRepository;
    private final NormalizedCandidateCanonicalGroupRepository groupRepository;
    private final NormalizedCandidateCanonicalGroupMemberRepository memberRepository;
    private final NormalizedCandidateCanonicalGroupEdgeRepository edgeRepository;
    private final ImportedSourceRecordRepository importedSourceRecordRepository;

    public NormalizedCandidateCanonicalGroupService(NormalizedContentCandidateRepository candidateRepository,
            NormalizedCandidatePairReviewRepository reviewRepository,
            NormalizedCandidateMatchPairRepository pairRepository,
            NormalizedCandidateCanonicalGroupRepository groupRepository,
            NormalizedCandidateCanonicalGroupMemberRepository memberRepository,
            NormalizedCandidateCanonicalGroupEdgeRepository edgeRepository,
            ImportedSourceRecordRepository importedSourceRecordRepository) {
        this.candidateRepository = candidateRepository;
        this.reviewRepository = reviewRepository;
        this.pairRepository = pairRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.edgeRepository = edgeRepository;
        this.importedSourceRecordRepository = importedSourceRecordRepository;
    }

    // ===================================================================================
    // Creation
    // ===================================================================================

    @Transactional
    public CanonicalGroupCreationResult create(NormalizedCandidateType candidateType,
            CanonicalGroupCreationRequest request, UserAccount decidedBy) {
        Objects.requireNonNull(decidedBy, "decidedBy is required");
        if (candidateType != NormalizedCandidateType.VOCABULARY) {
            throw new NormalizedCandidateCanonicalGroupRejectedException(
                    "Ticket 4E-3A는 VOCABULARY candidate만 canonical group으로 묶습니다 (candidateType=" + candidateType + ").");
        }

        // Step 1: request-shape validation, zero queries.
        List<ParticipantExpectation> participants = request.participants();
        if (participants == null || participants.size() < 2) {
            throw new NormalizedCandidateCanonicalGroupRejectedException(
                    "canonical group은 최소 2개의 서로 다른 candidate가 필요합니다.");
        }
        Set<Long> participantIdSet = new LinkedHashSet<>();
        for (ParticipantExpectation p : participants) {
            Objects.requireNonNull(p.candidateId(), "participant candidateId is required");
            Objects.requireNonNull(p.expectedNormalizedAt(), "participant expectedNormalizedAt is required");
            if (!participantIdSet.add(p.candidateId())) {
                throw new NormalizedCandidateCanonicalGroupRejectedException(
                        "중복된 candidate id가 제출되었습니다: " + p.candidateId());
            }
        }
        if (participantIdSet.size() < 2) {
            throw new NormalizedCandidateCanonicalGroupRejectedException(
                    "canonical group은 최소 2개의 서로 다른 candidate가 필요합니다.");
        }
        Long canonicalCandidateId =
                Objects.requireNonNull(request.canonicalCandidateId(), "canonicalCandidateId is required");
        if (!participantIdSet.contains(canonicalCandidateId)) {
            throw new NormalizedCandidateCanonicalGroupRejectedException(
                    "canonicalCandidateId는 제출된 participant 목록에 포함되어야 합니다.");
        }

        // Step 2: sort participant ids ascending.
        List<Long> sortedIds = new ArrayList<>(new TreeSet<>(participantIdSet));

        // Required C(N,2) pair-key set, in the same deterministic (lower, higher) order the sorted
        // participant list already implies.
        List<PairKey> requiredPairKeys = new ArrayList<>();
        for (int i = 0; i < sortedIds.size(); i++) {
            for (int j = i + 1; j < sortedIds.size(); j++) {
                requiredPairKeys.add(new PairKey(sortedIds.get(i), sortedIds.get(j)));
            }
        }
        Map<PairKey, Long> expectedReviewVersionByPairKey = new LinkedHashMap<>();
        List<EdgeExpectation> edges = request.edges() == null ? List.of() : request.edges();
        for (EdgeExpectation e : edges) {
            Objects.requireNonNull(e.leftCandidateId(), "edge leftCandidateId is required");
            Objects.requireNonNull(e.rightCandidateId(), "edge rightCandidateId is required");
            Objects.requireNonNull(e.expectedReviewVersion(), "edge expectedReviewVersion is required");
            PairKey key = PairKey.of(e.leftCandidateId(), e.rightCandidateId());
            if (expectedReviewVersionByPairKey.putIfAbsent(key, e.expectedReviewVersion()) != null) {
                throw new NormalizedCandidateCanonicalGroupRejectedException(
                        "중복된 edge가 제출되었습니다: " + key.lowerId() + "-" + key.higherId());
            }
        }
        if (!expectedReviewVersionByPairKey.keySet().equals(new LinkedHashSet<>(requiredPairKeys))) {
            throw new NormalizedCandidateCanonicalGroupRejectedException(
                    "제출된 edge 목록이 participant들의 C(N,2) 전체 쌍과 정확히 일치하지 않습니다 (모든 쌍에 대한 review가 "
                            + "필요하며, 참여하지 않는 candidate 쌍의 edge는 제출할 수 없습니다).");
        }

        // Step 3: lock every participating candidate, one at a time, ascending id.
        Map<Long, NormalizedContentCandidate> lockedCandidatesById = new LinkedHashMap<>();
        for (Long id : sortedIds) {
            NormalizedContentCandidate candidate = candidateRepository
                    .findByIdAndCandidateTypeForCanonicalGroupCreation(id, NormalizedCandidateType.VOCABULARY)
                    .orElseThrow(() -> new NormalizedCandidateCanonicalGroupRejectedException(
                            "candidate를 찾을 수 없거나 VOCABULARY가 아닙니다: " + id));
            lockedCandidatesById.put(id, candidate);
        }

        // Step 4: revalidate from locked state - same sourceRef, expectedNormalizedAt still matches.
        Map<Long, Instant> expectedNormalizedAtByCandidateId = new LinkedHashMap<>();
        for (ParticipantExpectation p : participants) {
            expectedNormalizedAtByCandidateId.put(p.candidateId(), p.expectedNormalizedAt());
        }
        String sourceRef = lockedCandidatesById.get(sortedIds.get(0)).getSourceRef();
        for (Long id : sortedIds) {
            NormalizedContentCandidate candidate = lockedCandidatesById.get(id);
            if (!Objects.equals(candidate.getSourceRef(), sourceRef)) {
                throw new NormalizedCandidateCanonicalGroupRejectedException(
                        "모든 candidate는 같은 sourceRef를 가져야 합니다 (candidate " + id + "의 sourceRef가 다릅니다).");
            }
            Instant expected = expectedNormalizedAtByCandidateId.get(id);
            if (!candidate.getNormalizedAt().equals(expected)) {
                throw new NormalizedCandidateCanonicalGroupStaleException(
                        "candidate " + id + "가 화면을 읽은 이후 재정규화되었습니다 (expectedNormalizedAt=" + expected
                                + ", currentNormalizedAt=" + candidate.getNormalizedAt() + ") - 새로고침 후 다시 확인해주세요.");
            }
        }

        // Steps 5-6: lock every required NormalizedCandidatePairReview row, in pair-key order.
        Map<PairKey, NormalizedCandidatePairReview> lockedReviewsByPairKey = new LinkedHashMap<>();
        for (PairKey key : requiredPairKeys) {
            NormalizedCandidatePairReview review = reviewRepository
                    .findByLeftCandidateIdAndRightCandidateIdForCanonicalGroupCreation(key.lowerId(), key.higherId())
                    .orElseThrow(() -> new NormalizedCandidateCanonicalGroupRejectedException(
                            "candidate " + key.lowerId() + "와 " + key.higherId() + " 사이의 review가 없습니다."));
            lockedReviewsByPairKey.put(key, review);
        }

        // Step 7: only now read the current Ticket 4B pair for each identity (non-locking - safe
        // because every participating candidate is already locked above, and Ticket 4B's analyzer now
        // locks those same candidates before mutating any pair row for them).
        Map<PairKey, NormalizedCandidateMatchPair> currentPairsByPairKey = new LinkedHashMap<>();
        for (PairKey key : requiredPairKeys) {
            NormalizedCandidateMatchPair pair = pairRepository
                    .findByLeftCandidateIdAndRightCandidateId(key.lowerId(), key.higherId())
                    .orElseThrow(() -> new NormalizedCandidateCanonicalGroupRejectedException(
                            "candidate " + key.lowerId() + "와 " + key.higherId() + " 사이의 현재 Ticket 4B pair가 없습니다 "
                                    + "(재분석으로 사라졌을 수 있습니다)."));
            currentPairsByPairKey.put(key, pair);
        }

        // Step 8: revalidate every edge - freshness, decision, version.
        for (PairKey key : requiredPairKeys) {
            NormalizedContentCandidate left = lockedCandidatesById.get(key.lowerId());
            NormalizedContentCandidate right = lockedCandidatesById.get(key.higherId());
            NormalizedCandidateMatchPair pair = currentPairsByPairKey.get(key);
            NormalizedCandidatePairReview review = lockedReviewsByPairKey.get(key);

            boolean pairFresh = !left.getNormalizedAt().isAfter(pair.getGeneratedAt())
                    && !right.getNormalizedAt().isAfter(pair.getGeneratedAt());
            if (!pairFresh) {
                throw new NormalizedCandidateCanonicalGroupStaleException(
                        "candidate " + key.lowerId() + "-" + key.higherId() + " pair 분석이 최신 상태가 아닙니다 (재분석 필요).");
            }
            if (review.getDecision() != HumanReviewDecision.SAME_CONTENT) {
                throw new NormalizedCandidateCanonicalGroupRejectedException(
                        "candidate " + key.lowerId() + "-" + key.higherId() + " review가 SAME_CONTENT가 아닙니다 (decision="
                                + review.getDecision() + ").");
            }
            boolean reviewFresh = pair.getAssessment() == review.getAssessmentSnapshot()
                    && left.getNormalizedAt().equals(review.getLeftNormalizedAtSnapshot())
                    && right.getNormalizedAt().equals(review.getRightNormalizedAtSnapshot());
            if (!reviewFresh) {
                throw new NormalizedCandidateCanonicalGroupStaleException(
                        "candidate " + key.lowerId() + "-" + key.higherId() + " review가 현재 pair/candidate 상태와 더 "
                                + "이상 일치하지 않습니다 (STALE) - 재검토가 필요합니다.");
            }
            Long expectedVersion = expectedReviewVersionByPairKey.get(key);
            if (review.getVersion() != expectedVersion) {
                throw new NormalizedCandidateCanonicalGroupStaleException(
                        "candidate " + key.lowerId() + "-" + key.higherId() + " review가 화면을 읽은 이후 변경되었습니다 "
                                + "(expectedReviewVersion=" + expectedVersion + ", currentVersion=" + review.getVersion()
                                + ") - 새로고침 후 다시 확인해주세요.");
            }
        }

        // Step 9: no participant already in a current canonical-group membership.
        List<NormalizedCandidateCanonicalGroupMember> existingMemberships =
                memberRepository.findByMemberCandidate_IdIn(sortedIds);
        if (!existingMemberships.isEmpty()) {
            Long conflicting = existingMemberships.get(0).getMemberCandidate().getId();
            throw new NormalizedCandidateCanonicalGroupRejectedException(
                    "candidate " + conflicting + "는 이미 다른 canonical group의 현재 멤버입니다.");
        }

        // Step 10: no participant already production-linked - unconditional, no B/E carve-outs
        // (Ticket 4E-3B's own later scope).
        List<Long> sourceNoteIds = sortedIds.stream().map(lockedCandidatesById::get)
                .map(NormalizedContentCandidate::getSourceNoteId).toList();
        boolean anyPromoted = importedSourceRecordRepository
                .findBySourceRefAndNoteTypeAndSourceNoteIdIn(sourceRef, VOCABULARY_NOTE_TYPE, sourceNoteIds).stream()
                .anyMatch(record -> record.getContentItem() != null);
        if (anyPromoted) {
            throw new NormalizedCandidateCanonicalGroupRejectedException(
                    "참여 candidate 중 이미 production ContentItem에 연결된 candidate가 있어 canonical group을 생성할 수 없습니다 "
                            + "(Ticket 4E-3B의 별도 범위).");
        }

        // Step 11: insert group + membership rows + edge rows.
        NormalizedContentCandidate canonicalCandidate = lockedCandidatesById.get(canonicalCandidateId);
        Instant now = Instant.now();
        NormalizedCandidateCanonicalGroup group =
                new NormalizedCandidateCanonicalGroup(canonicalCandidate, decidedBy, request.note(), now);
        groupRepository.save(group);

        for (Long id : sortedIds) {
            NormalizedContentCandidate candidate = lockedCandidatesById.get(id);
            memberRepository.save(
                    new NormalizedCandidateCanonicalGroupMember(group, candidate, candidate.getNormalizedAt()));
        }

        for (PairKey key : requiredPairKeys) {
            NormalizedContentCandidate left = lockedCandidatesById.get(key.lowerId());
            NormalizedContentCandidate right = lockedCandidatesById.get(key.higherId());
            NormalizedCandidatePairReview review = lockedReviewsByPairKey.get(key);
            NormalizedCandidateMatchPair pair = currentPairsByPairKey.get(key);
            edgeRepository.save(new NormalizedCandidateCanonicalGroupEdge(group, left, right, review,
                    review.getVersion(), review.getReviewedAt(), review.getReviewer(), review.getDecision(),
                    pair.getAssessment(), left.getNormalizedAt(), right.getNormalizedAt()));
        }

        // Step 12: commit (implicit) or roll back the whole transaction on any failure above.
        return new CanonicalGroupCreationResult(group.getId());
    }

    // ===================================================================================
    // Dissolution
    // ===================================================================================

    @Transactional
    public void dissolve(Long groupId, long expectedVersion, String dissolutionNote, UserAccount dissolvedBy) {
        Objects.requireNonNull(dissolvedBy, "dissolvedBy is required");
        NormalizedCandidateCanonicalGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("canonical group을 찾을 수 없습니다: " + groupId));
        if (group.getStatus() != NormalizedCandidateCanonicalGroupStatus.ACTIVE) {
            throw new NormalizedCandidateCanonicalGroupRejectedException(
                    "ACTIVE 상태의 group만 해체할 수 있습니다 (현재 status=" + group.getStatus() + ").");
        }
        if (group.getVersion() != expectedVersion) {
            throw new NormalizedCandidateCanonicalGroupStaleException(
                    "group이 화면을 읽은 이후 변경되었습니다 (expectedVersion=" + expectedVersion + ", currentVersion="
                            + group.getVersion() + ") - 새로고침 후 다시 확인해주세요.");
        }
        group.dissolve(dissolvedBy, Instant.now(), dissolutionNote);
        groupRepository.save(group);
        memberRepository.deleteByGroup_Id(groupId);
    }

    // ===================================================================================
    // Read model
    // ===================================================================================

    @Transactional(readOnly = true)
    public CanonicalGroupListResult list(NormalizedCandidateCanonicalGroupStatus statusFilter) {
        List<CanonicalGroupListRow> rows = groupRepository.findAllForList(statusFilter).stream()
                .map(g -> new CanonicalGroupListRow(g.getId(), g.getStatus(), g.getCanonicalCandidate().getId(),
                        shortPreview(g.getCanonicalCandidate()), g.getCanonicalCandidate().getSourceRef(),
                        memberRepository.findByGroup_IdOrderByMemberCandidate_Id(g.getId()).size(),
                        g.getDecidedBy().getDisplayName(), g.getDecidedAt()))
                .toList();
        return new CanonicalGroupListResult(rows);
    }

    @Transactional(readOnly = true)
    public CanonicalGroupDetailView detail(Long groupId) {
        NormalizedCandidateCanonicalGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("canonical group을 찾을 수 없습니다: " + groupId));
        List<NormalizedCandidateCanonicalGroupMember> members =
                memberRepository.findByGroup_IdOrderByMemberCandidate_Id(groupId);
        List<CanonicalGroupMemberView> memberViews = members.stream()
                .map(m -> new CanonicalGroupMemberView(m.getMemberCandidate().getId(),
                        shortPreview(m.getMemberCandidate()),
                        m.getMemberCandidate().getId().equals(group.getCanonicalCandidate().getId()),
                        m.getMemberNormalizedAtSnapshot()))
                .toList();
        List<CanonicalGroupEdgeView> edgeViews = edgeRepository.findByGroup_IdOrderByIdAsc(groupId).stream()
                .map(e -> new CanonicalGroupEdgeView(e.getLeftCandidate().getId(), e.getRightCandidate().getId(),
                        e.getPairReviewVersionSnapshot(), e.getPairReviewedAtSnapshot(),
                        e.getReviewerSnapshot().getDisplayName(), e.getReviewDecisionSnapshot().name(),
                        e.getAssessmentSnapshot().name(), e.getLeftNormalizedAtSnapshot(),
                        e.getRightNormalizedAtSnapshot()))
                .toList();
        return new CanonicalGroupDetailView(group.getId(), group.getStatus(), group.getCanonicalCandidate().getId(),
                group.getDecidedBy().getDisplayName(), group.getDecidedAt(), group.getNote(),
                group.getDissolvedBy() == null ? null : group.getDissolvedBy().getDisplayName(),
                group.getDissolvedAt(), group.getDissolutionNote(), group.getVersion(), memberViews, edgeViews);
    }

    private String shortPreview(NormalizedContentCandidate candidate) {
        NormalizedVocabularyCandidateDetail vocab = candidate.getVocabularyDetail();
        if (vocab != null) {
            return joinNonBlank(vocab.getExpression(), vocab.getReading());
        }
        NormalizedGrammarCandidateDetail grammar = candidate.getGrammarDetail();
        return grammar == null ? null : grammar.getPattern();
    }

    private static String joinNonBlank(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a + " (" + b + ")";
    }

    private record PairKey(Long lowerId, Long higherId) {
        static PairKey of(Long a, Long b) {
            return a < b ? new PairKey(a, b) : new PairKey(b, a);
        }
    }
}
