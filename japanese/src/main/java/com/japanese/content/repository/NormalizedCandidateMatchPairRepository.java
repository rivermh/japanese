package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidateMatchAssessment;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidateType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedCandidateMatchPairRepository extends JpaRepository<NormalizedCandidateMatchPair, Long> {

    List<NormalizedCandidateMatchPair> findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(
            NormalizedCandidateType candidateType, String sourceRef);

    List<NormalizedCandidateMatchPair> findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRefAndAssessment(
            NormalizedCandidateType candidateType, String sourceRef, NormalizedCandidateMatchAssessment assessment);

    /**
     * JLPT-MAX Ticket 4C: the current-pair lookup for one stable candidate-pair identity - used to
     * check whether a {@link NormalizedCandidatePairReview} still has a live Ticket 4B pair behind
     * it, and to re-validate freshness before accepting a new decision. Never used to join a review
     * to a pair by FK (see {@link com.japanese.content.entity.NormalizedCandidatePairReview}'s
     * class javadoc for why).
     */
    Optional<NormalizedCandidateMatchPair> findByLeftCandidateIdAndRightCandidateId(Long leftCandidateId,
            Long rightCandidateId);

    /**
     * Same lookup as {@link #findByLeftCandidateIdAndRightCandidateId} but eagerly fetches the
     * ordered {@code evidence} collection for a single detail-page render (JLPT-MAX Ticket 4C step
     * 23) - {@code distinct} is required because a plain collection fetch join otherwise returns the
     * pair once per evidence row.
     */
    @Query("select distinct p from NormalizedCandidateMatchPair p "
            + "join fetch p.leftCandidate join fetch p.rightCandidate "
            + "left join fetch p.evidence "
            + "where p.leftCandidate.id = :leftCandidateId and p.rightCandidate.id = :rightCandidateId")
    Optional<NormalizedCandidateMatchPair> findWithEvidenceByCandidateIds(
            @Param("leftCandidateId") Long leftCandidateId, @Param("rightCandidateId") Long rightCandidateId);

    /**
     * The admin review list's read path for VOCABULARY (JLPT-MAX Ticket 4C): one query fetch
     * -joins both candidates and their {@code vocabularyDetail} so rendering a short left/right
     * preview for every row in scope never triggers one lazy-load per candidate. {@code sourceRef}
     * is optional - {@code null} matches every source ref. {@code distinct} avoids duplicate pair
     * rows a from left-joined to-one association would not itself cause, but is kept for symmetry
     * and safety with {@link #findWithEvidenceByCandidateIds}.
     */
    @Query("select distinct p from NormalizedCandidateMatchPair p "
            + "join fetch p.leftCandidate lc left join fetch lc.vocabularyDetail "
            + "join fetch p.rightCandidate rc left join fetch rc.vocabularyDetail "
            + "where lc.candidateType = com.japanese.content.entity.NormalizedCandidateType.VOCABULARY "
            + "and (:sourceRef is null or lc.sourceRef = :sourceRef) order by p.id asc")
    List<NormalizedCandidateMatchPair> findVocabularyPairsForReview(@Param("sourceRef") String sourceRef);

    /** Grammar counterpart of {@link #findVocabularyPairsForReview}, fetch-joining {@code grammarDetail}. */
    @Query("select distinct p from NormalizedCandidateMatchPair p "
            + "join fetch p.leftCandidate lc left join fetch lc.grammarDetail "
            + "join fetch p.rightCandidate rc left join fetch rc.grammarDetail "
            + "where lc.candidateType = com.japanese.content.entity.NormalizedCandidateType.GRAMMAR "
            + "and (:sourceRef is null or lc.sourceRef = :sourceRef) order by p.id asc")
    List<NormalizedCandidateMatchPair> findGrammarPairsForReview(@Param("sourceRef") String sourceRef);

    /**
     * JLPT-MAX Ticket 4D: every current pair for a {@code (candidateType, sourceRef)} scope, where
     * {@code sourceRef == null} means "every source ref for this type" - the promotion-readiness
     * planner's batch pair-participation load (never one query per candidate). Deliberately does not
     * fetch-join the candidates/evidence: the caller already holds a fully detail-loaded candidate
     * map for the same scope and only ever needs this pair's id/assessment/generatedAt plus its two
     * candidate ids (safe off an uninitialized proxy without triggering a query).
     */
    @Query("select p from NormalizedCandidateMatchPair p "
            + "where p.leftCandidate.candidateType = :candidateType "
            + "and (:sourceRef is null or p.leftCandidate.sourceRef = :sourceRef) order by p.id asc")
    List<NormalizedCandidateMatchPair> findByCandidateTypeAndOptionalSourceRef(
            @Param("candidateType") NormalizedCandidateType candidateType, @Param("sourceRef") String sourceRef);

    /**
     * JLPT-MAX Ticket 4D: every current pair a single candidate participates in (either side), with
     * evidence fetch-joined, for the promotion-readiness detail page. Only ever called for one
     * candidate at a time - never used for a scope-wide batch load (see
     * {@link #findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef} for that).
     */
    @Query("select distinct p from NormalizedCandidateMatchPair p "
            + "join fetch p.leftCandidate join fetch p.rightCandidate left join fetch p.evidence "
            + "where p.leftCandidate.id = :candidateId or p.rightCandidate.id = :candidateId order by p.id asc")
    List<NormalizedCandidateMatchPair> findByEitherCandidateIdWithEvidence(@Param("candidateId") Long candidateId);
}
