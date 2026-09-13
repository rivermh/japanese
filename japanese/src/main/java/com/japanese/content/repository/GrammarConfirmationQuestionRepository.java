package com.japanese.content.repository;

import com.japanese.content.entity.GrammarConfirmationQuestion;
import com.japanese.content.entity.ReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrammarConfirmationQuestionRepository extends JpaRepository<GrammarConfirmationQuestion, Long>, JpaSpecificationExecutor<GrammarConfirmationQuestion> {
    List<GrammarConfirmationQuestion> findByGrammarContentItemSlugAndPublishedTrueAndReviewStatusOrderById(String slug, ReviewStatus status);
    Optional<GrammarConfirmationQuestion> findByIdAndPublishedTrueAndReviewStatus(Long id, ReviewStatus status);
    Optional<GrammarConfirmationQuestion> findByGrammarIdAndSourceRef(Long grammarId, String sourceRef);

    @Query("select distinct question from GrammarConfirmationQuestion question join fetch question.choices where question.published=true and question.reviewStatus=:status and question.grammar.contentItem.id in :contentIds order by question.id")
    List<GrammarConfirmationQuestion> findApprovedForQuiz(
            @Param("contentIds") java.util.Collection<Long> contentIds, @Param("status") ReviewStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct q from GrammarConfirmationQuestion q left join fetch q.choices where q.id=:id")
    Optional<GrammarConfirmationQuestion> findByIdForReview(@Param("id") Long id);
}
