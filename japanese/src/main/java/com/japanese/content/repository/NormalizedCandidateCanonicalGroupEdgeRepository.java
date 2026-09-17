package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidateCanonicalGroupEdge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedCandidateCanonicalGroupEdgeRepository
        extends JpaRepository<NormalizedCandidateCanonicalGroupEdge, Long> {

    /**
     * JLPT-MAX Ticket 4E-3A: every permanent edge row for one group, fetch-joining both candidates and
     * the reviewer snapshot for a single detail-page render - used both for an ACTIVE group's admin
     * detail view and, after dissolution, as the sole surviving source (together with the group header)
     * of that group's historical full membership (see {@code NormalizedCandidateCanonicalGroupEdge}'s
     * own class javadoc for why this reconstruction always works).
     */
    @Query("select e from NormalizedCandidateCanonicalGroupEdge e "
            + "join fetch e.leftCandidate join fetch e.rightCandidate join fetch e.reviewerSnapshot "
            + "where e.group.id = :groupId order by e.id asc")
    List<NormalizedCandidateCanonicalGroupEdge> findByGroup_IdOrderByIdAsc(@Param("groupId") Long groupId);
}
