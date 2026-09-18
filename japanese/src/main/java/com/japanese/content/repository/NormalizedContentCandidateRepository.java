package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedContentCandidateRepository extends JpaRepository<NormalizedContentCandidate, Long> {
    Optional<NormalizedContentCandidate> findBySourceRefAndSourceNoteIdAndCandidateType(
            String sourceRef, long sourceNoteId, NormalizedCandidateType candidateType);

    List<NormalizedContentCandidate> findByCandidateType(NormalizedCandidateType candidateType);

    List<NormalizedContentCandidate> findByCandidateTypeAndSourceRef(NormalizedCandidateType candidateType,
            String sourceRef);

    /**
     * JLPT-MAX Ticket 4D: the batch candidate load for a whole promotion-readiness scope - fetch-joins
     * both to-one detail associations (only the one matching {@code candidateType} is ever non-null)
     * so scanning up to ~10k candidates never triggers one lazy-load per candidate. {@code sourceRef}
     * is optional - {@code null} matches every source ref for the given type, mirroring
     * {@code NormalizedCandidateMatchPairRepository.findVocabularyPairsForReview}. Vocabulary
     * meanings/examples (List associations, which cannot be fetch-joined together with each other or
     * with a to-one association in the same query without risking a Cartesian/{@code MultipleBagFetch}
     * problem) are batch-loaded separately by the service, keyed by candidate id.
     */
    @Query("select distinct c from NormalizedContentCandidate c "
            + "left join fetch c.vocabularyDetail left join fetch c.grammarDetail "
            + "where c.candidateType = :candidateType and (:sourceRef is null or c.sourceRef = :sourceRef)")
    List<NormalizedContentCandidate> findByCandidateTypeAndSourceRefWithDetailForReadiness(
            @Param("candidateType") NormalizedCandidateType candidateType, @Param("sourceRef") String sourceRef);

    /**
     * JLPT-MAX Ticket 4E-0: promotion-time {@code PESSIMISTIC_WRITE} row lock, serializing two admins
     * who concurrently attempt to promote the same candidate. Only a future promotion write
     * transaction (Ticket 4E-1) is meant to call this - every read-only readiness query above
     * deliberately never does.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NormalizedContentCandidate c where c.id = :id and c.candidateType = :candidateType")
    Optional<NormalizedContentCandidate> findByIdAndCandidateTypeForPromotion(
            @Param("id") Long id, @Param("candidateType") NormalizedCandidateType candidateType);

    /**
     * JLPT-MAX Ticket 4E-3A: the deterministic ascending-id candidate-id discovery query
     * {@code NormalizedCandidateConflictAnalyzer.analyze} now uses to decide, up front and without
     * locking, WHICH candidate rows it must then lock one-by-one (in this exact order) before mutating
     * any pair row for the same {@code (candidateType, sourceRef)} scope - see that method's own
     * javadoc for the full cross-service lock-ordering invariant this supports. Deliberately returns
     * only ids (not entities): loading full, unlocked entity instances here would populate the
     * persistence context with stale-by-the-time-they-are-used state, which the analyzer must instead
     * obtain fresh, one at a time, via {@link #findByIdForConflictAnalysis}.
     */
    @Query("select c.id from NormalizedContentCandidate c "
            + "where c.candidateType = :candidateType and c.sourceRef = :sourceRef order by c.id asc")
    List<Long> findIdsByCandidateTypeAndSourceRefOrderByIdAsc(
            @Param("candidateType") NormalizedCandidateType candidateType, @Param("sourceRef") String sourceRef);

    /**
     * JLPT-MAX Ticket 4E-3A: single-row {@code PESSIMISTIC_WRITE} lock used only by
     * {@code NormalizedCandidateConflictAnalyzer.analyze} (never by any read-only readiness query),
     * acquired once per id from {@link #findIdsByCandidateTypeAndSourceRefOrderByIdAsc}'s result, in
     * that exact ascending order, before any pair-row mutation in the same scope. Deliberately a
     * separate method from {@link #findByIdAndCandidateTypeForPromotion} - despite locking the same way
     * - because that method's name and javadoc are specific to Ticket 4E-1/4E-3A promotion-style
     * single-candidate writes; reusing it here for Ticket 4B's own, unrelated concurrency-hardening
     * concern would be misleading about which write path actually holds the lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NormalizedContentCandidate c where c.id = :id")
    Optional<NormalizedContentCandidate> findByIdForConflictAnalysis(@Param("id") Long id);

    /**
     * JLPT-MAX Ticket 4E-3A: single-row {@code PESSIMISTIC_WRITE} lock used only by
     * {@code NormalizedCandidateCanonicalGroupService} when creating a canonical group - acquired once
     * per participating candidate id, in ascending order, before any pair-review lock or group/member/
     * edge write. Kept separate from {@link #findByIdAndCandidateTypeForPromotion} (Ticket 4E-1's own,
     * differently-scoped single-candidate promotion lock) for the same naming-clarity reason as
     * {@link #findByIdForConflictAnalysis}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NormalizedContentCandidate c where c.id = :id and c.candidateType = :candidateType")
    Optional<NormalizedContentCandidate> findByIdAndCandidateTypeForCanonicalGroupCreation(
            @Param("id") Long id, @Param("candidateType") NormalizedCandidateType candidateType);

    /**
     * JLPT-MAX Ticket 4E-3B: single-row {@code PESSIMISTIC_WRITE} lock used only by
     * {@code NormalizedCandidateGroupPromotionService} - acquired once per member candidate id
     * (canonical included), in ascending order, after the group header row is already locked and
     * before any review/provenance lock. Kept separate from
     * {@link #findByIdAndCandidateTypeForCanonicalGroupCreation} (Ticket 4E-3A's own group-creation
     * lock) and {@link #findByIdAndCandidateTypeForPromotion} (Ticket 4E-1's own single-candidate
     * promotion lock) for the same naming-clarity reason those two are already kept separate from each
     * other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NormalizedContentCandidate c where c.id = :id and c.candidateType = :candidateType")
    Optional<NormalizedContentCandidate> findByIdAndCandidateTypeForGroupPromotion(
            @Param("id") Long id, @Param("candidateType") NormalizedCandidateType candidateType);
}
