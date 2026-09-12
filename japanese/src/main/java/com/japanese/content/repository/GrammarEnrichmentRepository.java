package com.japanese.content.repository;

import com.japanese.content.entity.GrammarEnrichment;
import com.japanese.content.entity.ReviewStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrammarEnrichmentRepository extends JpaRepository<GrammarEnrichment, Long> {
    Optional<GrammarEnrichment> findByGrammarId(Long grammarId);
    Optional<GrammarEnrichment> findByGrammarIdAndPublishedTrueAndReviewStatus(Long grammarId, ReviewStatus reviewStatus);
}
