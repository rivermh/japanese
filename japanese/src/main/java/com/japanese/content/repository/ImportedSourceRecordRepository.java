package com.japanese.content.repository;

import com.japanese.content.entity.ImportedSourceRecord;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportedSourceRecordRepository extends JpaRepository<ImportedSourceRecord, Long> {

    Optional<ImportedSourceRecord> findBySourceRefAndNoteTypeAndSourceNoteId(
            String sourceRef, String noteType, long sourceNoteId);

    /**
     * JLPT-MAX Ticket 4E-0: promotion-time {@code PESSIMISTIC_WRITE} row lock over the exact
     * {@code (sourceRef, noteType, sourceNoteId)} identity, serializing two transactions that would
     * otherwise both read the same existing-but-unlinked record and each create their own
     * {@code ContentItem} for it. Does not, by itself, guard the case where no row exists yet for
     * that identity (see {@code NormalizedCandidatePromotionReadinessService}'s Ticket 4E-0 javadoc) -
     * only a future promotion write transaction (Ticket 4E-1) is meant to call this.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ImportedSourceRecord r where r.sourceRef = :sourceRef and r.noteType = :noteType "
            + "and r.sourceNoteId = :sourceNoteId")
    Optional<ImportedSourceRecord> findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion(
            @Param("sourceRef") String sourceRef, @Param("noteType") String noteType,
            @Param("sourceNoteId") long sourceNoteId);

    /**
     * JLPT-MAX Ticket 4E-3B: the same {@code PESSIMISTIC_WRITE} identity lock as
     * {@link #findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion}, kept as its own dedicated method
     * (not reused) so this table's lock methods stay self-documenting about which write path actually
     * holds the lock at any given time - {@code NormalizedCandidateGroupPromotionService} calls this
     * once per group member, in deterministic member-candidate-id order, after every candidate/review
     * lock in that same transaction is already held.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ImportedSourceRecord r where r.sourceRef = :sourceRef and r.noteType = :noteType "
            + "and r.sourceNoteId = :sourceNoteId")
    Optional<ImportedSourceRecord> findBySourceRefAndNoteTypeAndSourceNoteIdForGroupPromotion(
            @Param("sourceRef") String sourceRef, @Param("noteType") String noteType,
            @Param("sourceNoteId") long sourceNoteId);

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
