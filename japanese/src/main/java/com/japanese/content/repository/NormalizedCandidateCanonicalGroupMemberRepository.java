package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidateCanonicalGroupMember;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NormalizedCandidateCanonicalGroupMemberRepository
        extends JpaRepository<NormalizedCandidateCanonicalGroupMember, Long> {

    /**
     * JLPT-MAX Ticket 4E-3A: which of the supplied (already candidate-locked) ids already carry a
     * current membership row - a non-empty result means at least one participant is already reserved
     * by a different ACTIVE group and creation must be rejected. A plain (non-locking) read is
     * sufficient here: {@code UNIQUE(member_candidate_id)} is the actual, timing-independent
     * correctness guarantee against double-membership (see this entity's own class javadoc); this read
     * only produces a clear, early rejection message rather than relying solely on that constraint
     * violation.
     */
    List<NormalizedCandidateCanonicalGroupMember> findByMemberCandidate_IdIn(Collection<Long> candidateIds);

    List<NormalizedCandidateCanonicalGroupMember> findByGroup_IdOrderByMemberCandidate_Id(Long groupId);

    @Modifying
    @Query("delete from NormalizedCandidateCanonicalGroupMember m where m.group.id = :groupId")
    int deleteByGroup_Id(@Param("groupId") Long groupId);
}
