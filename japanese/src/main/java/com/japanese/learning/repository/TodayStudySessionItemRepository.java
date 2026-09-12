package com.japanese.learning.repository;

import com.japanese.learning.entity.TodayStudySessionItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TodayStudySessionItemRepository extends JpaRepository<TodayStudySessionItem, Long> {
    @Query("select item from TodayStudySessionItem item join fetch item.contentItem where item.session.id=:sessionId order by item.position")
    List<TodayStudySessionItem> findItems(Long sessionId);
    @Query("select item from TodayStudySessionItem item join fetch item.contentItem where item.session.id=:sessionId and item.completed=false order by item.position")
    List<TodayStudySessionItem> findIncompleteItems(Long sessionId);
    Optional<TodayStudySessionItem> findBySessionIdAndContentItemSlug(Long sessionId, String slug);
    long countBySessionIdAndCompletedTrue(Long sessionId);
}
