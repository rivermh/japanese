package com.japanese.content.service;

import com.japanese.content.dto.PromotionReadinessModels.OverallStatus;
import com.japanese.content.dto.PromotionReadinessModels.PromotionReadinessResult;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Example;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.NormalizedVocabularyCandidateDetail;
import com.japanese.content.entity.NormalizedVocabularyCandidateExample;
import com.japanese.content.entity.NormalizedVocabularyCandidateMeaning;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import com.japanese.content.service.PrivateApkgNoteProvenanceReader.RawProvenance;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4E-1: the smallest safe production WRITE path that promotes exactly one
 * {@code READY_FOR_DRAFT_PROMOTION} Vocabulary {@link NormalizedContentCandidate} into a production
 * draft ({@code ContentItem}/{@code Word}/{@code Meaning}/{@code Example}, {@code published = false}).
 *
 * <p><b>Grammar is out of scope</b>: {@link #promote} rejects any non-{@code VOCABULARY} candidate
 * before touching any repository - Grammar candidates are promoted separately by
 * {@code NormalizedGrammarCandidatePromotionService} (Ticket 4E-8), never by this class.
 *
 * <p><b>One authoritative readiness policy</b>: this class never duplicates any promotion-readiness
 * business rule. It delegates the actual pass/fail decision entirely to
 * {@link NormalizedCandidatePromotionReadinessService#detail}, called from <em>inside</em> this
 * class's own write transaction, after the candidate row is already locked below - so the decision is
 * always made against the current, locked state, never a stale GET-time result the caller might have
 * cached. (Spring's default {@code REQUIRED} propagation means that inner {@code readOnly = true}
 * call simply joins this already-open read-write transaction; per-call detail: the persistence
 * context's identity map guarantees {@code readiness.detail(...)}'s own
 * {@code candidateRepository.findById(...)} returns the exact same managed, already-locked
 * {@code NormalizedContentCandidate} instance this method loaded below, rather than issuing a second,
 * unlocked read.)
 *
 * <p><b>Transaction/lock contract</b> (Ticket 4E-1 hardening, revised from the original contract in
 * {@code NormalizedCandidatePromotionReadinessService}'s class javadoc to add the freshness check at
 * step 2):
 * <ol>
 *   <li>Lock the candidate row ({@code findByIdAndCandidateTypeForPromotion}) - always first, so two
 *       admins concurrently promoting the <em>same</em> candidate are fully serialized by this single
 *       lock before either can reach any later step.</li>
 *   <li>Compare the caller-supplied {@code expectedNormalizedAt} against the now-locked candidate's
 *       current {@code normalizedAt} - reject as stale (zero writes) on mismatch, <em>before</em>
 *       readiness is even recomputed. This proves "the admin reviewed this exact revision"; step 3
 *       below separately proves "this revision is valid right now" - neither check substitutes for
 *       the other. See this class's "Freshness" section below for why {@code normalizedAt} alone is
 *       sufficient and no separate pair/review freshness token is needed.</li>
 *   <li>Recompute readiness in this same transaction against that locked candidate.</li>
 *   <li>Reject unless the recomputed status is exactly {@code READY_FOR_DRAFT_PROMOTION} - this also
 *       covers {@code ALREADY_PROMOTED} (an already-promoted candidate is never re-promoted) and every
 *       {@code BLOCKED} reason (normalization/pair/mapping/rights/identity) uniformly, with zero
 *       business-rule duplication.</li>
 *   <li>Lock any existing {@code ImportedSourceRecord} row for this exact
 *       {@code (sourceRef, noteType, sourceNoteId)} identity
 *       ({@code findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion}) - acquired only after the
 *       candidate lock, a fixed order this class always follows. If no such row exists yet, the
 *       candidate lock (already held) plus this table's {@code uk_imported_source_record} unique
 *       constraint plus this whole transaction's atomic commit-or-rollback is the defense for that
 *       race (a lock cannot protect a row that does not exist) - see the readiness service javadoc.</li>
 *   <li>Re-check {@code ALREADY_PROMOTED} against that locked row's current
 *       {@code contentItem} link.</li>
 *   <li>If no {@code ImportedSourceRecord} row exists yet, look up the matching
 *       {@code private_apkg_notes} row for truthful provenance (see "Provenance" below) - reject
 *       (zero writes) if none exists, rather than fabricate one.</li>
 *   <li>Only then create the production {@code ContentItem}/{@code Word}/{@code Meaning}/
 *       {@code Example}/{@code Level} link.</li>
 *   <li>Link (or create, using the truthful provenance from the step above) provenance in the same
 *       transaction.</li>
 *   <li>Commit - or roll back the whole transaction on any failure (including a unique-constraint
 *       violation surfaced at flush), leaving no partial production/provenance row.</li>
 * </ol>
 *
 * <p><b>Freshness</b>: {@code expectedNormalizedAt} is compared against
 * {@code NormalizedContentCandidate.normalizedAt} - the same field
 * {@code NormalizedCandidatePromotionReadinessService}'s own {@code PAIR_ANALYSIS_STALE}/
 * {@code PAIR_REVIEW_STALE} axes already treat as this candidate's authoritative revision marker
 * ({@code NormalizedCandidateStore}'s own class javadoc: re-normalizing the same note replaces this
 * row's scalar fields and every child collection in place, and bumps {@code normalizedAt}), so no new
 * versioning concept is introduced. A separate pair/review freshness token (the
 * {@code expectedPairGeneratedAt}/{@code expectedAssessment}/{@code expectedReviewVersion} triple
 * {@code AdminNormalizedCandidateReviewController.submitDecision} uses) is deliberately NOT duplicated
 * here: step 3's readiness recomputation already re-derives {@code PAIR_UNREVIEWED}/
 * {@code PAIR_REVIEW_STALE}/{@code PAIR_NEEDS_FOLLOWUP}/{@code SAME_CONTENT_CANONICAL_SELECTION_REQUIRED}
 * fresh from the database on every call (never cached from the admin's GET render), so any pair/review
 * state change between GET and POST - even one that leaves {@code normalizedAt} itself unchanged - is
 * already caught by that recomputation turning {@code overallStatus} away from
 * {@code READY_FOR_DRAFT_PROMOTION}.
 *
 * <p><b>Production mapping</b> mirrors the one existing production Vocabulary writer
 * ({@code ApkgVocabularyImporter}) field-for-field: {@code expression}/{@code reading}/
 * {@code partOfSpeech} verbatim, pitch accent via the shared
 * {@code NormalizedCandidatePromotionReadinessService.pitchAccentPreview} serialization,
 * {@code Meaning.languageTag} exclusively via {@link NormalizedVocabularyMeaningLanguagePolicy} (never
 * a hardcoded {@code "ko"} fallback here), and examples linked to their meaning by
 * {@code meaningLabel} exactly like {@code ApkgVocabularyImporter.attachExamples}. The slug is
 * generated exactly once, via {@link ProductionContentSlugPolicy#generateSlug}, only after every
 * rejection check above has already passed - so a rejected promotion never mints an identity.
 *
 * <p><b>Draft state</b>: every created {@code ContentItem} is {@code published = false} with the
 * entity's own default initial {@code ReviewStatus.PENDING} (see {@code ContentItem}'s constructor) -
 * exactly {@code ApkgVocabularyImporter}'s own convention for a freshly-created item. No
 * {@code ContentReviewHistory} row is written for this initial creation: across this codebase (see
 * {@code ContentReviewService}/{@code ContentPublicationService}/{@code AdminContentReviewService}),
 * that table is only ever written on a review <em>transition</em> (approve/reject/etc.), never on
 * initial {@code PENDING} creation - {@code ApkgVocabularyImporter} itself never writes one either.
 *
 * <p><b>Provenance ({@code ImportedSourceRecord})</b> - revised for Ticket 4E-1 hardening (MAJOR 1):
 * {@code sourceRef}/{@code noteType}/{@code sourceNoteId} are copied verbatim from the candidate's own
 * provenance pointer - {@code noteType} uses the exact same {@code "JLPT MAX덱 어휘"} literal
 * {@code NormalizedCandidatePromotionReadinessService} and {@code ApkgVocabularyImporter} both already
 * use for this identity. When no {@code ImportedSourceRecord} row exists yet for this identity, this
 * class now reads the matching {@code private_apkg_notes} row (via
 * {@link PrivateApkgNoteProvenanceReader}, the exact same {@code (sourceRef, sourceNoteId)} identity a
 * candidate already carries as its own provenance pointer, and the same unique key that staging table
 * already enforces) and uses ITS genuine raw {@code tags}/{@code field_names}/{@code field_values} -
 * never a normalized-candidate-derived synthetic substitute. {@code field_names}/{@code field_values}
 * are re-encoded from that table's JSON-array storage format into the U+001F-joined form every other
 * reader/writer of this column already expects, via {@code RawProvenance.joinedFieldNames()}/
 * {@code joinedFieldValues()} (Ticket 4E-8 hardening, MAJOR 1: this class and
 * {@code NormalizedGrammarCandidatePromotionService} now share that single encoding implementation, so
 * the two can never drift onto two different delimiter conventions - see
 * {@link PrivateApkgNoteProvenanceReader}'s own javadoc for why this re-encoding does not change the
 * truthfulness of the data, only its delimiter). If no matching {@code private_apkg_notes} row exists
 * (that staging row is independent of a candidate and may have been reprocessed/removed - see
 * {@code NormalizedContentCandidate}'s own class javadoc), this class REJECTS the promotion (zero
 * writes) rather than fabricate a substitute payload - see {@link #promote} for the exact message.
 * When a row already exists (created by some other pipeline stage) but is not yet linked to a
 * production {@code ContentItem}, it is reused/linked in place exactly as before - untouched otherwise
 * - avoiding {@code uk_imported_source_record}.
 */
@Service
public class NormalizedVocabularyCandidatePromotionService {

    /** Matches {@code NormalizedCandidatePromotionReadinessService.VOCABULARY_NOTE_TYPE} - see that class's javadoc. */
    private static final String VOCABULARY_NOTE_TYPE = "JLPT MAX덱 어휘";
    private static final String JLPT_LEVEL_SYSTEM = "JLPT";

    private final NormalizedContentCandidateRepository candidateRepository;
    private final ImportedSourceRecordRepository importedSourceRecordRepository;
    private final ContentItemRepository contentItemRepository;
    private final LevelRepository levelRepository;
    private final NormalizedCandidatePromotionReadinessService readiness;
    private final ProductionContentSlugPolicy slugPolicy;
    private final NormalizedVocabularyMeaningLanguagePolicy meaningLanguagePolicy;
    private final PrivateApkgNoteProvenanceReader provenanceReader;

    public NormalizedVocabularyCandidatePromotionService(
            NormalizedContentCandidateRepository candidateRepository,
            ImportedSourceRecordRepository importedSourceRecordRepository,
            ContentItemRepository contentItemRepository,
            LevelRepository levelRepository,
            NormalizedCandidatePromotionReadinessService readiness,
            ProductionContentSlugPolicy slugPolicy,
            NormalizedVocabularyMeaningLanguagePolicy meaningLanguagePolicy,
            PrivateApkgNoteProvenanceReader provenanceReader) {
        this.candidateRepository = candidateRepository;
        this.importedSourceRecordRepository = importedSourceRecordRepository;
        this.contentItemRepository = contentItemRepository;
        this.levelRepository = levelRepository;
        this.readiness = readiness;
        this.slugPolicy = slugPolicy;
        this.meaningLanguagePolicy = meaningLanguagePolicy;
        this.provenanceReader = provenanceReader;
    }

    /**
     * Promotes exactly one Vocabulary candidate to a production draft. See this class's javadoc for
     * the full transaction/lock/mapping contract.
     *
     * @param expectedNormalizedAt the candidate's {@code normalizedAt} as rendered on the admin's GET
     *        detail page - compared against the locked candidate's current value before anything else
     *        is checked (see "Freshness" in this class's javadoc)
     * @throws NoSuchElementException if no candidate exists for {@code candidateId} (of any type)
     * @throws NormalizedCandidatePromotionStaleException if {@code expectedNormalizedAt} no longer
     *         matches the locked candidate's current {@code normalizedAt}
     * @throws NormalizedCandidatePromotionRejectedException if {@code candidateType} is not
     *         {@code VOCABULARY}, the candidate is not currently (re-checked, inside this
     *         transaction) exactly {@code READY_FOR_DRAFT_PROMOTION}, is already linked to a
     *         production {@code ContentItem}, or no truthful provenance is available to link/create
     */
    @Transactional
    public NormalizedVocabularyCandidatePromotionResult promote(NormalizedCandidateType candidateType,
            Long candidateId, Instant expectedNormalizedAt) {
        if (candidateType != NormalizedCandidateType.VOCABULARY) {
            throw new NormalizedCandidatePromotionRejectedException(
                    "Ticket 4E-1은 VOCABULARY candidate만 승격합니다 (candidateType=" + candidateType + ").");
        }

        // Step 1: lock the candidate row first - see class javadoc for why this fixed ordering matters.
        NormalizedContentCandidate candidate = candidateRepository
                .findByIdAndCandidateTypeForPromotion(candidateId, NormalizedCandidateType.VOCABULARY)
                .orElseThrow(() -> new NoSuchElementException("candidate를 찾을 수 없습니다: " + candidateId));

        // Step 2: freshness check - BEFORE readiness is recomputed. Proves "the admin reviewed this
        // exact revision", which a fresh READY status alone can never prove (a different, also-READY
        // revision could have replaced it between GET and POST) - see class javadoc "Freshness".
        if (!candidate.getNormalizedAt().equals(expectedNormalizedAt)) {
            throw new NormalizedCandidatePromotionStaleException(
                    "candidate가 화면을 읽은 이후 재정규화되었습니다 (expectedNormalizedAt=" + expectedNormalizedAt
                            + ", currentNormalizedAt=" + candidate.getNormalizedAt()
                            + ") - 새로고침 후 다시 확인해주세요.");
        }

        // Step 3-4: recompute readiness against the now-locked candidate; reject unless exactly READY.
        PromotionReadinessResult result = readiness.detail(NormalizedCandidateType.VOCABULARY, candidateId).result();
        if (result.overallStatus() != OverallStatus.READY_FOR_DRAFT_PROMOTION) {
            throw new NormalizedCandidatePromotionRejectedException(
                    "candidate가 현재 READY_FOR_DRAFT_PROMOTION 상태가 아닙니다 (overallStatus=" + result.overallStatus()
                            + ", issues=" + result.issues() + ").");
        }

        String sourceRef = candidate.getSourceRef();
        long sourceNoteId = candidate.getSourceNoteId();

        // Step 5-6: lock any existing provenance row for this exact identity (candidate lock already
        // held); re-check ALREADY_PROMOTED against its current, now-locked link state.
        Optional<ImportedSourceRecord> existingRecord = importedSourceRecordRepository
                .findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion(sourceRef, VOCABULARY_NOTE_TYPE, sourceNoteId);
        if (existingRecord.isPresent() && existingRecord.get().getContentItem() != null) {
            throw new NormalizedCandidatePromotionRejectedException(
                    "candidate는 이미 production ContentItem에 연결되어 있습니다 (already promoted).");
        }

        // Step 7: only needed when a new provenance row must be created (an existing row, linked or
        // not, is never touched here - see class javadoc "Provenance"). Rejects rather than fabricates
        // a substitute when no truthful raw source payload is available.
        RawProvenance rawProvenance = existingRecord.isEmpty()
                ? provenanceReader.find(sourceRef, sourceNoteId)
                        .orElseThrow(() -> new NormalizedCandidatePromotionRejectedException(
                                "private_apkg_notes에서 candidate의 원본 provenance(raw Anki 데이터)를 찾을 수 없습니다 "
                                        + "(sourceRef=" + sourceRef + ", sourceNoteId=" + sourceNoteId + ") - "
                                        + "정규화된 candidate 필드로 대체하지 않고 승격을 차단합니다."))
                : null;

        NormalizedVocabularyCandidateDetail vocab = candidate.getVocabularyDetail();

        // Resolved by readiness above (JLPT_LEVEL_UNMAPPABLE would otherwise have blocked this
        // candidate) - never auto-created here; a genuinely missing/duplicate row is a defensive
        // fallback that should be unreachable given the readiness check just passed.
        Level level = levelRepository.findBySystemAndCode(JLPT_LEVEL_SYSTEM, vocab.getLevelCode())
                .orElseThrow(() -> new NormalizedCandidatePromotionRejectedException(
                        "production Level을 찾을 수 없습니다 (system=JLPT, code=" + vocab.getLevelCode() + ")."));

        // Resolved by readiness above (VOCABULARY_MEANING_LANGUAGE_POLICY_UNRESOLVED would otherwise
        // have blocked this candidate) - never a hardcoded "ko" fallback in this class.
        String languageTag = meaningLanguagePolicy.resolveLanguageTag(sourceRef)
                .orElseThrow(() -> new NormalizedCandidatePromotionRejectedException(
                        "meaning language policy를 해석할 수 없습니다 (sourceRef=" + sourceRef + ")."));

        // Step: slug generated exactly once, only now that readiness has passed.
        String slug = slugPolicy.generateSlug(NormalizedCandidateType.VOCABULARY);

        Word word = new Word(vocab.getExpression(), vocab.getReading(), vocab.getPartOfSpeech(),
                NormalizedCandidatePromotionReadinessService.pitchAccentPreview(vocab));

        List<NormalizedVocabularyCandidateMeaning> meanings = candidate.getVocabularyMeanings();
        Map<String, Meaning> meaningByText = new LinkedHashMap<>();
        for (NormalizedVocabularyCandidateMeaning meaning : meanings) {
            Meaning produced = new Meaning(languageTag, meaning.getMeaningText(), meaning.getSenseOrder());
            word.addMeaning(produced);
            meaningByText.putIfAbsent(meaning.getMeaningText(), produced);
        }

        ContentItem item = new ContentItem(slug, ContentType.WORD, sourceRef, false);
        item.attachWord(word);
        item.addLevel(level);
        for (NormalizedVocabularyCandidateExample example : candidate.getVocabularyExamples()) {
            Example produced = new Example(example.getJapaneseText(), example.getReading(), example.getTranslation(),
                    example.getDisplayOrder());
            Meaning matched = meaningByText.get(example.getMeaningLabel());
            if (matched != null) {
                produced.setMeaning(matched);
            }
            item.addExample(produced);
        }

        // Step 8: create production entities (cascades Word/Meaning/Example via ContentItem/Word).
        ContentItem savedItem = contentItemRepository.save(item);

        // Step 9: link (existing row, untouched otherwise) or create (using the truthful raw
        // provenance resolved in step 7) provenance in the same transaction.
        ImportedSourceRecord record = existingRecord.orElseGet(() -> new ImportedSourceRecord(
                sourceRef, VOCABULARY_NOTE_TYPE, sourceNoteId, vocab.getLevelCode(), rawProvenance.tags(),
                rawProvenance.joinedFieldNames(), rawProvenance.joinedFieldValues()));
        record.linkContentItem(savedItem);
        importedSourceRecordRepository.save(record);

        return new NormalizedVocabularyCandidatePromotionResult(savedItem.getId(), slug);
    }
}
