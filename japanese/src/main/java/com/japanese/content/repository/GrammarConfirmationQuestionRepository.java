package com.japanese.content.repository;

import com.japanese.content.entity.GrammarConfirmationQuestion;
import com.japanese.content.entity.ReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrammarConfirmationQuestionRepository extends JpaRepository<GrammarConfirmationQuestion, Long> {
    List<GrammarConfirmationQuestion> findByGrammarContentItemSlugAndPublishedTrueAndReviewStatusOrderById(String slug, ReviewStatus status);
    Optional<GrammarConfirmationQuestion> findByIdAndPublishedTrueAndReviewStatus(Long id, ReviewStatus status);
    Optional<GrammarConfirmationQuestion> findByGrammarIdAndSourceRef(Long grammarId, String sourceRef);
}
