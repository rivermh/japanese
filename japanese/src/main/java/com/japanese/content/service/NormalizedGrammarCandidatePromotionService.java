package com.japanese.content.service;

import com.japanese.content.dto.PromotionReadinessModels.OverallStatus;
import com.japanese.content.dto.PromotionReadinessModels.PromotionReadinessResult;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Example;
import com.japanese.content.entity.Grammar;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.NormalizedGrammarCandidateDetail;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import com.japanese.content.service.PrivateApkgNoteProvenanceReader.RawProvenance;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4E-8: the smallest safe production WRITE path that promotes exactly one
 * {@code READY_FOR_DRAFT_PROMOTION} Grammar {@link NormalizedContentCandidate} into a production
 * draft ({@code ContentItem}/{@code Grammar}/exactly one {@code Example}, {@code published = false}).
 *
 * <p>Mirrors {@link NormalizedVocabularyCandidatePromotionService}'s (Ticket 4E-1) full transaction/
 * lock/freshness/provenance contract exactly - see that class's javadoc for the shared rationale this
 * class does not repeat. The one structural difference: Grammar has no {@code Meaning} entity, so
 * there is no meaning-language-policy step here.
 *
 * <p><b>Vocabulary is out of scope</b>: {@link #promote} rejects any non-{@code GRAMMAR} candidate
 * before touching any repository - Vocabulary candidates are promoted separately by
 * {@code NormalizedVocabularyCandidatePromotionService}, never by this class.
 *
 * <p><b>Production mapping (Ticket 4E-8, ratified)</b>: {@code Grammar.pattern} is the candidate's
 * {@code pattern} verbatim; {@code Grammar.connection} is the candidate's {@code connectionForm}
 * verbatim (null allowed); {@code Grammar.explanation} is composed by the single shared
 * {@link NormalizedCandidatePromotionReadinessService#composeGrammarExplanation} helper (also used by
 * that class's own {@code GRAMMAR_EXPLANATION_TOO_LONG} readiness check, so the two can never drift).
 * Exactly one {@code Example} is created from {@code frontExample} ({@code text} =
 * {@code frontExample.japaneseText}, {@code translation} = {@code frontExample.translation},
 * {@code reading} = {@code frontExample.reading} verbatim, or {@code null} if the normalized source
 * has none (Ticket 4E-8 hardening, MAJOR 2) - never fabricated. Real v2.1.1 data never has furigana
 * here today, see {@code GrammarNormalizationResult}'s own javadoc, but this preserves whatever a
 * future source provides). {@code confusablePatterns} is never resolved
 * into a {@code GrammarRelation}/{@code GrammarComparison} - doing so would require inferring another
 * production Grammar's identity from free text, which this ticket deliberately does not attempt; no
 * such row is ever created here.
 *
 * <p><b>Provenance encoding (Ticket 4E-8 hardening, MAJOR 1)</b>: when a new {@code ImportedSourceRecord}
 * must be created, its {@code field_names}/{@code field_values} are re-encoded from
 * {@code private_apkg_notes}'s JSON-array storage format into the U+001F-joined form every other
 * reader/writer of this column already expects, via the single shared
 * {@code RawProvenance.joinedFieldNames()}/{@code joinedFieldValues()} implementation also used by
 * {@link NormalizedVocabularyCandidatePromotionService} - the two classes can never drift onto two
 * different delimiter conventions.
 */
@Service
public class NormalizedGrammarCandidatePromotionService {

    /** Matches {@code NormalizedCandidatePromotionReadinessService.GRAMMAR_NOTE_TYPE} - see that class's javadoc. */
    private static final String GRAMMAR_NOTE_TYPE = "JLPT MAX덱 문법";
    private static final String JLPT_LEVEL_SYSTEM = "JLPT";

    private final NormalizedContentCandidateRepository candidateRepository;
    private final ImportedSourceRecordRepository importedSourceRecordRepository;
    private final ContentItemRepository contentItemRepository;
    private final LevelRepository levelRepository;
    private final NormalizedCandidatePromotionReadinessService readiness;
    private final ProductionContentSlugPolicy slugPolicy;
    private final PrivateApkgNoteProvenanceReader provenanceReader;

    public NormalizedGrammarCandidatePromotionService(
            NormalizedContentCandidateRepository candidateRepository,
            ImportedSourceRecordRepository importedSourceRecordRepository,
            ContentItemRepository contentItemRepository,
            LevelRepository levelRepository,
            NormalizedCandidatePromotionReadinessService readiness,
            ProductionContentSlugPolicy slugPolicy,
            PrivateApkgNoteProvenanceReader provenanceReader) {
        this.candidateRepository = candidateRepository;
        this.importedSourceRecordRepository = importedSourceRecordRepository;
        this.contentItemRepository = contentItemRepository;
        this.levelRepository = levelRepository;
        this.readiness = readiness;
        this.slugPolicy = slugPolicy;
        this.provenanceReader = provenanceReader;
    }

    /**
     * Promotes exactly one Grammar candidate to a production draft. See this class's javadoc, and
     * {@link NormalizedVocabularyCandidatePromotionService}'s javadoc, for the full transaction/lock/
     * mapping contract this mirrors.
     *
     * @param expectedNormalizedAt the candidate's {@code normalizedAt} as rendered on the admin's GET
     *        detail page - compared against the locked candidate's current value before anything else
     *        is checked
     * @throws NoSuchElementException if no candidate exists for {@code candidateId} (of any type)
     * @throws NormalizedCandidatePromotionStaleException if {@code expectedNormalizedAt} no longer
     *         matches the locked candidate's current {@code normalizedAt}
     * @throws NormalizedCandidatePromotionRejectedException if {@code candidateType} is not
     *         {@code GRAMMAR}, the candidate is not currently (re-checked, inside this transaction)
     *         exactly {@code READY_FOR_DRAFT_PROMOTION}, is already linked to a production
     *         {@code ContentItem}, or no truthful provenance is available to link/create
     */
    @Transactional
    public NormalizedGrammarCandidatePromotionResult promote(NormalizedCandidateType candidateType,
            Long candidateId, Instant expectedNormalizedAt) {
        if (candidateType != NormalizedCandidateType.GRAMMAR) {
            throw new NormalizedCandidatePromotionRejectedException(
                    "Ticket 4E-8은 GRAMMAR candidate만 승격합니다 (candidateType=" + candidateType + ").");
        }

        NormalizedContentCandidate candidate = candidateRepository
                .findByIdAndCandidateTypeForPromotion(candidateId, NormalizedCandidateType.GRAMMAR)
                .orElseThrow(() -> new NoSuchElementException("candidate를 찾을 수 없습니다: " + candidateId));

        if (!candidate.getNormalizedAt().equals(expectedNormalizedAt)) {
            throw new NormalizedCandidatePromotionStaleException(
                    "candidate가 화면을 읽은 이후 재정규화되었습니다 (expectedNormalizedAt=" + expectedNormalizedAt
                            + ", currentNormalizedAt=" + candidate.getNormalizedAt()
                            + ") - 새로고침 후 다시 확인해주세요.");
        }

        PromotionReadinessResult result = readiness.detail(NormalizedCandidateType.GRAMMAR, candidateId).result();
        if (result.overallStatus() != OverallStatus.READY_FOR_DRAFT_PROMOTION) {
            throw new NormalizedCandidatePromotionRejectedException(
                    "candidate가 현재 READY_FOR_DRAFT_PROMOTION 상태가 아닙니다 (overallStatus=" + result.overallStatus()
                            + ", issues=" + result.issues() + ").");
        }

        String sourceRef = candidate.getSourceRef();
        long sourceNoteId = candidate.getSourceNoteId();

        Optional<ImportedSourceRecord> existingRecord = importedSourceRecordRepository
                .findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion(sourceRef, GRAMMAR_NOTE_TYPE, sourceNoteId);
        if (existingRecord.isPresent() && existingRecord.get().getContentItem() != null) {
            throw new NormalizedCandidatePromotionRejectedException(
                    "candidate는 이미 production ContentItem에 연결되어 있습니다 (already promoted).");
        }

        RawProvenance rawProvenance = existingRecord.isEmpty()
                ? provenanceReader.find(sourceRef, sourceNoteId)
                        .orElseThrow(() -> new NormalizedCandidatePromotionRejectedException(
                                "private_apkg_notes에서 candidate의 원본 provenance(raw Anki 데이터)를 찾을 수 없습니다 "
                                        + "(sourceRef=" + sourceRef + ", sourceNoteId=" + sourceNoteId + ") - "
                                        + "정규화된 candidate 필드로 대체하지 않고 승격을 차단합니다."))
                : null;

        NormalizedGrammarCandidateDetail grammar = candidate.getGrammarDetail();

        // Resolved by readiness above (JLPT_LEVEL_UNMAPPABLE would otherwise have blocked this
        // candidate) - never auto-created here.
        Level level = levelRepository.findBySystemAndCode(JLPT_LEVEL_SYSTEM, grammar.getLevelCode())
                .orElseThrow(() -> new NormalizedCandidatePromotionRejectedException(
                        "production Level을 찾을 수 없습니다 (system=JLPT, code=" + grammar.getLevelCode() + ")."));

        String slug = slugPolicy.generateSlug(NormalizedCandidateType.GRAMMAR);

        Grammar production = new Grammar(grammar.getPattern(),
                NormalizedCandidatePromotionReadinessService.composeGrammarExplanation(grammar),
                grammar.getConnectionForm());

        ContentItem item = new ContentItem(slug, ContentType.GRAMMAR, sourceRef, false);
        item.attachGrammar(production);
        item.addLevel(level);
        item.addExample(new Example(grammar.getFrontExampleJapaneseText(), grammar.getFrontExampleReading(),
                grammar.getFrontExampleTranslation(), 1));

        ContentItem savedItem = contentItemRepository.save(item);

        ImportedSourceRecord record = existingRecord.orElseGet(() -> new ImportedSourceRecord(
                sourceRef, GRAMMAR_NOTE_TYPE, sourceNoteId, grammar.getLevelCode(), rawProvenance.tags(),
                rawProvenance.joinedFieldNames(), rawProvenance.joinedFieldValues()));
        record.linkContentItem(savedItem);
        importedSourceRecordRepository.save(record);

        return new NormalizedGrammarCandidatePromotionResult(savedItem.getId(), slug);
    }
}
