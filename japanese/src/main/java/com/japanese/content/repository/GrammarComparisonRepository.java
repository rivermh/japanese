package com.japanese.content.repository;

import com.japanese.content.entity.GrammarComparison;
import com.japanese.content.entity.ReviewStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GrammarComparisonRepository extends JpaRepository<GrammarComparison, Long>, JpaSpecificationExecutor<GrammarComparison> {
    Optional<GrammarComparison> findByRelationId(Long relationId);
    Optional<GrammarComparison> findByRelationIdAndPublishedTrueAndReviewStatus(Long relationId, ReviewStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from GrammarComparison c where c.id=:id")
    Optional<GrammarComparison> findByIdForReview(@Param("id") Long id);
}
