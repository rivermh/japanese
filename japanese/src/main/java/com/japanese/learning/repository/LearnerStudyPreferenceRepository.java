package com.japanese.learning.repository;

import com.japanese.learning.entity.LearnerStudyPreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearnerStudyPreferenceRepository extends JpaRepository<LearnerStudyPreference, Long> {
    Optional<LearnerStudyPreference> findByLearnerProfileId(Long learnerProfileId);
}
