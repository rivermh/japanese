package com.japanese.content.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.dto.NormalizedCandidateGroupPromotionModels.GroupPromotionEligibilityView;
import com.japanese.content.dto.NormalizedCandidateGroupPromotionModels.GroupPromotionResult;
import com.japanese.content.dto.PromotionReadinessModels.PromotionReadinessDetailView;
import com.japanese.content.dto.PromotionReadinessModels.PromotionReadinessIssue;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Example;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroup;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupEdge;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupMember;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupStatus;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.NormalizedVocabularyCandidateDetail;
import com.japanese.content.entity.NormalizedVocabularyCandidateExample;
import com.japanese.content.entity.NormalizedVocabularyCandidateMeaning;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.NormalizedCandidateCanonicalGroupEdgeRepository;
import com.japanese.content.repository.NormalizedCandidateCanonicalGroupMemberRepository;
import com.japanese.content.repository.NormalizedCandidateCanonicalGroupRepository;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import com.japanese.content.service.PrivateApkgNoteProvenanceReader.RawProvenance;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4E-3B: the smallest safe GROUP-AWARE production write path that promotes exactly one
 * {@code ACTIVE}, fresh {@link NormalizedCandidateCanonicalGroup} into a production draft - ONE
 * {@code ContentItem}/{@code Word}, sourced exclusively from the group's canonical candidate, with
 * EVERY member candidate (canonical included) converging its own truthful {@code ImportedSourceRecord}
 * onto that single {@code ContentItem}.
 *
 * <p><b>Absolute boundary</b>: no member candidate's normalized fields other than the canonical's are
 * ever read into {@code Word}/{@code Meaning}/{@code Example}/slug/{@code Level} selection - see
 * {@link #buildProductionContent}. No text is merged, no "better value" is chosen, no field is unioned.
 * Grammar is rejected before any query. Rights are never mutated. Nothing here publishes or changes
 * {@code ReviewStatus} beyond the entity's own default initial state.
 *
 * <p><b>Readiness architecture</b>: ordinary {@link NormalizedCandidatePromotionReadinessService} is
 * never modified and never weakened - a candidate that is a group member remains just as blocked from
 * ordinary {@link NormalizedVocabularyCandidatePromotionService#promote} as before this class exists.
 * This class instead computes its OWN group-aware eligibility per member by calling the SAME
 * authoritative {@code readiness.detail(...)} and then interpreting its result differently depending on
 * the member's role - see {@link #requireGroupAwareReadiness} for the exact rule, which reuses (never
 * duplicates) {@link NormalizedCandidatePromotionReadinessService#isMappingIssue} - the same
 * "content-mapping vs. everything else" classification that service's own {@code MappingStatus} already
 * exposes:
 * <ul>
 *   <li><b>Canonical candidate</b>: every ordinary readiness issue must be absent, EXCEPT
 *   {@code SAME_CONTENT_CANONICAL_SELECTION_REQUIRED} - and only when every contributing pair (per
 *   {@link PromotionReadinessDetailView#pairs()}'s own per-pair {@code issuesFromThisPair}) has its
 *   partner candidate inside THIS group's own membership. An extra, out-of-group SAME_CONTENT
 *   relationship is a distinct, unresolved human decision this group promotion must not silently
 *   absorb - it still blocks.</li>
 *   <li><b>Non-canonical member</b>: the same rule, PLUS exempt from every content-mapping issue
 *   ({@code VOCAB_*}/{@code GRAMMAR_*}/{@code JLPT_LEVEL_UNMAPPABLE}/meaning-language) - its own
 *   {@code expression}/{@code reading}/{@code meanings}/{@code Level} are never written to production,
 *   so a mapping problem in those unused fields must not block group promotion. Every other axis
 *   (normalization quality, source rights, production identity policy, already-promoted) still blocks
 *   it exactly like any other candidate.</li>
 * </ul>
 *
 * <p><b>Freshness model</b> (JLPT-MAX Ticket 4E-3B design decision, deliberately different from Ticket
 * 4E-3A's own admin-submitted freshness tokens): the admin submission carries only the group's own
 * {@code expectedGroupVersion} - proving "this is the same GROUP DECISION I saw" (the only thing that
 * can change about an ACTIVE group is its status, guarded by {@code @Version}). The reference values for
 * "has the underlying source data drifted since this group was APPROVED" are never re-submitted by the
 * admin - they already exist durably on the group's own {@link NormalizedCandidateCanonicalGroupMember}/
 * {@link NormalizedCandidateCanonicalGroupEdge} rows (their own immutable snapshots), so this service
 * compares LIVE state directly against those, inside the transaction, after locking. This is a strictly
 * stronger check than re-deriving "what the admin's GET page happened to show" would be, since a group
 * can sit ACTIVE for a long time without being dissolved while the evidence underneath it silently goes
 * stale (Ticket 4E-3A's own "ACTIVE is a lifecycle state, not a freshness guarantee" - see that ticket's
 * schema v3 report) - re-validating against the ORIGINAL APPROVAL, not merely "what render happened to
 * show this time", is what actually protects against that.
 *
 * <p><b>Transaction/lock order</b> (JLPT-MAX Ticket 4E-3B, extending Ticket 4E-3A's own approved
 * hierarchy by one new resource class at the front): GROUP ROW -&gt; CANDIDATES (ascending id) -&gt;
 * PAIR REVIEWS (deterministic pair-key order) -&gt; PROVENANCE ROWS (ascending member-candidate-id order)
 * -&gt; production write. The group-row lock is acquired FIRST and is what safely serializes this
 * transaction against a concurrent {@code NormalizedCandidateCanonicalGroupService#dissolve} call on the
 * SAME group: dissolve's own status-flip is a {@code save(group)} that needs an exclusive lock on this
 * same row to actually persist; under Hibernate's default auto-flush behavor, that pending UPDATE is
 * flushed (and therefore needs to acquire the row lock) before dissolve's own subsequent membership-row
 * bulk {@code DELETE} statement can execute - so a concurrent dissolve either fully commits before this
 * transaction acquires the group lock (observed as {@code status != ACTIVE}, cleanly rejected) or fully
 * blocks on this transaction's lock until it commits/rolls back (never a partial/interleaved dissolve).
 * Ticket 4B match-pair rows are, as in Ticket 4E-3A, deliberately never separately locked - candidate-
 * level locking (shared with {@code NormalizedCandidateConflictAnalyzer.analyze}'s own Ticket 4E-3A
 * hardening) is what stabilizes them.
 */
@Service
public class NormalizedCandidateGroupPromotionService {

    /** Matches {@code NormalizedCandidatePromotionReadinessService.VOCABULARY_NOTE_TYPE}. */
    private static final String VOCABULARY_NOTE_TYPE = "JLPT MAX덱 어휘";
    private static final String JLPT_LEVEL_SYSTEM = "JLPT";

    private final NormalizedCandidateCanonicalGroupRepository groupRepository;
    private final NormalizedCandidateCanonicalGroupMemberRepository memberRepository;
    private final NormalizedCandidateCanonicalGroupEdgeRepository edgeRepository;
    private final NormalizedContentCandidateRepository candidateRepository;
    private final NormalizedCandidatePairReviewRepository reviewRepository;
    private final NormalizedCandidateMatchPairRepository pairRepository;
    private final ImportedSourceRecordRepository importedSourceRecordRepository;
    private final ContentItemRepository contentItemRepository;
    private final LevelRepository levelRepository;
    private final NormalizedCandidatePromotionReadinessService readiness;
    private final ProductionContentSlugPolicy slugPolicy;
    private final NormalizedVocabularyMeaningLanguagePolicy meaningLanguagePolicy;
    private final PrivateApkgNoteProvenanceReader provenanceReader;

    public NormalizedCandidateGroupPromotionService(NormalizedCandidateCanonicalGroupRepository groupRepository,
            NormalizedCandidateCanonicalGroupMemberRepository memberRepository,
            NormalizedCandidateCanonicalGroupEdgeRepository edgeRepository,
            NormalizedContentCandidateRepository candidateRepository,
            NormalizedCandidatePairReviewRepository reviewRepository,
            NormalizedCandidateMatchPairRepository pairRepository,
            ImportedSourceRecordRepository importedSourceRecordRepository,
            ContentItemRepository contentItemRepository, LevelRepository levelRepository,
            NormalizedCandidatePromotionReadinessService readiness, ProductionContentSlugPolicy slugPolicy,
            NormalizedVocabularyMeaningLanguagePolicy meaningLanguagePolicy,
            PrivateApkgNoteProvenanceReader provenanceReader) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.edgeRepository = edgeRepository;
        this.candidateRepository = candidateRepository;
        this.reviewRepository = reviewRepository;
        this.pairRepository = pairRepository;
        this.importedSourceRecordRepository = importedSourceRecordRepository;
        this.contentItemRepository = contentItemRepository;
        this.levelRepository = levelRepository;
        this.readiness = readiness;
        this.slugPolicy = slugPolicy;
        this.meaningLanguagePolicy = meaningLanguagePolicy;
        this.provenanceReader = provenanceReader;
    }

    // ===================================================================================
    // Promotion
    // ===================================================================================

    @Transactional
    public GroupPromotionResult promote(Long groupId, long expectedGroupVersion, UserAccount promotedBy) {
        Objects.requireNonNull(promotedBy, "promotedBy is required");

        // Step 1: lock the group row first - see class javadoc for why this fixed ordering matters.
        NormalizedCandidateCanonicalGroup group = groupRepository.findByIdForGroupPromotion(groupId)
                .orElseThrow(() -> new NoSuchElementException("canonical group을 찾을 수 없습니다: " + groupId));

        if (group.getStatus() != NormalizedCandidateCanonicalGroupStatus.ACTIVE) {
            throw new NormalizedCandidateGroupPromotionRejectedException(
                    "ACTIVE 상태의 group만 승격할 수 있습니다 (현재 status=" + group.getStatus() + ").");
        }
        if (group.getVersion() != expectedGroupVersion) {
            throw new NormalizedCandidateGroupPromotionStaleException(
                    "group이 화면을 읽은 이후 변경되었습니다 (expectedGroupVersion=" + expectedGroupVersion
                            + ", currentVersion=" + group.getVersion() + ") - 새로고침 후 다시 확인해주세요.");
        }
        NormalizedCandidateType candidateType = group.getCanonicalCandidate().getCandidateType();
        if (candidateType != NormalizedCandidateType.VOCABULARY) {
            throw new NormalizedCandidateGroupPromotionRejectedException(
                    "Ticket 4E-3B는 VOCABULARY group만 승격합니다 (candidateType=" + candidateType + ").");
        }

        // Step 2: determine stable member candidate ids (plain read - safe, see class javadoc).
        List<NormalizedCandidateCanonicalGroupMember> members = memberRepository
                .findByGroup_IdOrderByMemberCandidate_Id(groupId);
        if (members.size() < 2) {
            throw new NormalizedCandidateGroupPromotionRejectedException(
                    "group에 유효한 멤버가 2개 미만입니다 (malformed group state).");
        }
        List<Long> sortedIds = members.stream().map(m -> m.getMemberCandidate().getId()).sorted().toList();
        Set<Long> memberIdSet = new HashSet<>(sortedIds);
        Long canonicalId = group.getCanonicalCandidate().getId();
        if (!memberIdSet.contains(canonicalId)) {
            throw new NormalizedCandidateGroupPromotionRejectedException(
                    "canonical candidate가 현재 group membership에 없습니다 (malformed group state).");
        }
        Map<Long, Instant> approvedNormalizedAtByCandidateId = new LinkedHashMap<>();
        for (NormalizedCandidateCanonicalGroupMember m : members) {
            approvedNormalizedAtByCandidateId.put(m.getMemberCandidate().getId(), m.getMemberNormalizedAtSnapshot());
        }

        // Step 3: lock every member candidate, one at a time, ascending id.
        Map<Long, NormalizedContentCandidate> lockedCandidatesById = new LinkedHashMap<>();
        for (Long id : sortedIds) {
            NormalizedContentCandidate candidate = candidateRepository
                    .findByIdAndCandidateTypeForGroupPromotion(id, NormalizedCandidateType.VOCABULARY)
                    .orElseThrow(() -> new NormalizedCandidateGroupPromotionRejectedException(
                            "candidate를 찾을 수 없거나 VOCABULARY가 아닙니다: " + id));
            lockedCandidatesById.put(id, candidate);
        }

        // Step 4: revalidate each member's normalizedAt against the value APPROVED at group creation
        // time (not a caller-submitted value - see class javadoc "Freshness model"). Also defensively
        // re-verify every member shares the canonical candidate's sourceRef: Ticket 4E-3A's own
        // create() already enforces this at group-creation time (and NormalizedContentCandidate.sourceRef
        // has no setter, so it can never drift afterward), but this write path must never rely on that
        // guarantee alone - see this ticket's own independent-review instruction not to trust "the other
        // service would never create bad state." A mismatch here can only mean malformed persisted state.
        String canonicalSourceRef = lockedCandidatesById.get(canonicalId).getSourceRef();
        for (Long id : sortedIds) {
            NormalizedContentCandidate candidate = lockedCandidatesById.get(id);
            if (!candidate.getSourceRef().equals(canonicalSourceRef)) {
                throw new NormalizedCandidateGroupPromotionRejectedException(
                        "candidate " + id + "의 sourceRef가 canonical candidate와 다릅니다 (malformed group state).");
            }
            Instant approved = approvedNormalizedAtByCandidateId.get(id);
            if (!candidate.getNormalizedAt().equals(approved)) {
                throw new NormalizedCandidateGroupPromotionStaleException(
                        "candidate " + id + "가 group 승인 이후 재정규화되었습니다 (approvedNormalizedAt=" + approved
                                + ", currentNormalizedAt=" + candidate.getNormalizedAt() + ") - group 재검토가 필요합니다.");
            }
        }

        // Step 5: load historical edges and build the required C(N,2) pair-key set from the sorted
        // member list - defensive sanity checks against a malformed group (should be unreachable given
        // Ticket 4E-3A's own invariants, but never silently trusted).
        List<NormalizedCandidateCanonicalGroupEdge> historicalEdges = edgeRepository.findByGroup_IdOrderByIdAsc(groupId);
        List<PairKey> requiredPairKeys = new ArrayList<>();
        for (int i = 0; i < sortedIds.size(); i++) {
            for (int j = i + 1; j < sortedIds.size(); j++) {
                requiredPairKeys.add(new PairKey(sortedIds.get(i), sortedIds.get(j)));
            }
        }
        Map<PairKey, NormalizedCandidateCanonicalGroupEdge> edgeByPairKey = new LinkedHashMap<>();
        for (NormalizedCandidateCanonicalGroupEdge edge : historicalEdges) {
            edgeByPairKey.put(new PairKey(edge.getLeftCandidate().getId(), edge.getRightCandidate().getId()), edge);
        }
        if (historicalEdges.size() != requiredPairKeys.size() || !edgeByPairKey.keySet().containsAll(requiredPairKeys)) {
            throw new NormalizedCandidateGroupPromotionRejectedException(
                    "group의 historical edge 집합이 현재 membership과 일치하지 않습니다 (malformed group state).");
        }

        // Step 6: lock every required review row, in that same deterministic pair-key order.
        Map<PairKey, NormalizedCandidatePairReview> lockedReviewsByPairKey = new LinkedHashMap<>();
        for (PairKey key : requiredPairKeys) {
            NormalizedCandidatePairReview review = reviewRepository
                    .findByLeftCandidateIdAndRightCandidateIdForGroupPromotion(key.lowerId(), key.higherId())
                    .orElseThrow(() -> new NormalizedCandidateGroupPromotionStaleException(
                            "candidate " + key.lowerId() + "와 " + key.higherId() + " 사이의 review가 더 이상 존재하지 않습니다."));
            lockedReviewsByPairKey.put(key, review);
        }

        // Step 7: only now read the current Ticket 4B pair for each identity (non-locking - safe
        // because every member candidate is already locked, and Ticket 4B's analyzer locks those same
        // candidates before mutating any pair row for them - see Ticket 4E-3A's own hardening).
        Map<PairKey, NormalizedCandidateMatchPair> currentPairsByPairKey = new LinkedHashMap<>();
        for (PairKey key : requiredPairKeys) {
            NormalizedCandidateMatchPair pair = pairRepository
                    .findByLeftCandidateIdAndRightCandidateId(key.lowerId(), key.higherId())
                    .orElseThrow(() -> new NormalizedCandidateGroupPromotionStaleException(
                            "candidate " + key.lowerId() + "와 " + key.higherId() + " 사이의 현재 Ticket 4B pair가 "
                                    + "사라졌습니다 (재분석으로 사라졌을 수 있습니다) - group 재검토가 필요합니다."));
            currentPairsByPairKey.put(key, pair);
        }

        // Step 8: revalidate every edge against BOTH current live state AND the edge's own immutable
        // approval snapshot.
        for (PairKey key : requiredPairKeys) {
            NormalizedContentCandidate left = lockedCandidatesById.get(key.lowerId());
            NormalizedContentCandidate right = lockedCandidatesById.get(key.higherId());
            NormalizedCandidateMatchPair pair = currentPairsByPairKey.get(key);
            NormalizedCandidatePairReview review = lockedReviewsByPairKey.get(key);
            NormalizedCandidateCanonicalGroupEdge edge = edgeByPairKey.get(key);

            boolean pairFresh = !left.getNormalizedAt().isAfter(pair.getGeneratedAt())
                    && !right.getNormalizedAt().isAfter(pair.getGeneratedAt());
            if (!pairFresh) {
                throw new NormalizedCandidateGroupPromotionStaleException(
                        "candidate " + key.lowerId() + "-" + key.higherId() + " pair 분석이 최신 상태가 아닙니다 - group "
                                + "재검토가 필요합니다.");
            }
            if (review.getDecision() != HumanReviewDecision.SAME_CONTENT) {
                throw new NormalizedCandidateGroupPromotionStaleException(
                        "candidate " + key.lowerId() + "-" + key.higherId() + " review가 더 이상 SAME_CONTENT가 아닙니다 "
                                + "(decision=" + review.getDecision() + ") - group 재검토가 필요합니다.");
            }
            boolean reviewFresh = pair.getAssessment() == review.getAssessmentSnapshot()
                    && left.getNormalizedAt().equals(review.getLeftNormalizedAtSnapshot())
                    && right.getNormalizedAt().equals(review.getRightNormalizedAtSnapshot());
            if (!reviewFresh) {
                throw new NormalizedCandidateGroupPromotionStaleException(
                        "candidate " + key.lowerId() + "-" + key.higherId() + " review가 현재 pair/candidate 상태와 "
                                + "일치하지 않습니다 - group 재검토가 필요합니다.");
            }
            if (review.getVersion() != edge.getPairReviewVersionSnapshot()) {
                throw new NormalizedCandidateGroupPromotionStaleException(
                        "candidate " + key.lowerId() + "-" + key.higherId() + " review가 group 승인 이후 재검토되었습니다 "
                                + "(approvedVersion=" + edge.getPairReviewVersionSnapshot() + ", currentVersion="
                                + review.getVersion() + ") - group 재검토가 필요합니다.");
            }
        }

        // Step 9: provenance - lock any existing record per member, in ascending member-id order;
        // reject the WHOLE promotion if ANY member is already linked (no exceptions - Ticket 4E-3B v1
        // policy, see class javadoc); gather genuine raw provenance for members with no existing row.
        // Deliberately runs BEFORE the group-aware readiness check below: an already-linked member must
        // always surface this specific, unconditional rejection message, never the more generic
        // ALREADY_PROMOTED readiness-issue wording (which the ordinary NormalizedCandidatePromotionReadinessService
        // also happens to flag for the same underlying condition).
        String sourceRef = lockedCandidatesById.get(canonicalId).getSourceRef();
        Map<Long, ImportedSourceRecord> existingRecordsByCandidateId = new LinkedHashMap<>();
        Map<Long, RawProvenance> rawProvenanceByCandidateId = new LinkedHashMap<>();
        for (Long id : sortedIds) {
            NormalizedContentCandidate candidate = lockedCandidatesById.get(id);
            Optional<ImportedSourceRecord> existing = importedSourceRecordRepository
                    .findBySourceRefAndNoteTypeAndSourceNoteIdForGroupPromotion(sourceRef, VOCABULARY_NOTE_TYPE,
                            candidate.getSourceNoteId());
            if (existing.isPresent()) {
                if (existing.get().getContentItem() != null) {
                    throw new NormalizedCandidateGroupPromotionRejectedException(
                            "candidate " + id + "는 이미 production ContentItem에 연결되어 있어 group promotion을 진행할 수 "
                                    + "없습니다 (예외 없이 전체 group을 차단합니다).");
                }
                existingRecordsByCandidateId.put(id, existing.get());
            } else {
                RawProvenance raw = provenanceReader.find(sourceRef, candidate.getSourceNoteId())
                        .orElseThrow(() -> new NormalizedCandidateGroupPromotionRejectedException(
                                "candidate " + id + "의 원본 provenance(raw Anki 데이터)를 private_apkg_notes에서 찾을 "
                                        + "수 없습니다 - 정규화된 필드로 대체하지 않고 전체 group promotion을 차단합니다."));
                rawProvenanceByCandidateId.put(id, raw);
            }
        }

        // Step 10: group-aware readiness per member - see class javadoc for the exact rule.
        for (Long id : sortedIds) {
            requireGroupAwareReadiness(candidateType, id, id.equals(canonicalId), memberIdSet);
        }

        // Step 11: resolve Level/languageTag from the CANONICAL candidate only (already confirmed
        // resolvable by readiness above - genuinely missing/duplicate here is a defensive fallback that
        // should be unreachable).
        NormalizedContentCandidate canonical = lockedCandidatesById.get(canonicalId);
        NormalizedVocabularyCandidateDetail canonicalVocab = canonical.getVocabularyDetail();
        Level level = levelRepository.findBySystemAndCode(JLPT_LEVEL_SYSTEM, canonicalVocab.getLevelCode())
                .orElseThrow(() -> new NormalizedCandidateGroupPromotionRejectedException(
                        "production Level을 찾을 수 없습니다 (system=JLPT, code=" + canonicalVocab.getLevelCode() + ")."));
        String languageTag = meaningLanguagePolicy.resolveLanguageTag(sourceRef)
                .orElseThrow(() -> new NormalizedCandidateGroupPromotionRejectedException(
                        "meaning language policy를 해석할 수 없습니다 (sourceRef=" + sourceRef + ")."));

        // Step 12: generate the slug exactly once, only now that every rejection check has passed.
        String slug = slugPolicy.generateSlug(NormalizedCandidateType.VOCABULARY);

        // Step 13: build production content from the CANONICAL candidate only.
        ContentItem savedItem = buildProductionContent(canonicalVocab, sourceRef, slug, level, languageTag);

        // Step 14: link (existing, untouched otherwise) or create (genuine raw provenance) every
        // member's own ImportedSourceRecord, converging all of them onto the ONE new ContentItem.
        for (Long id : sortedIds) {
            NormalizedContentCandidate candidate = lockedCandidatesById.get(id);
            ImportedSourceRecord record = existingRecordsByCandidateId.get(id);
            if (record == null) {
                RawProvenance raw = rawProvenanceByCandidateId.get(id);
                String memberLevelCode = candidate.getVocabularyDetail() == null
                        ? null : candidate.getVocabularyDetail().getLevelCode();
                record = new ImportedSourceRecord(sourceRef, VOCABULARY_NOTE_TYPE, candidate.getSourceNoteId(),
                        memberLevelCode, raw.tags(), String.join("", raw.fieldNames()),
                        String.join("", raw.fieldValues()));
            }
            record.linkContentItem(savedItem);
            importedSourceRecordRepository.save(record);
        }

        // Step 15: commit (implicit) or roll back the whole transaction on any failure above/below.
        return new GroupPromotionResult(savedItem.getId(), slug);
    }

    private ContentItem buildProductionContent(NormalizedVocabularyCandidateDetail canonicalVocab, String sourceRef,
            String slug, Level level, String languageTag) {
        Word word = new Word(canonicalVocab.getExpression(), canonicalVocab.getReading(),
                canonicalVocab.getPartOfSpeech(), NormalizedCandidatePromotionReadinessService.pitchAccentPreview(canonicalVocab));

        List<NormalizedVocabularyCandidateMeaning> meanings = canonicalVocab.getCandidate().getVocabularyMeanings();
        Map<String, Meaning> meaningByText = new LinkedHashMap<>();
        for (NormalizedVocabularyCandidateMeaning meaning : meanings) {
            Meaning produced = new Meaning(languageTag, meaning.getMeaningText(), meaning.getSenseOrder());
            word.addMeaning(produced);
            meaningByText.putIfAbsent(meaning.getMeaningText(), produced);
        }

        ContentItem item = new ContentItem(slug, ContentType.WORD, sourceRef, false);
        item.attachWord(word);
        item.addLevel(level);
        for (NormalizedVocabularyCandidateExample example : canonicalVocab.getCandidate().getVocabularyExamples()) {
            Example produced = new Example(example.getJapaneseText(), example.getReading(), example.getTranslation(),
                    example.getDisplayOrder());
            Meaning matched = meaningByText.get(example.getMeaningLabel());
            if (matched != null) {
                produced.setMeaning(matched);
            }
            item.addExample(produced);
        }
        return contentItemRepository.save(item);
    }

    /**
     * See class javadoc "Readiness architecture" for the full rule. Throws
     * {@link NormalizedCandidateGroupPromotionRejectedException} listing every issue code that still
     * blocks this member after the group-aware exemptions are applied.
     */
    private void requireGroupAwareReadiness(NormalizedCandidateType candidateType, Long candidateId,
            boolean isCanonical, Set<Long> memberIds) {
        PromotionReadinessDetailView detail = readiness.detail(candidateType, candidateId);
        List<PromotionReadinessIssueCode> blockers = new ArrayList<>();
        for (PromotionReadinessIssue issue : detail.result().issues()) {
            if (issue.code() == PromotionReadinessIssueCode.SAME_CONTENT_CANONICAL_SELECTION_REQUIRED) {
                boolean fullyExplainedByThisGroup = detail.pairs().stream()
                        .filter(p -> p.issuesFromThisPair().contains(PromotionReadinessIssueCode.SAME_CONTENT_CANONICAL_SELECTION_REQUIRED))
                        .allMatch(p -> memberIds.contains(p.partnerCandidateId()));
                if (fullyExplainedByThisGroup) {
                    continue;
                }
            } else if (!isCanonical && NormalizedCandidatePromotionReadinessService.isMappingIssue(issue.code())) {
                continue;
            }
            blockers.add(issue.code());
        }
        if (!blockers.isEmpty()) {
            throw new NormalizedCandidateGroupPromotionRejectedException(
                    (isCanonical ? "canonical" : "non-canonical") + " candidate " + candidateId
                            + "가 group promotion 준비 상태가 아닙니다 (issues=" + blockers + ").");
        }
    }

    // ===================================================================================
    // Read-only eligibility preview (admin detail page only - never trusted by promote() itself)
    // ===================================================================================

    @Transactional(readOnly = true)
    public GroupPromotionEligibilityView previewEligibility(Long groupId) {
        NormalizedCandidateCanonicalGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("canonical group을 찾을 수 없습니다: " + groupId));
        List<String> reasons = new ArrayList<>();
        if (group.getStatus() != NormalizedCandidateCanonicalGroupStatus.ACTIVE) {
            reasons.add("group이 ACTIVE 상태가 아닙니다 (status=" + group.getStatus() + ").");
            return new GroupPromotionEligibilityView(false, reasons);
        }
        NormalizedCandidateType candidateType = group.getCanonicalCandidate().getCandidateType();
        if (candidateType != NormalizedCandidateType.VOCABULARY) {
            reasons.add("VOCABULARY group만 승격할 수 있습니다.");
            return new GroupPromotionEligibilityView(false, reasons);
        }
        List<NormalizedCandidateCanonicalGroupMember> members = memberRepository
                .findByGroup_IdOrderByMemberCandidate_Id(groupId);
        Set<Long> memberIds = new HashSet<>();
        for (NormalizedCandidateCanonicalGroupMember m : members) {
            memberIds.add(m.getMemberCandidate().getId());
        }
        Long canonicalId = group.getCanonicalCandidate().getId();
        for (NormalizedCandidateCanonicalGroupMember m : members) {
            Long id = m.getMemberCandidate().getId();
            boolean isCanonical = id.equals(canonicalId);
            try {
                requireGroupAwareReadiness(candidateType, id, isCanonical, memberIds);
            } catch (NormalizedCandidateGroupPromotionRejectedException e) {
                reasons.add(e.getMessage());
            }
        }
        return new GroupPromotionEligibilityView(reasons.isEmpty(), reasons);
    }

    private record PairKey(Long lowerId, Long higherId) {
    }
}
