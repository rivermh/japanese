package com.japanese.content.repository;

import com.japanese.content.entity.Bookmark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    Optional<Bookmark> findByUserAccountLoginIdAndContentItemSlug(String loginId, String slug);
    @EntityGraph(attributePaths = {"contentItem", "contentItem.word", "contentItem.word.meanings", "contentItem.grammar", "contentItem.levels", "contentItem.categories"})
    List<Bookmark> findByUserAccountLoginIdOrderByCreatedAtDesc(String loginId);
}
