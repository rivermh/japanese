package com.japanese.learning.repository;

import com.japanese.learning.entity.WeaknessReviewSessionItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WeaknessReviewSessionItemRepository extends JpaRepository<WeaknessReviewSessionItem, Long> {
    @Query("select item from WeaknessReviewSessionItem item join fetch item.contentItem where item.session.id=:sessionId and item.completed=false order by item.position")
    List<WeaknessReviewSessionItem> findIncompleteItems(Long sessionId);
    Optional<WeaknessReviewSessionItem> findBySessionIdAndContentItemSlug(Long sessionId, String slug);
    long countBySessionIdAndCompletedTrue(Long sessionId);
}
