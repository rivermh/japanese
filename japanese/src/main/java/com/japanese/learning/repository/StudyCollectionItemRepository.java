package com.japanese.learning.repository;

import com.japanese.learning.entity.StudyCollectionItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyCollectionItemRepository extends JpaRepository<StudyCollectionItem, Long> {
    @EntityGraph(attributePaths = {"contentItem", "contentItem.word", "contentItem.word.meanings", "contentItem.grammar", "contentItem.levels", "contentItem.categories"})
    List<StudyCollectionItem> findByStudyCollectionIdOrderByCreatedAtAsc(Long collectionId);
    Optional<StudyCollectionItem> findByStudyCollectionIdAndContentItemSlug(Long collectionId, String slug);
    void deleteByStudyCollectionIdAndContentItemSlug(Long collectionId, String slug);
    long countByStudyCollectionId(Long collectionId);
}
