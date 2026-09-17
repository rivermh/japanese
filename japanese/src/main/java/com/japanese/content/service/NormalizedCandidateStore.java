package com.japanese.content.service;

import com.japanese.content.entity.NormalizedCandidateExtraField;
import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedCandidateWarning;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.NormalizedGrammarCandidateConfusablePattern;
import com.japanese.content.entity.NormalizedGrammarCandidateDetail;
import com.japanese.content.entity.NormalizedVocabularyCandidateDetail;
import com.japanese.content.entity.NormalizedVocabularyCandidateExample;
import com.japanese.content.entity.NormalizedVocabularyCandidateMeaning;
import com.japanese.content.importer.GrammarNormalizationResult;
import com.japanese.content.importer.GrammarNormalizationSeverity;
import com.japanese.content.importer.GrammarNormalizationWarning;
import com.japanese.content.importer.NormalizedConfusablePattern;
import com.japanese.content.importer.NormalizedExample;
import com.japanese.content.importer.NormalizedGrammarExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.NormalizedPitchAccent;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.importer.VocabularyNormalizationSeverity;
import com.japanese.content.importer.VocabularyNormalizationWarning;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministically maps a pure {@link VocabularyNormalizationResult}/
 * {@link GrammarNormalizationResult} into a persisted {@link NormalizedContentCandidate} snapshot.
 * Neither normalization parser is touched by this class - it only reads their already-computed
 * Result objects. Never creates, updates, or references any production entity
 * (ContentItem/Word/Grammar/GrammarEnrichment/GrammarRelation/GrammarComparison) or
 * ImportedSourceRecord; dedup, conflict resolution, review, and promotion are explicitly out of
 * scope here and belong to later tickets.
 *
 * <p>Re-saving the same {@code (sourceRef, sourceNoteId, candidateType)} refreshes the existing
 * envelope row in place: every child row for that candidate is deleted with an immediate bulk
 * delete (not entity-collection {@code clear()}, which would only be scheduled for Hibernate's
 * next flush and can otherwise race the fresh rows' own inserts into the same unique-per-order
 * constraint), followed by {@code flush()+clear()} to drop any now-stale managed state before the
 * candidate is reloaded and repopulated. A re-normalization never leaves stale child rows behind.
 */
@Service
public class NormalizedCandidateStore {

    private final NormalizedContentCandidateRepository repository;
    private final EntityManager entityManager;

