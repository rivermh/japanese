package com.japanese.content.service;

import static com.japanese.content.service.PromotionReadinessIssueCode.ALREADY_PROMOTED;
import static com.japanese.content.service.PromotionReadinessIssueCode.GRAMMAR_CONNECTION_TOO_LONG;
import static com.japanese.content.service.PromotionReadinessIssueCode.GRAMMAR_MAPPING_POLICY_UNRESOLVED;
import static com.japanese.content.service.PromotionReadinessIssueCode.GRAMMAR_PATTERN_MISSING;
import static com.japanese.content.service.PromotionReadinessIssueCode.GRAMMAR_PATTERN_TOO_LONG;
import static com.japanese.content.service.PromotionReadinessIssueCode.JLPT_LEVEL_UNMAPPABLE;
import static com.japanese.content.service.PromotionReadinessIssueCode.NORMALIZATION_FATAL;
import static com.japanese.content.service.PromotionReadinessIssueCode.NORMALIZATION_REVIEW_REQUIRED;
import static com.japanese.content.service.PromotionReadinessIssueCode.PAIR_ANALYSIS_STALE;
import static com.japanese.content.service.PromotionReadinessIssueCode.PAIR_NEEDS_FOLLOWUP;
import static com.japanese.content.service.PromotionReadinessIssueCode.PAIR_REVIEW_STALE;
import static com.japanese.content.service.PromotionReadinessIssueCode.PAIR_UNREVIEWED;
import static com.japanese.content.service.PromotionReadinessIssueCode.PRODUCTION_IDENTITY_POLICY_UNRESOLVED;
import static com.japanese.content.service.PromotionReadinessIssueCode.SAME_CONTENT_CANONICAL_SELECTION_REQUIRED;
import static com.japanese.content.service.PromotionReadinessIssueCode.SOURCE_NOT_REGISTERED;
import static com.japanese.content.service.PromotionReadinessIssueCode.SOURCE_RIGHTS_MANUAL_REVIEW;
import static com.japanese.content.service.PromotionReadinessIssueCode.SOURCE_RIGHTS_NOT_ALLOWED;
import static com.japanese.content.service.PromotionReadinessIssueCode.VOCAB_EXAMPLE_TEXT_TOO_LONG;
import static com.japanese.content.service.PromotionReadinessIssueCode.VOCAB_EXPRESSION_MISSING;
import static com.japanese.content.service.PromotionReadinessIssueCode.VOCAB_EXPRESSION_TOO_LONG;
import static com.japanese.content.service.PromotionReadinessIssueCode.VOCAB_MEANING_MISSING;
import static com.japanese.content.service.PromotionReadinessIssueCode.VOCAB_MEANING_TOO_LONG;
import static com.japanese.content.service.PromotionReadinessIssueCode.VOCAB_PART_OF_SPEECH_TOO_LONG;
import static com.japanese.content.service.PromotionReadinessIssueCode.VOCAB_PITCH_ACCENT_TOO_LONG;
import static com.japanese.content.service.PromotionReadinessIssueCode.VOCAB_READING_MISSING;
import static com.japanese.content.service.PromotionReadinessIssueCode.VOCAB_READING_TOO_LONG;

