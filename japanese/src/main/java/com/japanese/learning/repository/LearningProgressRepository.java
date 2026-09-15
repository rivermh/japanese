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
    Optional<LearningProgress> findByLearnerProfileLearnerKeyAndContentItemId(
            String learnerKey, Long contentItemId);

    List<LearningProgress> findByLearnerProfileLearnerKeyAndContentItemIdIn(
            String learnerKey, java.util.Collection<Long> contentItemIds);

    long countByLearnerProfileLearnerKey(String learnerKey);

    @Query("""
            select distinct progress
            from LearningProgress progress
            join fetch progress.contentItem item
            where progress.learnerProfile.learnerKey = :learnerKey
              and progress.nextReviewAt <= :now
              and item.published = true
              and (progress.learningState is null or progress.learningState <> :suspended)
              and (:type is null or item.type = :type)
              and (:filterLevels = false or exists (
                    select 1 from ContentItem levelItem join levelItem.levels level
                    where levelItem.id = item.id and level.id in :levelIds))
              and (:filterCategories = false or exists (
                    select 1 from ContentItem categoryItem join categoryItem.categories category
                    where categoryItem.id = item.id and category.id in :categoryIds))
            order by progress.nextReviewAt asc
            """)
    List<LearningProgress> findEligibleDue(
            @Param("learnerKey") String learnerKey,
            @Param("now") Instant now,
            @Param("type") com.japanese.content.entity.ContentType type,
            @Param("suspended") com.japanese.learning.entity.LearningState suspended,
            @Param("filterLevels") boolean filterLevels,
            @Param("levelIds") java.util.Collection<Long> levelIds,
            @Param("filterCategories") boolean filterCategories,
            @Param("categoryIds") java.util.Collection<Long> categoryIds,
            Pageable pageable
    );

    @Query("""
            select count(distinct progress)
            from LearningProgress progress
            join progress.contentItem item
            where progress.learnerProfile.learnerKey = :learnerKey
              and progress.nextReviewAt <= :now
              and item.published = true
              and (progress.learningState is null or progress.learningState <> :suspended)
              and (:filterLevels = false or exists (
                    select 1 from ContentItem levelItem join levelItem.levels level
                    where levelItem.id = item.id and level.id in :levelIds))
              and (:filterCategories = false or exists (
                    select 1 from ContentItem categoryItem join categoryItem.categories category
                    where categoryItem.id = item.id and category.id in :categoryIds))
            """)
    long countEligibleDue(
            @Param("learnerKey") String learnerKey,
            @Param("now") Instant now,
            @Param("suspended") com.japanese.learning.entity.LearningState suspended,
            @Param("filterLevels") boolean filterLevels,
            @Param("levelIds") java.util.Collection<Long> levelIds,
            @Param("filterCategories") boolean filterCategories,
            @Param("categoryIds") java.util.Collection<Long> categoryIds);

    @Query("""
            select min(progress.nextReviewAt)
            from LearningProgress progress
            join progress.contentItem item
            where progress.learnerProfile.learnerKey = :learnerKey
              and item.published = true
              and (progress.learningState is null or progress.learningState <> :suspended)
              and (:filterLevels = false or exists (
                    select 1 from ContentItem levelItem join levelItem.levels level
                    where levelItem.id = item.id and level.id in :levelIds))
              and (:filterCategories = false or exists (
                    select 1 from ContentItem categoryItem join categoryItem.categories category
                    where categoryItem.id = item.id and category.id in :categoryIds))
            """)
    Instant findNextEligibleReviewAt(@Param("learnerKey") String learnerKey,
            @Param("suspended") com.japanese.learning.entity.LearningState suspended,
            @Param("filterLevels") boolean filterLevels, @Param("levelIds") java.util.Collection<Long> levelIds,
            @Param("filterCategories") boolean filterCategories, @Param("categoryIds") java.util.Collection<Long> categoryIds);

    @Query("""
            select count(distinct progress)
            from LearningProgress progress
            join progress.contentItem item
            where progress.learnerProfile.learnerKey = :learnerKey
              and progress.nextReviewAt >= :start and progress.nextReviewAt < :end
              and item.published = true
              and (progress.learningState is null or progress.learningState <> :suspended)
              and (:filterLevels = false or exists (
                    select 1 from ContentItem levelItem join levelItem.levels level
                    where levelItem.id = item.id and level.id in :levelIds))
              and (:filterCategories = false or exists (
                    select 1 from ContentItem categoryItem join categoryItem.categories category
                    where categoryItem.id = item.id and category.id in :categoryIds))
            """)
    long countEligibleScheduledBetween(@Param("learnerKey") String learnerKey,
            @Param("start") Instant start, @Param("end") Instant end,
            @Param("suspended") com.japanese.learning.entity.LearningState suspended,
            @Param("filterLevels") boolean filterLevels, @Param("levelIds") java.util.Collection<Long> levelIds,
            @Param("filterCategories") boolean filterCategories, @Param("categoryIds") java.util.Collection<Long> categoryIds);

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
              and item.published = true
              and (progress.learningState is null or progress.learningState <> :suspended)
            group by level.code
            order by level.code
            """)
    List<com.japanese.learning.dto.LevelStudyProgress> summarizeEligibleByJlptLevel(
            @Param("learnerKey") String learnerKey, @Param("now") Instant now,
            @Param("suspended") com.japanese.learning.entity.LearningState suspended);
}
