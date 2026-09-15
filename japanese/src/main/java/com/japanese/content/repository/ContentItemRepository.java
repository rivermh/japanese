package com.japanese.content.repository;

import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.ReviewStatus;
import com.japanese.learning.entity.LearningProgress;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long>, JpaSpecificationExecutor<ContentItem> {

    Optional<ContentItem> findBySlug(String slug);

    Optional<ContentItem> findBySlugAndPublishedTrue(String slug);

    @Query("""
            select distinct item from ContentItem item
            left join fetch item.word word
            left join fetch word.meanings
            left join fetch item.grammar
            left join fetch item.levels
            left join fetch item.categories
            where item.published = true and item.id in :ids
            """)
    List<ContentItem> findPublishedForSummaryByIdIn(@Param("ids") java.util.Collection<Long> ids);

    /** Scope is applied in SQL before the candidate page is limited. */
    @Query("""
            select distinct item.id
            from ContentItem item
            where item.published = true
              and (:filterLevels = false or exists (
                    select 1 from ContentItem levelItem join levelItem.levels level
                    where levelItem.id = item.id
                      and concat(concat(level.system, ':'), level.code) in :levelCodes))
              and (:filterCategories = false or exists (
                    select 1 from ContentItem categoryItem join categoryItem.categories category
                    where categoryItem.id = item.id
                      and category.slug in :categorySlugs))
            order by item.id
            """)
    List<Long> findPublishedQuizCandidateIds(
            @Param("filterLevels") boolean filterLevels,
            @Param("levelCodes") java.util.Collection<String> levelCodes,
            @Param("filterCategories") boolean filterCategories,
            @Param("categorySlugs") java.util.Collection<String> categorySlugs,
            Pageable pageable);

    Optional<ContentItem> findByIdAndPublishedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContentItem c where c.id=:id")
    Optional<ContentItem> findByIdForReview(@Param("id") Long id);

    @Query("""
            select word.contentItem.id from Word word
            where word.contentItem.id in :ids
              and word.expressionSearch is not null and word.readingSearch is not null
              and exists (select peer.id from Word peer
                  where peer.id <> word.id
                    and peer.expressionSearch = word.expressionSearch
                    and peer.readingSearch = word.readingSearch)
            """)
    List<Long> findDuplicateWordContentIds(@Param("ids") java.util.Collection<Long> ids);

    @Query("""
            select grammar.contentItem.id from Grammar grammar
            where grammar.contentItem.id in :ids
              and grammar.patternSearch is not null
              and exists (select peer.id from Grammar peer
                  where peer.id <> grammar.id
                    and peer.patternSearch = grammar.patternSearch)
            """)
    List<Long> findDuplicateGrammarContentIds(@Param("ids") java.util.Collection<Long> ids);

    List<ContentItem> findByPublishedFalseOrderById(Pageable pageable);

    List<ContentItem> findByPublishedFalseAndReviewStatusOrderById(ReviewStatus reviewStatus, Pageable pageable);

    @Query("""
            select c from ContentItem c
            where c.published = false
              and (c.reviewStatus = :status or (:includeNull = true and c.reviewStatus is null))
            order by c.id
            """)
    List<ContentItem> findUnpublishedByReviewStatus(
            @Param("status") ReviewStatus status,
            @Param("includeNull") boolean includeNull,
            Pageable pageable);

    @Query("""
            select distinct c from ContentItem c
            left join c.levels l
            where c.published = false
              and (c.reviewStatus = :status or (:includeNull = true and c.reviewStatus is null))
              and (:type is null or c.type = :type)
              and (:levelCode is null or l.code = :levelCode)
            order by c.id
            """)
    List<ContentItem> findUnpublishedByFilters(
            @Param("status") ReviewStatus status,
            @Param("includeNull") boolean includeNull,
            @Param("type") ContentType type,
            @Param("levelCode") String levelCode,
            Pageable pageable);

    long countByPublishedFalse();

    long countByPublishedTrueAndCategoriesSlug(String categorySlug);

    long countByPublishedFalseAndReviewStatus(ReviewStatus reviewStatus);

    @Query("""
            select count(c) from ContentItem c
            where c.published = false
              and (c.reviewStatus = :status or (:includeNull = true and c.reviewStatus is null))
            """)
    long countUnpublishedByReviewStatus(
            @Param("status") ReviewStatus status,
            @Param("includeNull") boolean includeNull);

    @Query("""
            select count(distinct c) from ContentItem c
            left join c.levels l
            where c.published = false
              and (c.reviewStatus = :status or (:includeNull = true and c.reviewStatus is null))
              and (:type is null or c.type = :type)
              and (:levelCode is null or l.code = :levelCode)
            """)
    long countUnpublishedByFilters(
            @Param("status") ReviewStatus status,
            @Param("includeNull") boolean includeNull,
            @Param("type") ContentType type,
            @Param("levelCode") String levelCode);

    List<ContentItem> findByPublishedTrueOrderById(Pageable pageable);

    @Query(value = """
            select c
            from ContentItem c
            left join c.word w
            left join c.grammar g
            where c.published = true
              and (:type is null or c.type = :type)
              and (:levelCode is null or exists (
                    select 1 from ContentItem levelItem join levelItem.levels level
                    where levelItem.id = c.id and level.code = :levelCode))
              and (:categorySlug is null or exists (
                    select 1 from ContentItem categoryItem join categoryItem.categories category
                    where categoryItem.id = c.id and category.slug = :categorySlug))
              and (
                    :keyword is null
                    or w.expressionSearch like concat('%', :keyword, '%')
                    or w.readingSearch like concat('%', :keyword, '%')
                    or g.patternSearch like concat('%', :keyword, '%')
                    or g.searchText like concat('%', :keyword, '%')
                    or exists (select 1 from Meaning meaning where meaning.word.contentItem.id = c.id and meaning.searchKey like concat('%', :keyword, '%'))
                    or exists (select 1 from Example example where example.contentItem.id = c.id and example.searchKey like concat('%', :keyword, '%'))
                    or exists (
                        select 1 from ContentItem categorySearchItem join categorySearchItem.categories category
                        where categorySearchItem.id = c.id
                          and (lower(category.slug) like concat('%', :keyword, '%') or lower(category.name) like concat('%', :keyword, '%')))
              )
            order by
              case
                when :keyword is not null and (w.expressionSearch = :keyword or g.patternSearch = :keyword) then 0
                when :keyword is not null and w.readingSearch = :keyword then 1
                when :keyword is not null and (w.expressionSearch like concat(:keyword, '%') or w.readingSearch like concat(:keyword, '%') or g.patternSearch like concat(:keyword, '%')) then 2
                when :keyword is not null and exists (select 1 from Meaning exactMeaning where exactMeaning.word.contentItem.id = c.id and exactMeaning.searchKey = :keyword) then 3
                when :keyword is not null and g.searchText like concat('%', :keyword, '%') then 4
                else 5
              end,
              c.id
            """, countQuery = """
            select count(c)
            from ContentItem c
            left join c.word w
            left join c.grammar g
            where c.published = true
              and (:type is null or c.type = :type)
              and (:levelCode is null or exists (
                    select 1 from ContentItem levelItem join levelItem.levels level
                    where levelItem.id = c.id and level.code = :levelCode))
              and (:categorySlug is null or exists (
                    select 1 from ContentItem categoryItem join categoryItem.categories category
                    where categoryItem.id = c.id and category.slug = :categorySlug))
              and (
                    :keyword is null
                    or w.expressionSearch like concat('%', :keyword, '%')
                    or w.readingSearch like concat('%', :keyword, '%')
                    or g.patternSearch like concat('%', :keyword, '%')
                    or g.searchText like concat('%', :keyword, '%')
                    or exists (select 1 from Meaning meaning where meaning.word.contentItem.id = c.id and meaning.searchKey like concat('%', :keyword, '%'))
                    or exists (select 1 from Example example where example.contentItem.id = c.id and example.searchKey like concat('%', :keyword, '%'))
                    or exists (
                        select 1 from ContentItem categorySearchItem join categorySearchItem.categories category
                        where categorySearchItem.id = c.id
                          and (lower(category.slug) like concat('%', :keyword, '%') or lower(category.name) like concat('%', :keyword, '%')))
              )
            """)
    Page<ContentItem> searchPublished(
            @Param("keyword") String keyword,
            @Param("type") ContentType type,
            @Param("levelCode") String levelCode,
            @Param("categorySlug") String categorySlug,
            Pageable pageable
    );

    @Query("""
            select distinct c
            from ContentItem c
            left join c.levels l
            left join c.categories category
            where c.published = true
              and (:type is null or c.type = :type)
              and (:filterLevels = false or l.id in :levelIds)
              and (:filterCategories = false or category.id in :categoryIds)
              and not exists (
                  select progress.id
                  from LearningProgress progress
                  where progress.learnerProfile.learnerKey = :learnerKey
                    and progress.contentItem.id = c.id
              )
            order by c.id
            """)
    Page<ContentItem> findNewPublishedForLearner(
            @Param("learnerKey") String learnerKey,
            @Param("type") ContentType type,
            @Param("filterLevels") boolean filterLevels,
            @Param("levelIds") java.util.Collection<Long> levelIds,
            @Param("filterCategories") boolean filterCategories,
            @Param("categoryIds") java.util.Collection<Long> categoryIds,
            Pageable pageable
    );

    @Query("""
            select c
            from ContentItem c
            left join c.word w
            left join c.grammar g
            where (w is not null and (w.expressionSearch is null or w.readingSearch is null))
               or (g is not null and (g.patternSearch is null or g.searchText is null))
               or exists (select 1 from Meaning meaning where meaning.word.contentItem.id = c.id and meaning.searchKey is null)
               or exists (select 1 from Example example where example.contentItem.id = c.id and example.searchKey is null)
            order by c.id
            """)
    List<ContentItem> findNeedingSearchKeyBackfill(Pageable pageable);

    @Query("""
            select new com.japanese.learning.dto.LevelContentProgressAggregate(
                level.code, level.name, item.type, count(distinct item.id),
                coalesce(sum(case when progress.id is null then 1 else 0 end), 0),
                coalesce(sum(case when progress.learningState = :learning
                    or (progress.learningState is null and progress.lastResult = :incorrect) then 1 else 0 end), 0),
                coalesce(sum(case when progress.learningState = :review
                    or (progress.learningState is null and progress.lastResult = :correct) then 1 else 0 end), 0),
                coalesce(sum(case when progress.learningState = :mastered then 1 else 0 end), 0),
                coalesce(sum(case when progress.learningState = :suspended then 1 else 0 end), 0))
            from ContentItem item
            join item.levels level
            left join LearningProgress progress on progress.contentItem = item
                and progress.learnerProfile.id = :learnerProfileId
            where item.published = true
              and level.system = 'JLPT'
            group by level.code, level.name, item.type
            order by level.code, item.type
            """)
    List<com.japanese.learning.dto.LevelContentProgressAggregate> summarizeJlptProgress(
            @Param("learnerProfileId") Long learnerProfileId,
            @Param("learning") com.japanese.learning.entity.LearningState learning,
            @Param("review") com.japanese.learning.entity.LearningState review,
            @Param("mastered") com.japanese.learning.entity.LearningState mastered,
            @Param("suspended") com.japanese.learning.entity.LearningState suspended,
            @Param("correct") com.japanese.learning.entity.StudyResult correct,
            @Param("incorrect") com.japanese.learning.entity.StudyResult incorrect);
}
