package com.japanese.content.service;

import static com.japanese.content.service.PromotionReadinessIssueCode.ALREADY_PROMOTED;
import static com.japanese.content.service.PromotionReadinessIssueCode.GRAMMAR_CONNECTION_TOO_LONG;
import static com.japanese.content.service.PromotionReadinessIssueCode.GRAMMAR_EXAMPLE_TEXT_TOO_LONG;
import static com.japanese.content.service.PromotionReadinessIssueCode.GRAMMAR_EXPLANATION_SOURCE_MISSING;
import static com.japanese.content.service.PromotionReadinessIssueCode.GRAMMAR_EXPLANATION_TOO_LONG;
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
import static com.japanese.content.service.PromotionReadinessIssueCode.VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED;

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
 * deleted, or chosen as a canonical winner; {@code ContentSource.rightsStatus} is never changed;
 * nothing here is {@code @Transactional} without {@code readOnly = true}. {@code ProductionContentSlugPolicy}
 * (Ticket 4E-0) is consulted only via its side-effect-free {@code supports(...)} check - its
 * {@code generateSlug(...)} is never called here, so no candidate is ever assigned a production slug
 * by this class, and repeated readiness calls never mint a new identity. Recording a Ticket 4C
 * {@link HumanReviewDecision#SAME_CONTENT} decision is a statement about a <em>pair</em>, not a
 * canonical-winner selection - a candidate on either side of a fresh {@code SAME_CONTENT} pair is
 * therefore still never promotion-ready on its own (see
 * {@link PromotionReadinessIssueCode#SAME_CONTENT_CANONICAL_SELECTION_REQUIRED}).
 *
 * <p><b>Production identity/slug (Ticket 4E-0)</b>: {@link PromotionReadinessIssueCode#PRODUCTION_IDENTITY_POLICY_UNRESOLVED}
 * is added only when {@code ProductionContentSlugPolicy.supports(candidate.getCandidateType())} is
 * {@code false} - as of this ticket that policy supports both {@code VOCABULARY} and {@code GRAMMAR},
 * so this axis is resolved for every real candidate today. What remains unconditional (Grammar
 * mapping) or source-conditional (Vocabulary meaning language, see below) still keeps
 * {@link com.japanese.content.dto.PromotionReadinessModels.OverallStatus#READY_FOR_DRAFT_PROMOTION}
 * rare against the real v2.1.1 deck - see the Ticket 4D report for the pre-4E-0 measured numbers and
 * the Ticket 4E-0 report for what changed.
 *
 * <p><b>Grammar mapping (Ticket 4E-8, ratified)</b>: {@code Grammar.pattern} is the candidate's
 * {@code pattern} verbatim; {@code Grammar.connection} is the candidate's {@code connectionForm}
 * verbatim (may be null); {@code Grammar.explanation} (NOT NULL, max 2000) is
 * {@code meaningGloss + "\n\n" + nuance} - see {@link #composeGrammarExplanation} for the single
 * shared implementation both this readiness check and
 * {@code NormalizedGrammarCandidatePromotionService} use, so the two can never drift. This
 * composition is truthful without fabrication because {@code MISSING_MEANING_GLOSS}/
 * {@code MISSING_NUANCE} are FATAL {@code GrammarNormalizationIssue} severities - any candidate that
 * reaches this check already has both fields non-blank. {@code frontExample}/
 * {@code confusablePatterns} are shown in {@link GrammarMappingPreview} for human context only;
 * {@code confusablePatterns} is never resolved into a {@code GrammarRelation}/{@code GrammarComparison}
 * by any ticket (that would require inferring another production Grammar's identity from free text).
 *
 * <p><b>Vocabulary mapping is resolved</b> for {@code expression}/{@code reading}/{@code partOfSpeech}
 * (verbatim {@code AnkiFieldTextNormalizer.text(...)} copies, exactly like the sole existing
 * production {@code Word} writer, {@code ApkgVocabularyImporter}), {@code pitchAccent} (the
 * {@code "terminal="+terminalStates+";mora="+mora} serialization is that same importer's sole existing
 * {@code Word.pitchAccent} write format), and vocabulary {@code examples} (the normalized examples are
 * parsed by the very same {@code ExampleHtmlParser} the production importer uses, so the mapping is
 * lossless by construction) - each still individually blocked by {@link PromotionReadinessIssueCode}
 * when a value would not actually fit the target production column, never silently truncated.
 * {@code meanings} additionally require {@code NormalizedVocabularyMeaningLanguagePolicy} to resolve a
 * production {@code Meaning.languageTag} for the candidate's {@code sourceRef} (Ticket 4E-0) - see
 * {@link PromotionReadinessIssueCode#VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED}.
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
 *
 * <p><b>Ticket 4E-1's intended promotion transaction contract</b> (not implemented by this ticket -
 * this class remains 100% read-only; recorded here as the contract a future write service must
 * follow, using the {@code PESSIMISTIC_WRITE} lock primitives this ticket adds to
 * {@code NormalizedContentCandidateRepository}/{@code ImportedSourceRecordRepository}):
 * <ol>
 *   <li>Lock the candidate row ({@code findByIdAndCandidateTypeForPromotion}).</li>
 *   <li>Re-verify candidate/type/source identity against the locked row.</li>
 *   <li>Recompute readiness inside this same transaction (never trust a stale GET-time result).</li>
 *   <li>If an {@code ImportedSourceRecord} already exists for this identity, lock that row too
 *       ({@code findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion}); a race where no row exists
 *       yet is instead guarded by the candidate lock plus the existing
 *       {@code uk_imported_source_record} unique constraint plus this whole transaction rolling back
 *       together on conflict - a bare row lock cannot protect a row that does not exist yet.</li>
 *   <li>Re-check {@link PromotionReadinessIssueCode#ALREADY_PROMOTED} against the (now-locked) state.</li>
 *   <li>Only then create the production entity/entities.</li>
 *   <li>Link provenance in the same transaction.</li>
 *   <li>Commit - or roll back the whole transaction on any failure, leaving no partial production row.</li>
 * </ol>
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
    private static final int GRAMMAR_EXPLANATION_MAX = 2000;
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
    private final ProductionContentSlugPolicy slugPolicy;
    private final NormalizedVocabularyMeaningLanguagePolicy meaningLanguagePolicy;
    private final EntityManager entityManager;

    public NormalizedCandidatePromotionReadinessService(
            NormalizedContentCandidateRepository candidateRepository,
            NormalizedCandidateMatchPairRepository pairRepository,
            NormalizedCandidatePairReviewRepository reviewRepository,
            ImportedSourceRecordRepository importedSourceRecordRepository,
            LevelRepository levelRepository,
            ContentSourceRightsService sourceRights,
            ProductionContentSlugPolicy slugPolicy,
            NormalizedVocabularyMeaningLanguagePolicy meaningLanguagePolicy,
            EntityManager entityManager) {
        this.candidateRepository = candidateRepository;
        this.pairRepository = pairRepository;
        this.reviewRepository = reviewRepository;
        this.importedSourceRecordRepository = importedSourceRecordRepository;
        this.levelRepository = levelRepository;
        this.sourceRights = sourceRights;
        this.slugPolicy = slugPolicy;
        this.meaningLanguagePolicy = meaningLanguagePolicy;
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
            // C2. Vocabulary meaning-language policy (Ticket 4E-0) - VOCABULARY only, see class javadoc.
            if (meaningLanguagePolicy.resolveLanguageTag(candidate.getSourceRef()).isEmpty()) {
                addIssue(issuesByCode, VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED,
                        "이 candidate의 sourceRef에 대해 production Meaning.languageTag를 결정할 ratified 정책이 없습니다.");
            }
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

        // F. production identity/slug policy (Ticket 4E-0) - resolved once ProductionContentSlugPolicy
        // supports this candidate's type; never depends on sourceRef or any other candidate field.
        boolean identityResolved = slugPolicy.supports(candidate.getCandidateType());
        if (!identityResolved) {
            addIssue(issuesByCode, PRODUCTION_IDENTITY_POLICY_UNRESOLVED,
                    "candidate로부터 production ContentItem의 전역 slug/identity를 도출하는 정책이 아직 없습니다.");
        }

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
                pairResolutionStatus, mappingStatus, rightsStatus,
                identityResolved ? ProductionIdentityStatus.RESOLVED : ProductionIdentityStatus.UNRESOLVED,
                linkStatus, overall, issues);
    }

    /**
     * Package-private (not {@code private}) so {@code NormalizedCandidateGroupPromotionService}
     * (Ticket 4E-3B) can reuse this exact classification when deciding which readiness issues a
     * non-canonical group member is exempt from (its own {@code Word}/{@code Meaning}/{@code Example}
     * field mapping is never written to production, since only the canonical candidate supplies
     * production content) - this is the same "content-mapping vs. everything else" split
     * {@link MappingStatus} already exposes read-only for a single candidate; sharing it here is not
     * new readiness-policy duplication, only reuse of an existing, already-reviewed classification.
     */
    static boolean isMappingIssue(PromotionReadinessIssueCode code) {
        return switch (code) {
            case VOCAB_EXPRESSION_MISSING, VOCAB_EXPRESSION_TOO_LONG, VOCAB_READING_MISSING, VOCAB_READING_TOO_LONG,
                    VOCAB_PART_OF_SPEECH_TOO_LONG, VOCAB_PITCH_ACCENT_TOO_LONG, VOCAB_MEANING_MISSING,
                    VOCAB_MEANING_TOO_LONG, VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED,
                    VOCAB_EXAMPLE_TEXT_TOO_LONG, GRAMMAR_PATTERN_MISSING,
                    GRAMMAR_PATTERN_TOO_LONG, GRAMMAR_CONNECTION_TOO_LONG, GRAMMAR_EXAMPLE_TEXT_TOO_LONG,
                    GRAMMAR_EXPLANATION_TOO_LONG, GRAMMAR_EXPLANATION_SOURCE_MISSING,
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
        boolean frontExampleTooLong = tooLong(grammar.getFrontExampleJapaneseText())
                || tooLong(grammar.getFrontExampleReading()) || tooLong(grammar.getFrontExampleTranslation());
        if (frontExampleTooLong) {
            addIssue(issuesByCode, GRAMMAR_EXAMPLE_TEXT_TOO_LONG,
                    "frontExample 필드 길이가 production Example 제한(" + EXAMPLE_TEXT_MAX + ")을 초과합니다.");
        }
        String explanation = composeGrammarExplanation(grammar);
        if (explanation == null) {
            addIssue(issuesByCode, GRAMMAR_EXPLANATION_SOURCE_MISSING,
                    "meaningGloss 또는 nuance가 없어 production Grammar.explanation을 구성할 수 없습니다.");
        } else if (explanation.length() > GRAMMAR_EXPLANATION_MAX) {
            addIssue(issuesByCode, GRAMMAR_EXPLANATION_TOO_LONG,
                    "meaningGloss+nuance 조합 길이가 production Grammar.explanation 제한(" + GRAMMAR_EXPLANATION_MAX
                            + ")을 초과합니다.");
        }
    }

    /**
     * Package-private (not {@code private}) so {@code NormalizedGrammarCandidatePromotionService}
     * (Ticket 4E-8) can reuse this exact composition when actually writing {@code Grammar.explanation} -
     * the single shared implementation of the ratified {@code meaningGloss + "\n\n" + nuance} mapping,
     * mirroring {@link #pitchAccentPreview}'s existing precedent for Vocabulary. Returns {@code null}
     * (Ticket 4E-8 hardening, MINOR 1) rather than fabricating a {@code "null\n\n..."}-shaped string
     * when {@code meaningGloss} or {@code nuance} is blank/missing - an incomplete/FATAL candidate
     * snapshot never has a real composed explanation, and {@link #evaluateGrammarMapping} surfaces that
     * via {@link PromotionReadinessIssueCode#GRAMMAR_EXPLANATION_SOURCE_MISSING} instead of running the
     * {@code GRAMMAR_EXPLANATION_TOO_LONG} length check against a fabricated value.
     */
    static String composeGrammarExplanation(NormalizedGrammarCandidateDetail grammar) {
        if (blank(grammar.getMeaningGloss()) || blank(grammar.getNuance())) {
            return null;
        }
        return grammar.getMeaningGloss() + "\n\n" + grammar.getNuance();
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
                grammar.getRawKind(), grammar.getLevelCode(), composeGrammarExplanation(grammar));
        return new MappingPreview(null, preview);
    }

    /**
     * Package-private (not {@code private}) so {@code NormalizedVocabularyCandidatePromotionService}
     * (Ticket 4E-1) can reuse this exact serialization when actually writing {@code Word.pitchAccent} -
     * this is a pure, side-effect-free format helper (identical to {@code ApkgVocabularyImporter}'s own
     * {@code "terminal=...;mora=..."} convention), not a promotion-readiness business rule, so sharing
     * it is not the kind of readiness-policy duplication the Ticket 4E-1 contract above warns against.
     */
    static String pitchAccentPreview(NormalizedVocabularyCandidateDetail vocab) {
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
