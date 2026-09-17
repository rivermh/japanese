package com.japanese.content.repository;

import com.japanese.content.entity.ImportedSourceRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportedSourceRecordRepository extends JpaRepository<ImportedSourceRecord, Long> {

    Optional<ImportedSourceRecord> findBySourceRefAndNoteTypeAndSourceNoteId(
            String sourceRef, String noteType, long sourceNoteId);

    /**
     * JLPT-MAX Ticket 4D: batch existing-production-provenance check for a whole
     * {@code (sourceRef, noteType)} scope in one query - never called once per candidate (see
     * {@code NormalizedCandidatePromotionReadinessService}'s N+1 policy).
     */
    List<ImportedSourceRecord> findBySourceRefAndNoteTypeAndSourceNoteIdIn(
            String sourceRef, String noteType, Collection<Long> sourceNoteIds);

    Page<ImportedSourceRecord> findByNoteTypeOrderBySourceNoteId(String noteType, Pageable pageable);

    Page<ImportedSourceRecord> findByNoteTypeAndLevelCodeOrderBySourceNoteId(
            String noteType, String levelCode, Pageable pageable);

    Optional<ImportedSourceRecord> findByIdAndNoteType(Long id, String noteType);
    java.util.List<ImportedSourceRecord> findByContentItemIdOrderById(Long contentItemId);
}
