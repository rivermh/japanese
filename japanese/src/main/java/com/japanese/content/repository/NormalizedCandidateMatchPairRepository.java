package com.japanese.content.repository;

import com.japanese.content.entity.NormalizedCandidateMatchAssessment;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidateType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizedCandidateMatchPairRepository extends JpaRepository<NormalizedCandidateMatchPair, Long> {

    List<NormalizedCandidateMatchPair> findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(
            NormalizedCandidateType candidateType, String sourceRef);

    List<NormalizedCandidateMatchPair> findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRefAndAssessment(
            NormalizedCandidateType candidateType, String sourceRef, NormalizedCandidateMatchAssessment assessment);
}
