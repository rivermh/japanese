package com.japanese.content.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.CandidateFieldsView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.ConfusablePatternView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.CurrentReviewView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.DecisionSubmission;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.EvidenceView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.HistoryEntryView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.PairFreshness;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.ReviewDetailView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.ReviewFreshness;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.ReviewListResult;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.ReviewListRow;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.VocabExampleView;
import com.japanese.content.dto.NormalizedCandidatePairReviewModels.WarningView;
import com.japanese.content.entity.HumanReviewDecision;
import com.japanese.content.entity.NormalizedCandidateMatchAssessment;
import com.japanese.content.entity.NormalizedCandidateMatchEvidence;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidatePairReviewHistory;
import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.NormalizedGrammarCandidateDetail;
import com.japanese.content.entity.NormalizedVocabularyCandidateDetail;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewHistoryRepository;
import com.japanese.content.repository.NormalizedCandidatePairReviewRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4C: the private-candidate human-review workflow service - list/detail read paths
 * over Ticket 4B's {@link NormalizedCandidateMatchPair} analysis plus this ticket's own
 * {@link NormalizedCandidatePairReview}/{@link NormalizedCandidatePairReviewHistory}, and the single
 * write path that records a human decision.
 *
 * <p><b>Boundary</b> (JLPT-MAX Ticket 4C): this class never reads, writes, or references
 * {@code ContentItem}/{@code ReviewStatus}/{@code ContentReviewHistory}/{@code CurationReviewHistory}/
 * {@code GrammarRelation}/{@code GrammarComparison} - production review state and this private
 * candidate-pair review state are entirely separate domains. Recording {@link HumanReviewDecision#SAME_CONTENT}
 * never merges, deletes a candidate, picks a canonical winner, or promotes anything to production -
 * it only ever records what a human decided.
 */
@Service
public class NormalizedCandidatePairReviewService {

    private static final int PAGE_SIZE = 25;
    private static final int MAX_REVIEW_NOTE_LENGTH = 2000;

    private final NormalizedContentCandidateRepository candidateRepository;
    private final NormalizedCandidateMatchPairRepository pairRepository;
    private final NormalizedCandidatePairReviewRepository reviewRepository;
    private final NormalizedCandidatePairReviewHistoryRepository historyRepository;
    private final NormalizedCandidateConflictAnalyzer analyzer;

    public NormalizedCandidatePairReviewService(NormalizedContentCandidateRepository candidateRepository,
            NormalizedCandidateMatchPairRepository pairRepository,
            NormalizedCandidatePairReviewRepository reviewRepository,
            NormalizedCandidatePairReviewHistoryRepository historyRepository,
            NormalizedCandidateConflictAnalyzer analyzer) {
        this.candidateRepository = candidateRepository;
        this.pairRepository = pairRepository;
        this.reviewRepository = reviewRepository;
        this.historyRepository = historyRepository;
        this.analyzer = analyzer;
    }

    // ===================================================================================
    // List
    // ===================================================================================

    /**
     * All filtering/pagination happens in memory over the current Ticket 4B pairs for
     * {@code candidateType} (+ optional {@code sourceRef}) - at the real v2.1.1 deck's current scale
     * (33 pairs total) this is simpler and just as correct as a DB-level dynamic query, and every
     * filterable signal here (human decision, freshness) is computed, not a stored column that a
     * plain {@code WHERE} could target anyway. The DB side still does a fixed, small number of
     * queries regardless of row count - no per-row query is issued.
     */
    @Transactional(readOnly = true)
    public ReviewListResult list(NormalizedCandidateType candidateType, String sourceRef,
            NormalizedCandidateMatchAssessment assessmentFilter, String decisionFilter, String freshnessFilter,
            int page) {
        String scopeSourceRef = blankToNull(sourceRef);
        List<NormalizedCandidateMatchPair> pairs = candidateType == NormalizedCandidateType.VOCABULARY
                ? pairRepository.findVocabularyPairsForReview(scopeSourceRef)
                : pairRepository.findGrammarPairsForReview(scopeSourceRef);
        Map<PairKey, NormalizedCandidatePairReview> reviewsByKey = reviewRepository
                .findForReviewList(candidateType, scopeSourceRef).stream()
                .collect(Collectors.toMap(
                        r -> new PairKey(r.getLeftCandidate().getId(), r.getRightCandidate().getId()), r -> r));

        List<ReviewListRow> rows = pairs.stream()
                .sorted(Comparator.comparing(NormalizedCandidateMatchPair::getId))
                .map(pair -> toRow(pair, reviewsByKey.get(new PairKey(pair.getLeftCandidate().getId(),
                        pair.getRightCandidate().getId()))))
                .filter(row -> assessmentFilter == null || row.assessment() == assessmentFilter)
                .filter(row -> matchesDecisionFilter(row, decisionFilter))
                .filter(row -> freshnessFilter == null || freshnessFilter.isBlank()
                        || row.pairFreshness().name().equals(freshnessFilter))
                .toList();

        int total = rows.size();
        int safePage = Math.max(page, 0);
        int fromIndex = Math.min(safePage * PAGE_SIZE, total);
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);
        int totalPages = total == 0 ? 0 : (total + PAGE_SIZE - 1) / PAGE_SIZE;
        return new ReviewListResult(rows.subList(fromIndex, toIndex), safePage, totalPages, total);
    }

    private boolean matchesDecisionFilter(ReviewListRow row, String decisionFilter) {
        if (decisionFilter == null || decisionFilter.isBlank()) {
            return true;
        }
        if ("UNREVIEWED".equals(decisionFilter)) {
            return row.decision() == null;
        }
        return row.decision() == HumanReviewDecision.valueOf(decisionFilter);
    }

    private ReviewListRow toRow(NormalizedCandidateMatchPair pair, NormalizedCandidatePairReview review) {
        NormalizedContentCandidate left = pair.getLeftCandidate();
        NormalizedContentCandidate right = pair.getRightCandidate();
        PairFreshness pairFreshness = pairFreshness(pair, left, right);
        ReviewFreshness reviewFreshness = reviewFreshness(review, pair, left, right);
        NormalizedCandidateQualityState worst = NormalizedCandidateQualityState
                .worstOf(List.of(left.getQualityState(), right.getQualityState()));
        return new ReviewListRow(left.getId(), right.getId(), left.getCandidateType(), left.getSourceRef(),
                pair.getAssessment(), pair.getGeneratedAt(), pairFreshness, worst,
                shortPreview(left), shortPreview(right),
                review == null ? null : review.getDecision(), reviewFreshness,
                review == null ? null : review.getReviewer().getDisplayName(),
                review == null ? null : review.getReviewedAt());
    }

    private String shortPreview(NormalizedContentCandidate candidate) {
        NormalizedVocabularyCandidateDetail vocab = candidate.getVocabularyDetail();
        if (vocab != null) {
            return joinNonBlank(vocab.getExpression(), vocab.getReading());
        }
        NormalizedGrammarCandidateDetail grammar = candidate.getGrammarDetail();
        if (grammar != null) {
            return grammar.getPattern();
        }
        return null;
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

    // ===================================================================================
    // Detail
    // ===================================================================================

    /**
     * Detail is looked up by the two stable candidate ids, never by a Ticket 4B pair id (which is
     * not stable - see {@link NormalizedCandidatePairReview}'s javadoc). Deliberately does not
     * require a current pair to exist: a pair that disappeared after a candidate refresh/reanalyze
     * must still render its preserved review/history without crashing (JLPT-MAX Ticket 4C).
     */
    @Transactional(readOnly = true)
    public ReviewDetailView detail(NormalizedCandidateType candidateType, Long leftCandidateId, Long rightCandidateId) {
        NormalizedContentCandidate left = requireCandidate(leftCandidateId);
        NormalizedContentCandidate right = requireCandidate(rightCandidateId);
        requireCanonicalSameScopePair(candidateType, left, right);

        Optional<NormalizedCandidateMatchPair> currentPair =
                pairRepository.findWithEvidenceByCandidateIds(leftCandidateId, rightCandidateId);
        Optional<NormalizedCandidatePairReview> review =
                reviewRepository.findByLeftCandidateIdAndRightCandidateId(leftCandidateId, rightCandidateId);
        List<NormalizedCandidatePairReviewHistory> history =
                historyRepository.findByCandidatePairOrderByReviewedAtAsc(leftCandidateId, rightCandidateId);

        boolean analysisPresent = currentPair.isPresent();
        PairFreshness pairFreshness = currentPair.map(p -> pairFreshness(p, left, right)).orElse(null);
        ReviewFreshness reviewFreshness = reviewFreshness(review.orElse(null), currentPair.orElse(null), left, right);

        List<EvidenceView> evidenceViews = currentPair.map(NormalizedCandidateMatchPair::getEvidence).orElse(List.of())
                .stream()
                .sorted(Comparator.comparingInt(NormalizedCandidateMatchEvidence::getPosition))
                .map(e -> new EvidenceView(e.getPosition(), e.getEvidenceCode(), e.getFieldName(), e.getDetail()))
                .toList();

        CurrentReviewView currentReviewView = review.map(r -> new CurrentReviewView(r.getDecision(),
                r.getReviewer().getDisplayName(), r.getNote(), r.getReviewedAt(), reviewFreshness, r.getVersion()))
                .orElse(null);

        List<HistoryEntryView> historyViews = history.stream()
                .map(h -> new HistoryEntryView(h.getPreviousDecision(), h.getNewDecision(),
                        h.getReviewer().getDisplayName(), h.getNote(), h.getReviewedAt(), h.getAssessmentSnapshot()))
                .toList();

        return new ReviewDetailView(candidateType, left.getSourceRef(), left.getId(), right.getId(),
                currentPair.map(NormalizedCandidateMatchPair::getAssessment).orElse(null),
                currentPair.map(NormalizedCandidateMatchPair::getGeneratedAt).orElse(null),
                pairFreshness, analysisPresent, evidenceViews, candidateFields(left), candidateFields(right),
                currentReviewView, historyViews);
    }

    private CandidateFieldsView candidateFields(NormalizedContentCandidate candidate) {
        List<WarningView> warnings = candidate.getWarnings().stream()
                .map(w -> new WarningView(w.getPosition(), w.getIssueCode(), w.getSeverity(), w.getMessage()))
                .toList();

        NormalizedVocabularyCandidateDetail vocab = candidate.getVocabularyDetail();
        NormalizedGrammarCandidateDetail grammar = candidate.getGrammarDetail();

        List<String> meanings = candidate.getVocabularyMeanings().stream()
                .map(m -> m.getMeaningText())
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

    // ===================================================================================
    // Decision submission
    // ===================================================================================

    /**
     * Records a human decision for one pair identity, enforcing every eligibility/freshness/
     * concurrency check up front (JLPT-MAX Ticket 4C) before touching any row:
     * <ol>
     * <li>both candidates exist, share {@code candidateType} and {@code sourceRef};</li>
     * <li>a current Ticket 4B pair exists for this identity;</li>
     * <li>the submission's {@code expectedAssessment}/{@code expectedPairGeneratedAt} still match the
     * current pair exactly - any reanalysis between page render and submit is rejected as a
     * conflict, even if the newly computed analysis happens to be identical in content;</li>
     * <li>the pair is currently FRESH (candidates not refreshed since the pair was generated);</li>
     * <li>if a review already exists, {@code expectedReviewVersion} still matches (optimistic
     * concurrency against a second admin's concurrent re-review).</li>
     * </ol>
     * Never mutates {@link NormalizedCandidateMatchPair#getAssessment()} - only ever writes to the
     * private review/history tables.
     */
    @Transactional
    public CurrentReviewView submitDecision(NormalizedCandidateType candidateType, DecisionSubmission submission,
            UserAccount reviewer) {
        Objects.requireNonNull(reviewer, "reviewer is required");
        validateReviewNote(submission.note());
        Long rawLeft = Objects.requireNonNull(submission.leftCandidateId(), "leftCandidateId is required");
        Long rawRight = Objects.requireNonNull(submission.rightCandidateId(), "rightCandidateId is required");
        if (rawLeft.equals(rawRight)) {
            throw new IllegalArgumentException("A candidate cannot be reviewed against itself");
        }
        Long leftId = Math.min(rawLeft, rawRight);
        Long rightId = Math.max(rawLeft, rawRight);

        NormalizedContentCandidate left = requireCandidate(leftId);
        NormalizedContentCandidate right = requireCandidate(rightId);
        requireCanonicalSameScopePair(candidateType, left, right);

        NormalizedCandidateMatchPair currentPair = pairRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId)
                .orElseThrow(() -> new NormalizedCandidatePairReviewConflictException(
                        "현재 분석된 pair가 없습니다. 새로고침 후 다시 시도하세요."));

        if (submission.expectedAssessment() != currentPair.getAssessment()
                || submission.expectedPairGeneratedAt() == null
                || !submission.expectedPairGeneratedAt().equals(currentPair.getGeneratedAt())) {
            throw new NormalizedCandidatePairReviewConflictException(
                    "분석 결과가 화면을 연 이후 변경되었습니다. 새로고침 후 다시 시도하세요.");
        }
        if (pairFreshness(currentPair, left, right) != PairFreshness.FRESH) {
            throw new NormalizedCandidatePairReviewConflictException(
                    "candidate가 재정규화되어 이 분석은 최신 상태가 아닙니다. 재분석 후 다시 시도하세요.");
        }

        Optional<NormalizedCandidatePairReview> existingOpt =
                reviewRepository.findByLeftCandidateIdAndRightCandidateId(leftId, rightId);
        HumanReviewDecision previousDecision = existingOpt.map(NormalizedCandidatePairReview::getDecision).orElse(null);
        Instant now = Instant.now();
        String note = submission.note();

        NormalizedCandidatePairReview review;
        boolean noOp;
        if (existingOpt.isPresent()) {
            NormalizedCandidatePairReview existing = existingOpt.get();
            if (submission.expectedReviewVersion() == null || existing.getVersion() != submission.expectedReviewVersion()) {
                throw new NormalizedCandidatePairReviewConflictException(
                        "다른 관리자가 이미 이 검토를 수정했습니다. 새로고침 후 다시 시도하세요.");
            }
            noOp = existing.getDecision() == submission.decision()
                    && Objects.equals(existing.getNote(), note)
                    && reviewFreshness(existing, currentPair, left, right) == ReviewFreshness.FRESH;
            if (!noOp) {
                existing.recordDecision(submission.decision(), reviewer, note, now, left.getNormalizedAt(),
                        right.getNormalizedAt(), currentPair.getAssessment());
                reviewRepository.save(existing);
            }
            review = existing;
        } else {
            review = new NormalizedCandidatePairReview(left, right, left.getNormalizedAt(), right.getNormalizedAt(),
                    currentPair.getAssessment(), submission.decision(), reviewer, note, now);
            reviewRepository.save(review);
            noOp = false;
        }

        if (!noOp) {
            historyRepository.save(new NormalizedCandidatePairReviewHistory(left, right, previousDecision,
                    submission.decision(), reviewer, note, now, left.getNormalizedAt(), right.getNormalizedAt(),
                    currentPair.getAssessment()));
        }

        return new CurrentReviewView(review.getDecision(), review.getReviewer().getDisplayName(), review.getNote(),
                review.getReviewedAt(), ReviewFreshness.FRESH, review.getVersion());
    }

    // ===================================================================================
    // Re-analysis
    // ===================================================================================

    /**
     * A thin, explicit admin action wrapping {@code NormalizedCandidateConflictAnalyzer.analyze} -
     * never triggered implicitly by a GET/list request (JLPT-MAX Ticket 4C). Never
     * touches any review/history row: reanalysis only ever deletes/reinserts Ticket 4B pair/evidence
     * rows, which this ticket's review tables have no foreign key to.
     */
    @Transactional
    public NormalizedCandidateAnalysisSummary reanalyze(NormalizedCandidateType candidateType, String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank()) {
            throw new IllegalArgumentException("재분석하려면 sourceRef가 필요합니다.");
        }
        return analyzer.analyze(candidateType, sourceRef);
    }

    // ===================================================================================
    // Shared helpers
    // ===================================================================================

    private NormalizedContentCandidate requireCandidate(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("candidate를 찾을 수 없습니다: " + id));
    }

    private void validateReviewNote(String note) {
        if (note != null && note.length() > MAX_REVIEW_NOTE_LENGTH) {
            throw new IllegalArgumentException("Review note must be 2000 characters or fewer");
        }
    }

    private void requireCanonicalSameScopePair(NormalizedCandidateType candidateType, NormalizedContentCandidate left,
            NormalizedContentCandidate right) {
        if (left.getId() >= right.getId()) {
            throw new IllegalArgumentException("leftCandidateId must be less than rightCandidateId");
        }
        if (left.getCandidateType() != candidateType || right.getCandidateType() != candidateType) {
            throw new IllegalArgumentException("candidateType이 두 candidate와 일치하지 않습니다.");
        }
        if (!Objects.equals(left.getSourceRef(), right.getSourceRef())) {
            throw new IllegalArgumentException("서로 다른 sourceRef의 candidate는 함께 검토할 수 없습니다.");
        }
    }

    /** {@code leftCandidate.normalizedAt <= pair.generatedAt AND rightCandidate.normalizedAt <= pair.generatedAt}. */
    private PairFreshness pairFreshness(NormalizedCandidateMatchPair pair, NormalizedContentCandidate left,
            NormalizedContentCandidate right) {
        boolean fresh = !left.getNormalizedAt().isAfter(pair.getGeneratedAt())
                && !right.getNormalizedAt().isAfter(pair.getGeneratedAt());
        return fresh ? PairFreshness.FRESH : PairFreshness.STALE;
    }

    /**
     * A recorded review is FRESH only while its stored snapshot still matches the current candidates'
     * {@code normalizedAt} and the current pair's assessment exactly, and a current pair still
     * exists at all - see {@link NormalizedCandidatePairReview}'s class javadoc for why this
     * comparison (not the pair's own freshness) is what actually determines review validity across a
     * reanalyze.
     */
    private ReviewFreshness reviewFreshness(NormalizedCandidatePairReview review, NormalizedCandidateMatchPair currentPair,
            NormalizedContentCandidate left, NormalizedContentCandidate right) {
        if (review == null) {
            return ReviewFreshness.NOT_REVIEWED;
        }
        if (currentPair == null) {
            return ReviewFreshness.ANALYSIS_NO_LONGER_PRESENT;
        }
        boolean matches = currentPair.getAssessment() == review.getAssessmentSnapshot()
                && left.getNormalizedAt().equals(review.getLeftNormalizedAtSnapshot())
                && right.getNormalizedAt().equals(review.getRightNormalizedAtSnapshot());
        return matches ? ReviewFreshness.FRESH : ReviewFreshness.STALE;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PairKey(Long leftCandidateId, Long rightCandidateId) {
    }
}
