package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidateCanonicalGroup;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedCandidateCanonicalGroupRepository extends JpaRepository<NormalizedCandidateCanonicalGroup, Long> {

    /**
     * JLPT-MAX Ticket 4E-3A: the admin list read path - fetch-joins {@code canonicalCandidate} so
     * rendering a preview of every group in scope never triggers one lazy-load per row.
     * {@code status} is optional ({@code null} matches every status).
     */
    @Query("select g from NormalizedCandidateCanonicalGroup g join fetch g.canonicalCandidate "
            + "where (:status is null or g.status = :status) order by g.id desc")
    List<NormalizedCandidateCanonicalGroup> findAllForList(
            @Param("status") NormalizedCandidateCanonicalGroupStatus status);

    /**
     * JLPT-MAX Ticket 4E-3B: the FIRST lock acquired by
     * {@code NormalizedCandidateGroupPromotionService.promote} - a {@code PESSIMISTIC_WRITE} lock on
     * the group header row, acquired before any candidate/review lock. This is what safely serializes
     * a group-promotion attempt against a concurrent {@code NormalizedCandidateCanonicalGroupService.dissolve}
     * call on the SAME group: dissolve's own status-flip {@code save(group)} needs an exclusive lock on
     * this same row (Hibernate flushes that pending UPDATE, under its default auto-flush behavior,
     * before dissolve's subsequent membership-row bulk {@code DELETE} can execute), so it blocks until
     * this transaction commits or rolls back - see that service's class javadoc for the full
     * concurrency analysis. Deliberately a separate method from any Ticket 4E-3A lock (there was none
     * on this table before) - this is the first lock ever taken on this specific table.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from NormalizedCandidateCanonicalGroup g where g.id = :id")
    Optional<NormalizedCandidateCanonicalGroup> findByIdForGroupPromotion(@Param("id") Long id);
}
