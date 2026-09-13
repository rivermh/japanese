package com.japanese.learning.repository;

import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.learning.entity.StudyQueueEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyQueueRepository extends JpaRepository<StudyQueueEntry, Long> {

    Optional<StudyQueueEntry> findByUserAccountLoginIdAndContentItemSlug(String loginId, String slug);

    long countByUserAccountLoginId(String loginId);

    @EntityGraph(attributePaths = {"contentItem", "contentItem.word", "contentItem.word.meanings", "contentItem.grammar", "contentItem.levels", "contentItem.categories"})
    List<StudyQueueEntry> findByUserAccountLoginIdOrderByCreatedAtAsc(String loginId);

    @Query("""
            select queue.contentItem from StudyQueueEntry queue
            where queue.userAccount.id = :accountId
              and queue.contentItem.published = true
              and queue.contentItem.type = :type
              and not exists (
                  select progress.id from LearningProgress progress
                  where progress.learnerProfile.learnerKey = :learnerKey
                    and progress.contentItem.id = queue.contentItem.id
              )
            order by queue.createdAt asc
            """)
    List<ContentItem> findUnstartedPublishedForToday(
            @Param("accountId") Long accountId,
            @Param("learnerKey") String learnerKey,
            @Param("type") ContentType type,
            Pageable pageable);

    void deleteByUserAccountLoginIdAndContentItemId(String loginId, Long contentItemId);
}
