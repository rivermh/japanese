package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
