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
}
