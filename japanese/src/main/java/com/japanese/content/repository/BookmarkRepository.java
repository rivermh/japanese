package com.japanese.content.repository;

import com.japanese.content.entity.Bookmark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    Optional<Bookmark> findByUserAccountLoginIdAndContentItemSlug(String loginId, String slug);
    long countByUserAccountLoginId(String loginId);
    long countByUserAccountLoginIdAndContentItemPublishedTrue(String loginId);
    @EntityGraph(attributePaths = {"contentItem", "contentItem.word", "contentItem.word.meanings", "contentItem.grammar", "contentItem.levels", "contentItem.categories"})
    List<Bookmark> findByUserAccountLoginIdAndContentItemPublishedTrueOrderByCreatedAtDesc(String loginId);
}
