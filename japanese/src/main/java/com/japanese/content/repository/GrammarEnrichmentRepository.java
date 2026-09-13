package com.japanese.content.repository;

import com.japanese.content.entity.GrammarEnrichment;
import com.japanese.content.entity.ReviewStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GrammarEnrichmentRepository extends JpaRepository<GrammarEnrichment, Long>, JpaSpecificationExecutor<GrammarEnrichment> {
    Optional<GrammarEnrichment> findByGrammarId(Long grammarId);
    Optional<GrammarEnrichment> findByGrammarIdAndPublishedTrueAndReviewStatus(Long grammarId, ReviewStatus reviewStatus);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from GrammarEnrichment e where e.id=:id")
    Optional<GrammarEnrichment> findByIdForReview(@Param("id") Long id);
}
