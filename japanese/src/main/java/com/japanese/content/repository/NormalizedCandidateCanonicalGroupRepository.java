package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidateCanonicalGroup;
import com.japanese.content.entity.NormalizedCandidateCanonicalGroupStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