import com.japanese.content.dto.NormalizedCandidatePairReviewModels.CandidateFieldsView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.ConfusablePatternView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.PairFreshness;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.ReviewFreshness;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.VocabExampleView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.WarningView;
import com.japanese.content.dto.PromotionReadinessModels.ConfusablePatternPreview;
import com.japanese.content.dto.PromotionReadinessModels.ExistingProductionLinkStatus;
import com.japanese.content.dto.PromotionReadinessModels.GrammarMappingPreview;
import com.japanese.content.dto.PromotionReadinessModels.MappingPreview;
import com.japanese.content.dto.PromotionReadinessModels.MappingStatus;
import com.japanese.content.dto.PromotionReadinessModels.OverallStatus;
import com.japanese.content.dto.PromotionReadinessModels.PairResolutionStatus;
import com.japanese.content.dto.PromotionReadinessModels.ProductionIdentityStatus;
import com.japanese.content.dto.PromotionReadinessModels.PromotionReadinessDetailView;
import com.japanese.content.dto.PromotionReadinessModels.PromotionReadinessIssue;
import com.japanese.content.dto.PromotionReadinessModels.PromotionReadinessResult;
import com.japanese.content.dto.PromotionReadinessModels.ReadinessListResult;
import com.japanese.content.dto.PromotionReadinessModels.ReadinessListRow;
import com.japanese.content.dto.PromotionReadinessModels.ReadinessPairView;
import com.japanese.content.dto.PromotionReadinessModels.ReadinessSummary;
import com.japanese.content.dto.PromotionReadinessModels.VocabExamplePreview;
import com.japanese.content.dto.PromotionReadinessModels.VocabularyMappingPreview;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.NormalizedGrammarCandidateDetail;
import com.japanese.content.entity.NormalizedVocabularyCandidateDetail;
import com.japanese.content.entity.NormalizedVocabularyCandidateExample;
import com.japanese.content.entity.NormalizedVocabularyCandidateMeaning;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4D: a 100% read-only "promotion readiness / dry-run" planner over the private
 * candidate pipeline built by Tickets 4A ({@code NormalizedCandidateStore}), 4B
 * ({@code NormalizedCandidateConflictAnalyzer}), and 4C ({@code NormalizedCandidatePairReviewService}).
 *
 * <p><b>What this answers</b>: "can this candidate be promoted to a production draft, and if not,
 * exactly what is blocking it?" It never answers "should this candidate be published" (that remains
 * {@code ContentReleaseGateService}/{@code ContentSourceRightsService}'s job once a draft exists) and
 * it never performs any promotion itself - see the absolute boundary below.
 *
 * <p><b>Absolute boundary (never done here, never will be by this class)</b>: no
 * {@code ContentItem}/{@code Word}/{@code Grammar}/{@code Meaning}/{@code Example} is ever created or
 * updated; {@code ImportedSourceRecord.linkContentItem} is never called; no candidate is merged,
 * deleted, or chosen as a canonical winner; no production {@code ContentItem.slug}/global identity is
 * ever decided; {@code ContentSource.rightsStatus} is never changed; nothing here is
 * {@code @Transactional} without {@code readOnly = true}. Recording a Ticket 4C
 * {@link HumanReviewDecision#SAME_CONTENT} decision is a statement about a <em>pair</em>, not a
 * canonical-winner selection - a candidate on either side of a fresh {@code SAME_CONTENT} pair is
 * therefore still never promotion-ready on its own (see
 * {@link PromotionReadinessIssueCode#SAME_CONTENT_CANONICAL_SELECTION_REQUIRED}).
 *
 * <p><b>Why every real candidate is blocked today</b>: {@link PromotionReadinessIssueCode#PRODUCTION_IDENTITY_POLICY_UNRESOLVED}
 * is added to every single candidate unconditionally, because this codebase has no ratified policy
 * for deriving a production {@code ContentItem.slug}/global identity from a private candidate
 * (source-native {@code EntryID}/{@code UnitID}, {@code sourceNoteId}, and normalized
 * expression+reading/pattern are all deliberately kept distinct from global production identity
 * throughout Tickets 4A-4C). A near-zero (or zero)
 * {@link com.japanese.content.dto.PromotionReadinessModels.OverallStatus#READY_FOR_DRAFT_PROMOTION}
 * count against the real v2.1.1 deck is therefore an expected finding of this ticket, not a defect -
 * see the Ticket 4D report for the actual measured numbers.
 *
 * <p><b>Grammar mapping is deliberately conservative</b>: production {@code Grammar.explanation} is
 * {@code NOT NULL}, but no normalized Grammar candidate field ({@code meaningGloss}/{@code nuance}/
 * {@code frontExample.translation}) has a ratified mapping onto it (see
 * {@code GrammarNormalizationResult}'s class javadoc, and Ticket 3B's own deferral of this exact
 * decision) - so every Grammar candidate also unconditionally carries
 * {@link PromotionReadinessIssueCode#GRAMMAR_MAPPING_POLICY_UNRESOLVED}. Only {@code pattern} and
 * {@code connection} (renamed from the normalized {@code connectionForm}, the "접속" reference-card
 * field - the one Grammar field with an unambiguous, label-identified production counterpart) are
 * ever shown as mapped in {@link GrammarMappingPreview}.
 *
 * <p><b>Vocabulary mapping is resolved</b> for {@code expression}/{@code reading}/{@code partOfSpeech}
 * (verbatim {@code AnkiFieldTextNormalizer.text(...)} copies, exactly like the sole existing
 * production {@code Word} writer, {@code ApkgVocabularyImporter}), {@code pitchAccent} (the
 * {@code "terminal="+terminalStates+";mora="+mora} serialization is that same importer's sole existing
 * {@code Word.pitchAccent} write format), {@code meanings} (Korean-language {@code Meaning} rows,
 * matching that importer's sole existing {@code Meaning} write path), and vocabulary
 * {@code examples} (the normalized examples are parsed by the very same {@code ExampleHtmlParser} the
 * production importer uses, so the mapping is lossless by construction) - each still individually
 * blocked by {@link PromotionReadinessIssueCode} when a value would not actually fit the target
 * production column, never silently truncated.
 *
 * <p><b>Existing production provenance</b> is detected via {@code ImportedSourceRecord}'s
 * {@code (sourceRef, noteType, sourceNoteId)} identity, where {@code noteType} is derived from
 * {@code candidateType} using the one correspondence this codebase actually encodes twice
 * independently: {@code PrivateApkgExtractor.category()} recognizes exactly the Anki note-type name
 * strings {@code "JLPT MAX덱 어휘"}/{@code "JLPT MAX덱 문법"} as VOCABULARY/GRAMMAR, and
 * {@code ApkgVocabularyImporter} (the sole existing {@code ImportedSourceRecord} writer for those
 * categories) uses those exact same two strings as its {@code noteType} values.
 *
 * <p><b>Zero-write/N+1</b>: every public method here is {@code @Transactional(readOnly = true)}.
 * {@link #list}/{@link #summary} each load a whole {@code (candidateType, sourceRef)} scope with a
 * small, fixed number of batch queries (candidates with detail fetch-joined, vocabulary meanings/
 * examples by candidate id, current Ticket 4B pairs by scope, Ticket 4C reviews by scope, JLPT
 * {@code Level} rows, source rights per distinct {@code sourceRef}, existing-provenance per distinct
 * {@code sourceRef}) - never one query per candidate, regardless of candidate count.
 */
@Service
public class NormalizedCandidatePromotionReadinessService {

    private static final int PAGE_SIZE = 25;

    private static final int WORD_EXPRESSION_MAX = 120;
    private static final int WORD_READING_MAX = 120;
    private static final int WORD_PART_OF_SPEECH_MAX = 120;
    private static final int WORD_PITCH_ACCENT_MAX = 500;
    private static final int MEANING_TEXT_MAX = 500;
    private static final int EXAMPLE_TEXT_MAX = 1000;
    private static final int GRAMMAR_PATTERN_MAX = 200;
    private static final int GRAMMAR_CONNECTION_MAX = 500;
    private static final String JLPT_LEVEL_SYSTEM = "JLPT";

    /** See this class's javadoc ("Existing production provenance") for why these two are correct. */
    private static final String VOCABULARY_NOTE_TYPE = "JLPT MAX덱 어휘";
    private static final String GRAMMAR_NOTE_TYPE = "JLPT MAX덱 문법";

    private final NormalizedContentCandidateRepository candidateRepository;
    private final NormalizedCandidateMatchPairRepository pairRepository;
    private final NormalizedCandidatePairReviewRepository reviewRepository;
    private final ImportedSourceRecordRepository importedSourceRecordRepository;
    private final LevelRepository levelRepository;
    private final ContentSourceRightsService sourceRights;
    private final EntityManager entityManager;

    public NormalizedCandidatePromotionReadinessService(
            NormalizedContentCandidateRepository candidateRepository,
            NormalizedCandidateMatchPairRepository pairRepository,
            NormalizedCandidatePairReviewRepository reviewRepository,
            ImportedSourceRecordRepository importedSourceRecordRepository,
            LevelRepository levelRepository,
            ContentSourceRightsService sourceRights,
            EntityManager entityManager) {
        this.candidateRepository = candidateRepository;
        this.pairRepository = pairRepository;
        this.reviewRepository = reviewRepository;
        this.importedSourceRecordRepository = importedSourceRecordRepository;
        this.levelRepository = levelRepository;
        this.sourceRights = sourceRights;
        this.entityManager = entityManager;
    }

    // ===================================================================================
    // List / summary
    // ===================================================================================

    @Transactional(readOnly = true)
    public ReadinessListResult list(NormalizedCandidateType candidateType, String sourceRef,
            OverallStatus overallStatusFilter, PromotionReadinessIssueCode issueCodeFilter,
            NormalizedCandidateQualityState qualityFilter, int page) {
        List<PromotionReadinessResult> all = computeAll(candidateType, blankToNull(sourceRef));
        List<ReadinessListRow> rows = all.stream()
                .filter(r -> overallStatusFilter == null || r.overallStatus() == overallStatusFilter)
                .filter(r -> issueCodeFilter == null
                        || r.issues().stream().anyMatch(i -> i.code() == issueCodeFilter))
                .filter(r -> qualityFilter == null || r.qualityState() == qualityFilter)
                .sorted(Comparator.comparing(PromotionReadinessResult::candidateId))
                .map(this::toRow)
                .toList();

        int total = rows.size();
        int safePage = Math.max(page, 0);
        int fromIndex = Math.min(safePage * PAGE_SIZE, total);
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);
        int totalPages = total == 0 ? 0 : (total + PAGE_SIZE - 1) / PAGE_SIZE;
        return new ReadinessListResult(rows.subList(fromIndex, toIndex), safePage, totalPages, total);
    }

    @Transactional(readOnly = true)
    public ReadinessSummary summary(NormalizedCandidateType candidateType, String sourceRef) {
        String scope = blankToNull(sourceRef);
        List<PromotionReadinessResult> all = computeAll(candidateType, scope);

        long ready = all.stream().filter(r -> r.overallStatus() == OverallStatus.READY_FOR_DRAFT_PROMOTION).count();
        long blocked = all.stream().filter(r -> r.overallStatus() == OverallStatus.BLOCKED).count();
        long already = all.stream().filter(r -> r.overallStatus() == OverallStatus.ALREADY_PROMOTED).count();

        Map<String, Long> blockedByIssueCode = new LinkedHashMap<>();
        for (PromotionReadinessIssueCode code : PromotionReadinessIssueCode.values()) {
            blockedByIssueCode.put(code.name(), 0L);
        }
        for (PromotionReadinessResult r : all) {
            for (PromotionReadinessIssue issue : r.issues()) {
                blockedByIssueCode.merge(issue.code().name(), 1L, Long::sum);
            }
        }

        Map<String, Long> qualityBreakdown = new LinkedHashMap<>();
        for (NormalizedCandidateQualityState s : NormalizedCandidateQualityState.values()) {
            qualityBreakdown.put(s.name(), 0L);
        }
        all.forEach(r -> qualityBreakdown.merge(r.qualityState().name(), 1L, Long::sum));

        Map<String, Long> pairResolutionBreakdown = new LinkedHashMap<>();
        for (PairResolutionStatus s : PairResolutionStatus.values()) {
            pairResolutionBreakdown.put(s.name(), 0L);
        }
        all.forEach(r -> pairResolutionBreakdown.merge(r.pairResolutionStatus().name(), 1L, Long::sum));

        Map<String, Long> mappingBreakdown = new LinkedHashMap<>();
        for (MappingStatus s : MappingStatus.values()) {
            mappingBreakdown.put(s.name(), 0L);
        }
        all.forEach(r -> mappingBreakdown.merge(r.mappingStatus().name(), 1L, Long::sum));

        Map<String, Long> sourceRightsBreakdown = new LinkedHashMap<>();
        for (ContentSourceRightsStatus s : ContentSourceRightsStatus.values()) {
            sourceRightsBreakdown.put(s.name(), 0L);
        }
        sourceRightsBreakdown.put("NOT_REGISTERED", 0L);
        all.forEach(r -> sourceRightsBreakdown.merge(
                r.sourceRightsStatus() == null ? "NOT_REGISTERED" : r.sourceRightsStatus().name(), 1L, Long::sum));

        Map<String, Long> candidatesByBlockerCount = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        all.forEach(r -> candidatesByBlockerCount.merge(String.valueOf(r.issues().size()), 1L, Long::sum));

        // Map.copyOf(...) does not guarantee it preserves the source map's iteration order, so the
        // enum-declaration order built above is preserved explicitly via an unmodifiable view instead.
        return new ReadinessSummary(candidateType, scope, all.size(), ready, blocked, already,
                Collections.unmodifiableMap(blockedByIssueCode), Collections.unmodifiableMap(qualityBreakdown),
                Collections.unmodifiableMap(pairResolutionBreakdown), Collections.unmodifiableMap(mappingBreakdown),
                Collections.unmodifiableMap(sourceRightsBreakdown), Collections.unmodifiableMap(candidatesByBlockerCount));
    }

    // ===================================================================================
    // Detail
    // ===================================================================================

    @Transactional(readOnly = true)
    public PromotionReadinessDetailView detail(NormalizedCandidateType candidateType, Long candidateId) {
        NormalizedContentCandidate candidate = candidateRepository.findById(candidateId)
                .filter(c -> c.getCandidateType() == candidateType)
                .orElseThrow(() -> new NoSuchElementException("candidate를 찾을 수 없습니다: " + candidateId));

        List<NormalizedCandidateMatchPair> pairs = pairRepository.findByEitherCandidateIdWithEvidence(candidateId);
        Map<PairKey, NormalizedCandidatePairReview> reviewsByPairKey = new LinkedHashMap<>();
        Map<Long, NormalizedContentCandidate> candidatesById = new HashMap<>();
        candidatesById.put(candidate.getId(), candidate);
        for (NormalizedCandidateMatchPair pair : pairs) {
            candidatesById.putIfAbsent(pair.getLeftCandidate().getId(), pair.getLeftCandidate());
            candidatesById.putIfAbsent(pair.getRightCandidate().getId(), pair.getRightCandidate());
            reviewRepository.findByLeftCandidateIdAndRightCandidateId(
                            pair.getLeftCandidate().getId(), pair.getRightCandidate().getId())
                    .ifPresent(r -> reviewsByPairKey.put(
                            new PairKey(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId()), r));
        }

        Map<String, Long> jlptLevelCodeCounts = jlptLevelCodeCounts();
        ContentSourceRightsService.ReleaseEligibility eligibility = sourceRights.releaseEligibility(candidate.getSourceRef());
        boolean alreadyPromoted = importedSourceRecordRepository.findBySourceRefAndNoteTypeAndSourceNoteIdIn(
                        candidate.getSourceRef(), productionNoteType(candidateType), List.of(candidate.getSourceNoteId()))
                .stream()
                .anyMatch(record -> record.getContentItem() != null);

        PromotionReadinessResult result = evaluate(candidate, candidate.getVocabularyMeanings(),
                candidate.getVocabularyExamples(), pairs, reviewsByPairKey, candidatesById, jlptLevelCodeCounts,
                eligibility, alreadyPromoted);

        CandidateFieldsView fields = candidateFields(candidate);
        List<ReadinessPairView> pairViews = pairs.stream()
                .map(p -> toPairView(p, candidateId, reviewsByPairKey))
                .toList();
        MappingPreview preview = mappingPreview(candidate);
        return new PromotionReadinessDetailView(result, fields, pairViews, preview);
    }

    // ===================================================================================
    // Core computation
    // ===================================================================================

    private List<PromotionReadinessResult> computeAll(NormalizedCandidateType candidateType, String sourceRef) {
        List<NormalizedContentCandidate> candidates =
                candidateRepository.findByCandidateTypeAndSourceRefWithDetailForReadiness(candidateType, sourceRef);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, NormalizedContentCandidate> candidatesById = candidates.stream()
                .collect(Collectors.toMap(NormalizedContentCandidate::getId, c -> c));

        boolean vocabulary = candidateType == NormalizedCandidateType.VOCABULARY;
        Map<Long, List<NormalizedVocabularyCandidateMeaning>> meaningsByCandidateId =
                vocabulary ? loadMeanings(candidatesById.keySet()) : Map.of();
        Map<Long, List<NormalizedVocabularyCandidateExample>> examplesByCandidateId =
                vocabulary ? loadExamples(candidatesById.keySet()) : Map.of();

        List<NormalizedCandidateMatchPair> pairs =
                pairRepository.findByCandidateTypeAndOptionalSourceRef(candidateType, sourceRef);
        Map<Long, List<NormalizedCandidateMatchPair>> pairsByCandidateId = new HashMap<>();
        for (NormalizedCandidateMatchPair pair : pairs) {
            pairsByCandidateId.computeIfAbsent(pair.getLeftCandidate().getId(), k -> new ArrayList<>()).add(pair);
            pairsByCandidateId.computeIfAbsent(pair.getRightCandidate().getId(), k -> new ArrayList<>()).add(pair);
        }

        Map<PairKey, NormalizedCandidatePairReview> reviewsByPairKey = reviewRepository
                .findForReviewList(candidateType, sourceRef).stream()
                .collect(Collectors.toMap(
                        r -> new PairKey(r.getLeftCandidate().getId(), r.getRightCandidate().getId()), r -> r));

        Map<String, Long> jlptLevelCodeCounts = jlptLevelCodeCounts();

        Map<String, List<NormalizedContentCandidate>> candidatesBySourceRef = candidates.stream()
                .collect(Collectors.groupingBy(NormalizedContentCandidate::getSourceRef));
        String noteType = productionNoteType(candidateType);
        Map<String, ContentSourceRightsService.ReleaseEligibility> eligibilityBySourceRef = new HashMap<>();
        Map<String, Set<Long>> alreadyPromotedNoteIdsBySourceRef = new HashMap<>();
        for (Map.Entry<String, List<NormalizedContentCandidate>> entry : candidatesBySourceRef.entrySet()) {
            String ref = entry.getKey();
            eligibilityBySourceRef.put(ref, sourceRights.releaseEligibility(ref));
            List<Long> noteIds = entry.getValue().stream().map(NormalizedContentCandidate::getSourceNoteId).toList();
            Set<Long> linked = importedSourceRecordRepository
                    .findBySourceRefAndNoteTypeAndSourceNoteIdIn(ref, noteType, noteIds).stream()
                    .filter(record -> record.getContentItem() != null)
                    .map(ImportedSourceRecord::getSourceNoteId)
                    .collect(Collectors.toSet());
            alreadyPromotedNoteIdsBySourceRef.put(ref, linked);
        }

        List<PromotionReadinessResult> results = new ArrayList<>(candidates.size());
        for (NormalizedContentCandidate candidate : candidates) {
            List<NormalizedVocabularyCandidateMeaning> meanings =
                    meaningsByCandidateId.getOrDefault(candidate.getId(), List.of());
            List<NormalizedVocabularyCandidateExample> examples =
                    examplesByCandidateId.getOrDefault(candidate.getId(), List.of());
            List<NormalizedCandidateMatchPair> candidatePairs =
                    pairsByCandidateId.getOrDefault(candidate.getId(), List.of());
            ContentSourceRightsService.ReleaseEligibility eligibility = eligibilityBySourceRef.get(candidate.getSourceRef());
            boolean alreadyPromoted = alreadyPromotedNoteIdsBySourceRef
                    .getOrDefault(candidate.getSourceRef(), Set.of())
                    .contains(candidate.getSourceNoteId());
            results.add(evaluate(candidate, meanings, examples, candidatePairs, reviewsByPairKey, candidatesById,
                    jlptLevelCodeCounts, eligibility, alreadyPromoted));
        }
        return results;
    }

    /**
     * Evaluates every readiness axis for exactly one candidate. Every collaborator is pre-resolved
     * by the caller ({@link #computeAll} in batch, {@link #detail} for a single candidate) so this
     * method itself never issues a query - it is pure computation over already-loaded state.
     */
    private PromotionReadinessResult evaluate(NormalizedContentCandidate candidate,
            List<NormalizedVocabularyCandidateMeaning> meanings,
            List<NormalizedVocabularyCandidateExample> examples,
            List<NormalizedCandidateMatchPair> candidatePairs,
            Map<PairKey, NormalizedCandidatePairReview> reviewsByPairKey,
            Map<Long, NormalizedContentCandidate> candidatesById,
            Map<String, Long> jlptLevelCodeCounts,
            ContentSourceRightsService.ReleaseEligibility sourceEligibility,
            boolean alreadyPromoted) {

        Map<PromotionReadinessIssueCode, PromotionReadinessIssue> issuesByCode = new LinkedHashMap<>();

        // A. normalization quality (Ticket 4A) - REVIEW_REQUIRED here is a completeness signal,
        // never satisfied by a Ticket 4C pair decision (see this class's javadoc).
        switch (candidate.getQualityState()) {
            case FATAL -> addIssue(issuesByCode, NORMALIZATION_FATAL, "정규화 결과가 FATAL 상태입니다.");
            case REVIEW_REQUIRED -> addIssue(issuesByCode, NORMALIZATION_REVIEW_REQUIRED,
                    "정규화 결과에 사람의 확인이 필요합니다 (REVIEW_REQUIRED).");
            case INFORMATIONAL, CLEAN -> { }
        }

        // B. pair/dedup review resolution (Ticket 4B/4C) - only CURRENT pairs are consulted; a
        // candidate with zero current pairs is resolved by absence (section 10/11 of the ticket).
        for (NormalizedCandidateMatchPair pair : candidatePairs) {
            NormalizedContentCandidate left = candidatesById.get(pair.getLeftCandidate().getId());
            NormalizedContentCandidate right = candidatesById.get(pair.getRightCandidate().getId());
            boolean pairFresh = !left.getNormalizedAt().isAfter(pair.getGeneratedAt())
                    && !right.getNormalizedAt().isAfter(pair.getGeneratedAt());
            if (!pairFresh) {
                addIssue(issuesByCode, PAIR_ANALYSIS_STALE,
                        "pair 분석이 최신 candidate 상태를 반영하지 못했습니다 (재분석 필요).");
                continue;
            }
            NormalizedCandidatePairReview review = reviewsByPairKey.get(
                    new PairKey(pair.getLeftCandidate().getId(), pair.getRightCandidate().getId()));
            if (review == null) {
                addIssue(issuesByCode, PAIR_UNREVIEWED, "현재 pair에 대한 human review가 없습니다.");
                continue;
            }
            boolean reviewFresh = pair.getAssessment() == review.getAssessmentSnapshot()
                    && left.getNormalizedAt().equals(review.getLeftNormalizedAtSnapshot())
                    && right.getNormalizedAt().equals(review.getRightNormalizedAtSnapshot());
            if (!reviewFresh) {
                addIssue(issuesByCode, PAIR_REVIEW_STALE,
                        "human review가 현재 candidate/pair 상태와 더 이상 일치하지 않습니다 (STALE).");
                continue;
            }
            switch (review.getDecision()) {
                case NEEDS_FOLLOWUP -> addIssue(issuesByCode, PAIR_NEEDS_FOLLOWUP,
                        "human review가 NEEDS_FOLLOWUP 상태입니다.");
                case SAME_CONTENT -> addIssue(issuesByCode, SAME_CONTENT_CANONICAL_SELECTION_REQUIRED,
                        "SAME_CONTENT로 판정된 pair의 canonical 후보를 아직 선택하지 않았습니다.");
                case DISTINCT_CONTENT -> { }
            }
        }

        // C. production mapping compatibility (field-level + JLPT level)
        if (candidate.getCandidateType() == NormalizedCandidateType.VOCABULARY) {
            evaluateVocabularyMapping(candidate.getVocabularyDetail(), meanings, examples, issuesByCode);
        } else {
            evaluateGrammarMapping(candidate.getGrammarDetail(), issuesByCode);
        }
        String levelCode = candidate.getCandidateType() == NormalizedCandidateType.VOCABULARY
                ? candidate.getVocabularyDetail().getLevelCode()
                : candidate.getGrammarDetail().getLevelCode();
        long matchingLevelCount = levelCode == null ? 0L : jlptLevelCodeCounts.getOrDefault(levelCode, 0L);
        if (matchingLevelCount != 1L) {
            String reason;
            if (levelCode == null) {
                reason = "candidate에 levelCode가 없습니다.";
            } else if (matchingLevelCount == 0L) {
                reason = "일치하는 production Level(system=JLPT, code=" + levelCode + ") row가 없습니다.";
            } else {
                // (system, code)에 database-level uniqueness constraint가 없어 동일 code row가 둘 이상 존재할 수
                // 있다 - 그중 하나를 임의로 골라 매핑하지 않고 unmappable로 처리한다.
                reason = "production Level(system=JLPT, code=" + levelCode + ") row가 " + matchingLevelCount
                        + "개 존재하여 candidate를 정확히 하나의 Level에 매핑할 수 없습니다 (중복).";
            }
            addIssue(issuesByCode, JLPT_LEVEL_UNMAPPABLE,
                    "JLPT 레벨을 기존 production Level(system=JLPT) row에 매핑할 수 없습니다 (levelCode="
                            + levelCode + "): " + reason);
        }

        // D. source rights / future release eligibility (reuses ContentSourceRightsService as-is)
        ContentSourceRightsStatus rightsStatus = sourceEligibility.rightsStatus();
        if (rightsStatus == null) {
            addIssue(issuesByCode, SOURCE_NOT_REGISTERED, sourceEligibility.blockingReason());
        } else if (!sourceEligibility.allowed()) {
            if (rightsStatus == ContentSourceRightsStatus.BLOCKED) {
                addIssue(issuesByCode, SOURCE_RIGHTS_NOT_ALLOWED, sourceEligibility.blockingReason());
            } else {
                addIssue(issuesByCode, SOURCE_RIGHTS_MANUAL_REVIEW, sourceEligibility.blockingReason());
            }
        }

        // F. unresolved production identity/slug policy - unconditional, see class javadoc.
        addIssue(issuesByCode, PRODUCTION_IDENTITY_POLICY_UNRESOLVED,
                "candidate로부터 production ContentItem의 전역 slug/identity를 도출하는 정책이 아직 없습니다.");

        // E. existing production provenance/link
        ExistingProductionLinkStatus linkStatus = alreadyPromoted
                ? ExistingProductionLinkStatus.LINKED
                : ExistingProductionLinkStatus.NOT_LINKED;
        if (alreadyPromoted) {
            addIssue(issuesByCode, ALREADY_PROMOTED, "이미 production ContentItem에 연결된 provenance가 있습니다.");
        }

        List<PromotionReadinessIssue> issues = issuesByCode.values().stream()
                .sorted(Comparator.comparingInt(i -> i.code().ordinal()))
                .toList();

        PairResolutionStatus pairResolutionStatus = issues.stream().anyMatch(i -> i.code() == PAIR_ANALYSIS_STALE
                || i.code() == PAIR_UNREVIEWED || i.code() == PAIR_REVIEW_STALE || i.code() == PAIR_NEEDS_FOLLOWUP
                || i.code() == SAME_CONTENT_CANONICAL_SELECTION_REQUIRED)
                ? PairResolutionStatus.BLOCKED
                : PairResolutionStatus.RESOLVED;
        MappingStatus mappingStatus = issues.stream().anyMatch(i -> isMappingIssue(i.code()))
                ? MappingStatus.BLOCKED
                : MappingStatus.READY;

        OverallStatus overall = alreadyPromoted
                ? OverallStatus.ALREADY_PROMOTED
                : issues.isEmpty() ? OverallStatus.READY_FOR_DRAFT_PROMOTION : OverallStatus.BLOCKED;

        return new PromotionReadinessResult(candidate.getId(), candidate.getCandidateType(), candidate.getSourceRef(),
                candidate.getSourceNoteId(), shortPreview(candidate), candidate.getQualityState(),
                pairResolutionStatus, mappingStatus, rightsStatus, ProductionIdentityStatus.UNRESOLVED,
                linkStatus, overall, issues);
    }

    private static boolean isMappingIssue(PromotionReadinessIssueCode code) {
        return switch (code) {
            case VOCAB_EXPRESSION_MISSING, VOCAB_EXPRESSION_TOO_LONG, VOCAB_READING_MISSING, VOCAB_READING_TOO_LONG,
                    VOCAB_PART_OF_SPEECH_TOO_LONG, VOCAB_PITCH_ACCENT_TOO_LONG, VOCAB_MEANING_MISSING,
                    VOCAB_MEANING_TOO_LONG, VOCAB_EXAMPLE_TEXT_TOO_LONG, GRAMMAR_PATTERN_MISSING,
                    GRAMMAR_PATTERN_TOO_LONG, GRAMMAR_CONNECTION_TOO_LONG, GRAMMAR_MAPPING_POLICY_UNRESOLVED,
                    JLPT_LEVEL_UNMAPPABLE -> true;
            default -> false;
        };
    }

    private void evaluateVocabularyMapping(NormalizedVocabularyCandidateDetail vocab,
            List<NormalizedVocabularyCandidateMeaning> meanings, List<NormalizedVocabularyCandidateExample> examples,
            Map<PromotionReadinessIssueCode, PromotionReadinessIssue> issuesByCode) {
        if (blank(vocab.getExpression())) {
            addIssue(issuesByCode, VOCAB_EXPRESSION_MISSING, "expression이 없습니다.");
        } else if (vocab.getExpression().length() > WORD_EXPRESSION_MAX) {
            addIssue(issuesByCode, VOCAB_EXPRESSION_TOO_LONG,
                    "expression 길이가 production Word.expression 제한(" + WORD_EXPRESSION_MAX + ")을 초과합니다.");
        }
        if (blank(vocab.getReading())) {
            addIssue(issuesByCode, VOCAB_READING_MISSING, "reading이 없습니다.");
        } else if (vocab.getReading().length() > WORD_READING_MAX) {
            addIssue(issuesByCode, VOCAB_READING_TOO_LONG,
                    "reading 길이가 production Word.reading 제한(" + WORD_READING_MAX + ")을 초과합니다.");
        }
        if (vocab.getPartOfSpeech() != null && vocab.getPartOfSpeech().length() > WORD_PART_OF_SPEECH_MAX) {
            addIssue(issuesByCode, VOCAB_PART_OF_SPEECH_TOO_LONG,
                    "partOfSpeech 길이가 production Word.partOfSpeech 제한(" + WORD_PART_OF_SPEECH_MAX + ")을 초과합니다.");
        }
        String pitchPreview = pitchAccentPreview(vocab);
        if (pitchPreview != null && pitchPreview.length() > WORD_PITCH_ACCENT_MAX) {
            addIssue(issuesByCode, VOCAB_PITCH_ACCENT_TOO_LONG,
                    "\"terminal=...;mora=...\" 직렬화 길이가 production Word.pitchAccent 제한(" + WORD_PITCH_ACCENT_MAX
                            + ")을 초과합니다.");
        }
        if (meanings.isEmpty()) {
            addIssue(issuesByCode, VOCAB_MEANING_MISSING, "meaning이 하나도 없습니다.");
        } else if (meanings.stream().anyMatch(m -> m.getMeaningText().length() > MEANING_TEXT_MAX)) {
            addIssue(issuesByCode, VOCAB_MEANING_TOO_LONG,
                    "meaning 길이가 production Meaning.text 제한(" + MEANING_TEXT_MAX + ")을 초과합니다.");
        }
        boolean exampleTooLong = examples.stream().anyMatch(e ->
                tooLong(e.getJapaneseText()) || tooLong(e.getReading()) || tooLong(e.getTranslation()));
        if (exampleTooLong) {
            addIssue(issuesByCode, VOCAB_EXAMPLE_TEXT_TOO_LONG,
                    "example 필드 길이가 production Example 제한(" + EXAMPLE_TEXT_MAX + ")을 초과합니다.");
        }
    }

    private static boolean tooLong(String value) {
        return value != null && value.length() > EXAMPLE_TEXT_MAX;
    }

    private void evaluateGrammarMapping(NormalizedGrammarCandidateDetail grammar,
            Map<PromotionReadinessIssueCode, PromotionReadinessIssue> issuesByCode) {
        if (blank(grammar.getPattern())) {
            addIssue(issuesByCode, GRAMMAR_PATTERN_MISSING, "pattern이 없습니다.");
        } else if (grammar.getPattern().length() > GRAMMAR_PATTERN_MAX) {
            addIssue(issuesByCode, GRAMMAR_PATTERN_TOO_LONG,
                    "pattern 길이가 production Grammar.pattern 제한(" + GRAMMAR_PATTERN_MAX + ")을 초과합니다.");
        }
        if (grammar.getConnectionForm() != null && grammar.getConnectionForm().length() > GRAMMAR_CONNECTION_MAX) {
            addIssue(issuesByCode, GRAMMAR_CONNECTION_TOO_LONG,
                    "connection 길이가 production Grammar.connection 제한(" + GRAMMAR_CONNECTION_MAX + ")을 초과합니다.");
        }
        addIssue(issuesByCode, GRAMMAR_MAPPING_POLICY_UNRESOLVED,
                "production Grammar.explanation(및 frontExample/confusablePatterns 매핑)에 대한 ratified 정책이 없습니다.");
    }

    private static void addIssue(Map<PromotionReadinessIssueCode, PromotionReadinessIssue> issuesByCode,
            PromotionReadinessIssueCode code, String message) {
        issuesByCode.putIfAbsent(code, new PromotionReadinessIssue(code, message));
    }

    // ===================================================================================
    // Batch loaders
    // ===================================================================================

    private Map<Long, List<NormalizedVocabularyCandidateMeaning>> loadMeanings(Collection<Long> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        List<NormalizedVocabularyCandidateMeaning> all = entityManager.createQuery(
                        "select m from NormalizedVocabularyCandidateMeaning m where m.candidate.id in :ids",
                        NormalizedVocabularyCandidateMeaning.class)
                .setParameter("ids", candidateIds)
                .getResultList();
        return all.stream().collect(Collectors.groupingBy(m -> m.getCandidate().getId()));
    }

    private Map<Long, List<NormalizedVocabularyCandidateExample>> loadExamples(Collection<Long> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        List<NormalizedVocabularyCandidateExample> all = entityManager.createQuery(
                        "select e from NormalizedVocabularyCandidateExample e where e.candidate.id in :ids",
                        NormalizedVocabularyCandidateExample.class)
                .setParameter("ids", candidateIds)
                .getResultList();
        return all.stream().collect(Collectors.groupingBy(e -> e.getCandidate().getId()));
    }

    /**
     * Counts production {@code Level} rows per {@code code} within {@code system == "JLPT"}. There is
     * no {@code (system, code)} uniqueness constraint on this table, so a code can legitimately have
     * zero, one, or more than one matching row; callers must require exactly one match before treating
     * a candidate's {@code levelCode} as mappable (see {@link #evaluate}).
     */
    private Map<String, Long> jlptLevelCodeCounts() {
        return levelRepository.findAllByOrderBySystemAscCodeAsc().stream()
                .filter(l -> JLPT_LEVEL_SYSTEM.equals(l.getSystem()))
                .collect(Collectors.groupingBy(Level::getCode, Collectors.counting()));
    }

    private static String productionNoteType(NormalizedCandidateType candidateType) {
        return candidateType == NormalizedCandidateType.VOCABULARY ? VOCABULARY_NOTE_TYPE : GRAMMAR_NOTE_TYPE;
    }

    // ===================================================================================
    // View assembly
    // ===================================================================================

    private ReadinessListRow toRow(PromotionReadinessResult r) {
        return new ReadinessListRow(r.candidateId(), r.candidateType(), r.sourceRef(), r.sourceNoteId(),
                r.shortPreview(), r.qualityState(), r.pairResolutionStatus(), r.mappingStatus(),
                r.sourceRightsStatus(), r.productionIdentityStatus(), r.existingProductionLinkStatus(),
                r.overallStatus(), r.primaryIssueCode(), r.issues().size());
    }

    private CandidateFieldsView candidateFields(NormalizedContentCandidate candidate) {
        List<WarningView> warnings = candidate.getWarnings().stream()
                .map(w -> new WarningView(w.getPosition(), w.getIssueCode(), w.getSeverity(), w.getMessage()))
                .toList();
        NormalizedVocabularyCandidateDetail vocab = candidate.getVocabularyDetail();
        NormalizedGrammarCandidateDetail grammar = candidate.getGrammarDetail();
        List<String> meanings = candidate.getVocabularyMeanings().stream()
                .map(NormalizedVocabularyCandidateMeaning::getMeaningText)
                .toList();
        List<VocabExampleView> examples = candidate.getVocabularyExamples().stream()
                .map(e -> new VocabExampleView(e.getMeaningLabel(), e.getJapaneseText(), e.getReading(), e.getTranslation()))
                .toList();
        List<ConfusablePatternView> confusables = candidate.getGrammarConfusablePatterns().stream()
                .map(p -> new ConfusablePatternView(p.getPattern(), p.getExplanation()))
                .toList();
        return new CandidateFieldsView(candidate.getId(), candidate.getCandidateType(), candidate.getSourceRef(),
                candidate.getSourceNoteId(), candidate.getSourceIdentityKey(), candidate.getQualityState(),
                candidate.getNormalizedAt(), warnings,
                vocab == null ? null : vocab.getEntryId(),
                vocab == null ? null : vocab.getExpression(),
                vocab == null ? null : vocab.getReading(),
                vocab == null ? null : vocab.getPartOfSpeech(),
                vocab == null ? null : vocab.getPitchAccentTerminalStates(),
                vocab == null ? null : vocab.getPitchAccentMora(),
                vocab == null ? null : vocab.getLevelCode(),
                meanings, examples,
                grammar == null ? null : grammar.getUnitId(),
                grammar == null ? null : grammar.getPattern(),
                grammar == null ? null : grammar.getMeaningGloss(),
                grammar == null ? null : grammar.getNuance(),
                grammar == null ? null : grammar.getConnectionForm(),
                grammar == null ? null : grammar.getFrontExampleJapaneseText(),
                grammar == null ? null : grammar.getFrontExampleReading(),
                grammar == null ? null : grammar.getFrontExampleTranslation(),
                grammar == null ? null : grammar.getRawKind(),
                confusables);
    }

    private MappingPreview mappingPreview(NormalizedContentCandidate candidate) {
        if (candidate.getCandidateType() == NormalizedCandidateType.VOCABULARY) {
            NormalizedVocabularyCandidateDetail vocab = candidate.getVocabularyDetail();
            List<String> meanings = candidate.getVocabularyMeanings().stream()
                    .map(NormalizedVocabularyCandidateMeaning::getMeaningText)
                    .toList();
            List<VocabExamplePreview> examples = candidate.getVocabularyExamples().stream()
                    .map(e -> new VocabExamplePreview(e.getMeaningLabel(), e.getJapaneseText(), e.getReading(), e.getTranslation()))
                    .toList();
            VocabularyMappingPreview preview = new VocabularyMappingPreview(vocab.getExpression(), vocab.getReading(),
                    vocab.getPartOfSpeech(), pitchAccentPreview(vocab), meanings, examples, vocab.getLevelCode());
            return new MappingPreview(preview, null);
        }
        NormalizedGrammarCandidateDetail grammar = candidate.getGrammarDetail();
        List<ConfusablePatternPreview> confusables = candidate.getGrammarConfusablePatterns().stream()
                .map(p -> new ConfusablePatternPreview(p.getPattern(), p.getExplanation()))
                .toList();
        GrammarMappingPreview preview = new GrammarMappingPreview(grammar.getPattern(), grammar.getConnectionForm(),
                grammar.getMeaningGloss(), grammar.getNuance(), grammar.getFrontExampleJapaneseText(),
                grammar.getFrontExampleReading(), grammar.getFrontExampleTranslation(), confusables,
                grammar.getRawKind(), grammar.getLevelCode(), true);
        return new MappingPreview(null, preview);
    }

    private static String pitchAccentPreview(NormalizedVocabularyCandidateDetail vocab) {
        if (vocab.getPitchAccentTerminalStates() == null || vocab.getPitchAccentMora() == null) {
            return null;
        }
        return "terminal=" + vocab.getPitchAccentTerminalStates() + ";mora=" + vocab.getPitchAccentMora();
    }

    private ReadinessPairView toPairView(NormalizedCandidateMatchPair pair, Long candidateId,
            Map<PairKey, NormalizedCandidatePairReview> reviewsByPairKey) {
        NormalizedContentCandidate left = pair.getLeftCandidate();
        NormalizedContentCandidate right = pair.getRightCandidate();
        NormalizedContentCandidate partner = left.getId().equals(candidateId) ? right : left;
        boolean pairFresh = !left.getNormalizedAt().isAfter(pair.getGeneratedAt())
                && !right.getNormalizedAt().isAfter(pair.getGeneratedAt());
        NormalizedCandidatePairReview review = reviewsByPairKey.get(new PairKey(left.getId(), right.getId()));

        ReviewFreshness reviewFreshness;
        if (review == null) {
            reviewFreshness = ReviewFreshness.NOT_REVIEWED;
        } else {
            boolean matches = pair.getAssessment() == review.getAssessmentSnapshot()
                    && left.getNormalizedAt().equals(review.getLeftNormalizedAtSnapshot())
                    && right.getNormalizedAt().equals(review.getRightNormalizedAtSnapshot());
            reviewFreshness = matches ? ReviewFreshness.FRESH : ReviewFreshness.STALE;
        }

        List<PromotionReadinessIssueCode> pairIssues = new ArrayList<>();
        if (!pairFresh) {
            pairIssues.add(PAIR_ANALYSIS_STALE);
        } else if (review == null) {
            pairIssues.add(PAIR_UNREVIEWED);
        } else if (reviewFreshness != ReviewFreshness.FRESH) {
            pairIssues.add(PAIR_REVIEW_STALE);
        } else if (review.getDecision() == HumanReviewDecision.NEEDS_FOLLOWUP) {
            pairIssues.add(PAIR_NEEDS_FOLLOWUP);
        } else if (review.getDecision() == HumanReviewDecision.SAME_CONTENT) {
            pairIssues.add(SAME_CONTENT_CANONICAL_SELECTION_REQUIRED);
        }

        return new ReadinessPairView(partner.getId(), shortPreview(partner), pair.getAssessment(),
                pair.getGeneratedAt(), pairFresh ? PairFreshness.FRESH : PairFreshness.STALE,
                review == null ? null : review.getDecision(), reviewFreshness, List.copyOf(pairIssues));
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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PairKey(Long leftId, Long rightId) {
    }
}
