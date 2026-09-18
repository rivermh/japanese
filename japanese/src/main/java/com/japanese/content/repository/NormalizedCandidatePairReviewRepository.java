package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidatePairReview;
import com.japanese.content.entity.NormalizedCandidateType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedCandidatePairReviewRepository extends JpaRepository<NormalizedCandidatePairReview, Long> {

    Optional<NormalizedCandidatePairReview> findByLeftCandidateIdAndRightCandidateId(Long leftCandidateId,
            Long rightCandidateId);

    /**
     * JLPT-MAX Ticket 4E-3A: {@code PESSIMISTIC_WRITE} row lock over one stable pair-review identity,
     * acquired by {@code NormalizedCandidateCanonicalGroupService} for every required C(N,2) edge, in
     * deterministic (lowerCandidateId, higherCandidateId) order, AFTER every participating candidate
     * row is already locked. Ticket 4C's own {@code submitDecision} never takes this lock (it never
     * locks candidates either - see that method's own javadoc) - its plain {@code save()} UPDATE simply
     * blocks automatically at the storage-engine level against whichever review row this transaction
     * currently holds, which is what makes concurrent re-review safe to interleave around a canonical-
     * group creation without any change to Ticket 4C's existing code.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NormalizedCandidatePairReview r "
            + "where r.leftCandidate.id = :leftCandidateId and r.rightCandidate.id = :rightCandidateId")
    Optional<NormalizedCandidatePairReview> findByLeftCandidateIdAndRightCandidateIdForCanonicalGroupCreation(
            @Param("leftCandidateId") Long leftCandidateId, @Param("rightCandidateId") Long rightCandidateId);

    /**
     * JLPT-MAX Ticket 4E-3B: the same {@code PESSIMISTIC_WRITE} identity lock as
     * {@link #findByLeftCandidateIdAndRightCandidateIdForCanonicalGroupCreation}, kept as its own
     * dedicated method (not reused) for the same naming-clarity reason - acquired by
     * {@code NormalizedCandidateGroupPromotionService} for every one of the group's historical C(N,2)
     * edges, in the same deterministic (lowerCandidateId, higherCandidateId) order, after every member
     * candidate row is already locked.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NormalizedCandidatePairReview r "
            + "where r.leftCandidate.id = :leftCandidateId and r.rightCandidate.id = :rightCandidateId")
    Optional<NormalizedCandidatePairReview> findByLeftCandidateIdAndRightCandidateIdForGroupPromotion(
            @Param("leftCandidateId") Long leftCandidateId, @Param("rightCandidateId") Long rightCandidateId);

    /**
     * Fetch-joins {@code leftCandidate}/{@code rightCandidate}/{@code reviewer} so an admin review
     * list page (JLPT-MAX Ticket 4C) can look up every review for a
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
