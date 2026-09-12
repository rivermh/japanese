package com.japanese.content.repository;

import com.japanese.content.entity.GrammarComparison;
import com.japanese.content.entity.ReviewStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrammarComparisonRepository extends JpaRepository<GrammarComparison, Long> {
    Optional<GrammarComparison> findByRelationId(Long relationId);
    Optional<GrammarComparison> findByRelationIdAndPublishedTrueAndReviewStatus(Long relationId, ReviewStatus status);
}
