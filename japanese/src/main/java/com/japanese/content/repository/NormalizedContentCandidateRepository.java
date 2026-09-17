package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizedContentCandidateRepository extends JpaRepository<NormalizedContentCandidate, Long> {
    Optional<NormalizedContentCandidate> findBySourceRefAndSourceNoteIdAndCandidateType(
            String sourceRef, long sourceNoteId, NormalizedCandidateType candidateType);

    List<NormalizedContentCandidate> findByCandidateType(NormalizedCandidateType candidateType);
}
