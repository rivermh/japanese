package com.japanese.content.repository;

import com.japanese.content.entity.GrammarRelation;
import com.japanese.content.entity.GrammarRelationType;
import com.japanese.content.entity.ReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrammarRelationRepository extends JpaRepository<GrammarRelation, Long>, JpaSpecificationExecutor<GrammarRelation> {
    Optional<GrammarRelation> findByLeftGrammarIdAndRightGrammarIdAndRelationType(Long leftId, Long rightId, GrammarRelationType type);

    @Query("""
            select relation from GrammarRelation relation
            where (relation.leftGrammar.id = :grammarId or relation.rightGrammar.id = :grammarId)
              and relation.published = true and relation.reviewStatus = :status
              and relation.leftGrammar.contentItem.published = true and relation.rightGrammar.contentItem.published = true
            order by relation.id
            """)
    List<GrammarRelation> findPublicForGrammar(@Param("grammarId") Long grammarId, @Param("status") ReviewStatus status);

    @Query("""
            select relation from GrammarRelation relation
            where relation.leftGrammar.id = :leftId and relation.rightGrammar.id = :rightId
              and relation.published = true and relation.reviewStatus = :status
            """)
    List<GrammarRelation> findPublicPair(@Param("leftId") Long leftId, @Param("rightId") Long rightId, @Param("status") ReviewStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from GrammarRelation r where r.id=:id")
    Optional<GrammarRelation> findByIdForReview(@Param("id") Long id);
}