    public NormalizedCandidateStore(NormalizedContentCandidateRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public NormalizedContentCandidate saveVocabulary(VocabularyNormalizationResult result) {
        NormalizedCandidateQualityState quality = vocabularyQualityState(result.warnings());
        Optional<NormalizedContentCandidate> existing = repository.findBySourceRefAndSourceNoteIdAndCandidateType(
                result.sourceRef(), result.sourceNoteId(), NormalizedCandidateType.VOCABULARY);

        NormalizedContentCandidate candidate;
        if (existing.isPresent()) {
            Long id = existing.get().getId();
            deleteVocabularyChildren(id);
            deleteSharedChildren(id);
            entityManager.flush();
            entityManager.clear();
            candidate = repository.findById(id).orElseThrow();
            candidate.refreshEnvelope(result.entryId(), quality, Instant.now());
        } else {
            candidate = new NormalizedContentCandidate(NormalizedCandidateType.VOCABULARY, result.sourceRef(),
                    result.sourceNoteId(), result.entryId(), quality, Instant.now());
        }

        NormalizedPitchAccent pitchAccent = result.pitchAccent();
        NormalizedJlptLevel level = result.level();
        candidate.attachVocabularyDetail(new NormalizedVocabularyCandidateDetail(
                result.entryId(),
                result.expression(),
                result.reading(),
                result.partOfSpeech(),
                pitchAccent == null ? null : pitchAccent.terminalStates(),
                pitchAccent == null ? null : pitchAccent.mora(),
                level == null ? null : level.code(),
                level == null ? null : level.rawValue(),
                level == null ? null : level.sourceField(),
                result.normalizedSearchExpression(),
                result.normalizedSearchReading()));

        for (NormalizedMeaning meaning : result.meanings()) {
            candidate.addVocabularyMeaning(
                    new NormalizedVocabularyCandidateMeaning(meaning.senseOrder(), meaning.text()));
        }
        for (NormalizedExample example : result.examples()) {
            candidate.addVocabularyExample(new NormalizedVocabularyCandidateExample(example.displayOrder(),
                    example.meaningLabel(), example.japaneseText(), example.reading(), example.translation()));
        }
        int position = 1;
        for (VocabularyNormalizationWarning warning : result.warnings()) {
            candidate.addWarning(new NormalizedCandidateWarning(position++, warning.issue().name(),
                    warning.severity().name(), warning.message()));
        }
        addExtraFields(candidate, result.preservedExtraFields());
        return repository.save(candidate);
    }

    @Transactional
    public NormalizedContentCandidate saveGrammar(GrammarNormalizationResult result) {
        NormalizedCandidateQualityState quality = grammarQualityState(result.warnings());
        Optional<NormalizedContentCandidate> existing = repository.findBySourceRefAndSourceNoteIdAndCandidateType(
                result.sourceRef(), result.sourceNoteId(), NormalizedCandidateType.GRAMMAR);

        NormalizedContentCandidate candidate;
        if (existing.isPresent()) {
            Long id = existing.get().getId();
            deleteGrammarChildren(id);
            deleteSharedChildren(id);
            entityManager.flush();
            entityManager.clear();
            candidate = repository.findById(id).orElseThrow();
            candidate.refreshEnvelope(result.unitId(), quality, Instant.now());
        } else {
            candidate = new NormalizedContentCandidate(NormalizedCandidateType.GRAMMAR, result.sourceRef(),
                    result.sourceNoteId(), result.unitId(), quality, Instant.now());
        }

        NormalizedGrammarExample frontExample = result.frontExample();
        NormalizedJlptLevel level = result.level();
        candidate.attachGrammarDetail(new NormalizedGrammarCandidateDetail(
                result.unitId(),
                result.pattern(),
                frontExample == null ? null : frontExample.displayOrder(),
                frontExample == null ? null : frontExample.japaneseText(),
                frontExample == null ? null : frontExample.reading(),
                frontExample == null ? null : frontExample.translation(),
                result.meaningGloss(),
                result.nuance(),
                result.connection(),
                level == null ? null : level.code(),
                level == null ? null : level.rawValue(),
                level == null ? null : level.sourceField(),
                result.rawKind()));

        for (NormalizedConfusablePattern pattern : result.confusablePatterns()) {
            candidate.addGrammarConfusablePattern(new NormalizedGrammarCandidateConfusablePattern(
                    pattern.displayOrder(), pattern.pattern(), pattern.explanation()));
        }
        int position = 1;
        for (GrammarNormalizationWarning warning : result.warnings()) {
            candidate.addWarning(new NormalizedCandidateWarning(position++, warning.issue().name(),
                    warning.severity().name(), warning.message()));
        }
        addExtraFields(candidate, result.unknownFields());
        return repository.save(candidate);
    }

    private void deleteVocabularyChildren(Long candidateId) {
        entityManager.createQuery(
                        "delete from NormalizedVocabularyCandidateDetail d where d.candidate.id = :id")
                .setParameter("id", candidateId).executeUpdate();
        entityManager.createQuery(
                        "delete from NormalizedVocabularyCandidateMeaning m where m.candidate.id = :id")
                .setParameter("id", candidateId).executeUpdate();
        entityManager.createQuery(
                        "delete from NormalizedVocabularyCandidateExample e where e.candidate.id = :id")
                .setParameter("id", candidateId).executeUpdate();
    }

    private void deleteGrammarChildren(Long candidateId) {
        entityManager.createQuery(
                        "delete from NormalizedGrammarCandidateDetail d where d.candidate.id = :id")
                .setParameter("id", candidateId).executeUpdate();
        entityManager.createQuery(
                        "delete from NormalizedGrammarCandidateConfusablePattern p where p.candidate.id = :id")
                .setParameter("id", candidateId).executeUpdate();
    }

    private void deleteSharedChildren(Long candidateId) {
        entityManager.createQuery("delete from NormalizedCandidateWarning w where w.candidate.id = :id")
                .setParameter("id", candidateId).executeUpdate();
        entityManager.createQuery("delete from NormalizedCandidateExtraField f where f.candidate.id = :id")
                .setParameter("id", candidateId).executeUpdate();
    }

    private void addExtraFields(NormalizedContentCandidate candidate, Map<String, String> extras) {
        for (Map.Entry<String, String> entry : extras.entrySet()) {
            candidate.addExtraField(new NormalizedCandidateExtraField(entry.getKey(), entry.getValue()));
        }
    }

    private NormalizedCandidateQualityState vocabularyQualityState(List<VocabularyNormalizationWarning> warnings) {
        return NormalizedCandidateQualityState.worstOf(
                warnings.stream().map(warning -> toQualityState(warning.severity())).toList());
    }

    private NormalizedCandidateQualityState grammarQualityState(List<GrammarNormalizationWarning> warnings) {
        return NormalizedCandidateQualityState.worstOf(
                warnings.stream().map(warning -> toQualityState(warning.severity())).toList());
    }

    private static NormalizedCandidateQualityState toQualityState(VocabularyNormalizationSeverity severity) {
        return switch (severity) {
            case FATAL -> NormalizedCandidateQualityState.FATAL;
            case REVIEW_REQUIRED -> NormalizedCandidateQualityState.REVIEW_REQUIRED;
            case INFORMATIONAL -> NormalizedCandidateQualityState.INFORMATIONAL;
        };
    }

    private static NormalizedCandidateQualityState toQualityState(GrammarNormalizationSeverity severity) {
        return switch (severity) {
            case FATAL -> NormalizedCandidateQualityState.FATAL;
            case REVIEW_REQUIRED -> NormalizedCandidateQualityState.REVIEW_REQUIRED;
            case INFORMATIONAL -> NormalizedCandidateQualityState.INFORMATIONAL;
        };
    }
}
