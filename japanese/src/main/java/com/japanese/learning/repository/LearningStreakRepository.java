package com.japanese.learning.repository;

import com.japanese.learning.entity.LearningStreak;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningStreakRepository extends JpaRepository<LearningStreak, Long> {
    Optional<LearningStreak> findByLearnerProfileId(Long learnerProfileId);
}
