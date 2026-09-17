package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidateType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedCandidatePairReviewRepository extends JpaRepository<NormalizedCandidatePairReview, Long> {

    Optional<NormalizedCandidatePairReview> findByLeftCandidateIdAndRightCandidateId(Long leftCandidateId,
            Long rightCandidateId);

    /**
     * Fetch-joins {@code leftCandidate}/{@code rightCandidate}/{@code reviewer} so an admin review
     * list page (JLPT-MAX Ticket 4C step 21) can look up every review for a
     * {@code (candidateType, sourceRef)} scope in one query and join it in memory against the
     * current Ticket 4B pairs, rather than issuing one review lookup per pair row.
     * {@code sourceRef} is optional - {@code null} matches every source ref for the given type.
     */
    @Query("select r from NormalizedCandidatePairReview r "
            + "join fetch r.leftCandidate join fetch r.rightCandidate join fetch r.reviewer "
            + "where r.leftCandidate.candidateType = :candidateType "
            + "and (:sourceRef is null or r.leftCandidate.sourceRef = :sourceRef)")
    List<NormalizedCandidatePairReview> findForReviewList(@Param("candidateType") NormalizedCandidateType candidateType,
            @Param("sourceRef") String sourceRef);
}
