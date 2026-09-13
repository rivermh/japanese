package com.japanese.learning.repository;

import com.japanese.learning.entity.LearningProgress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearningProgressRepository extends JpaRepository<LearningProgress, Long> {
    @Query("select min(progress.nextReviewAt) from LearningProgress progress where progress.learnerProfile.learnerKey=:learnerKey")
    java.time.Instant findNextReviewAt(@Param("learnerKey") String learnerKey);

    Optional<LearningProgress> findByLearnerProfileLearnerKeyAndContentItemId(
            String learnerKey, Long contentItemId);

    List<LearningProgress> findByLearnerProfileLearnerKeyAndContentItemIdIn(
            String learnerKey, java.util.Collection<Long> contentItemIds);

    long countByLearnerProfileLearnerKey(String learnerKey);

    List<LearningProgress> findByLearnerProfileLearnerKeyAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            String learnerKey, Instant now, Pageable pageable);

    @Query("""
            select distinct progress
            from LearningProgress progress
            join fetch progress.contentItem item
            left join item.levels level
            left join item.categories category
            where progress.learnerProfile.learnerKey = :learnerKey
              and progress.nextReviewAt <= :now
              and item.published = true
              and (:type is null or item.type = :type)
              and (:filterLevels = false or level.id in :levelIds)
              and (:filterCategories = false or category.id in :categoryIds)
            order by progress.nextReviewAt asc
            """)
    List<LearningProgress> findDueForLearner(
            @Param("learnerKey") String learnerKey,
            @Param("now") Instant now,
            @Param("type") com.japanese.content.entity.ContentType type,
            @Param("filterLevels") boolean filterLevels,
            @Param("levelIds") java.util.Collection<Long> levelIds,
            @Param("filterCategories") boolean filterCategories,
            @Param("categoryIds") java.util.Collection<Long> categoryIds,
            Pageable pageable
    );

    long countByLearnerProfileLearnerKeyAndNextReviewAtLessThanEqual(String learnerKey, Instant now);

    long countByLearnerProfileLearnerKeyAndNextReviewAtGreaterThanEqualAndNextReviewAtLessThan(
            String learnerKey, Instant startedAt, Instant endedAt);

    @Query("""
            select count(distinct progress)
            from LearningProgress progress
            join progress.contentItem item
            left join item.levels level
            left join item.categories category
            where progress.learnerProfile.learnerKey = :learnerKey
              and progress.nextReviewAt <= :now
              and item.published = true
              and (:filterLevels = false or level.id in :levelIds)
              and (:filterCategories = false or category.id in :categoryIds)
            """)
    long countDueForLearner(
            @Param("learnerKey") String learnerKey,
            @Param("now") Instant now,
            @Param("filterLevels") boolean filterLevels,
            @Param("levelIds") java.util.Collection<Long> levelIds,
            @Param("filterCategories") boolean filterCategories,
            @Param("categoryIds") java.util.Collection<Long> categoryIds);

    @Query("select progress.contentItem.id from LearningProgress progress where progress.learnerProfile.learnerKey = :learnerKey")
    java.util.Set<Long> findLearnedContentIds(@Param("learnerKey") String learnerKey);

    @Query("""
            select new com.japanese.learning.dto.LevelStudyProgress(
                    level.code,
                    count(progress.id),
                    coalesce(sum(case when progress.nextReviewAt <= :now then 1 else 0 end), 0))
            from LearningProgress progress
            join progress.contentItem item
            join item.levels level
            where progress.learnerProfile.learnerKey = :learnerKey
              and level.system = 'JLPT'
            group by level.code
            order by level.code
            """)
    List<com.japanese.learning.dto.LevelStudyProgress> summarizeByJlptLevel(
            @Param("learnerKey") String learnerKey, @Param("now") Instant now);
}
