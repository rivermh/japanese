package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidatePairReviewHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedCandidatePairReviewHistoryRepository
        extends JpaRepository<NormalizedCandidatePairReviewHistory, Long> {

    @Query("select h from NormalizedCandidatePairReviewHistory h join fetch h.reviewer "
            + "where h.leftCandidate.id = :leftCandidateId and h.rightCandidate.id = :rightCandidateId "
            + "order by h.reviewedAt asc, h.id asc")
    List<NormalizedCandidatePairReviewHistory> findByCandidatePairOrderByReviewedAtAsc(
            @Param("leftCandidateId") Long leftCandidateId, @Param("rightCandidateId") Long rightCandidateId);
}
