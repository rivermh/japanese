package com.japanese.learning.repository;

import com.japanese.learning.entity.StudyRecord;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.entity.StudyActivityType;
import com.japanese.learning.dto.WeaknessStudyAggregate;
import com.japanese.learning.dto.WeaknessStreakAggregate;
import com.japanese.learning.dto.WeeklyStudyAggregate;
import com.japanese.learning.dto.WeeklyJlptProgressChange;
import com.japanese.content.entity.ContentType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyRecordRepository extends JpaRepository<StudyRecord, Long> {

    List<StudyRecord> findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(String learnerKey);

    List<StudyRecord> findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(String learnerKey, Pageable pageable);

    List<StudyRecord> findByLearnerProfileLearnerKeyAndResultOrderByStudiedAtDesc(
            String learnerKey, StudyResult result, Pageable pageable);

    List<StudyRecord> findByLearnerProfileLearnerKeyAndStudiedAtGreaterThanEqualOrderByStudiedAtAsc(
            String learnerKey, Instant startedAt);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {
            "contentItem", "contentItem.word", "contentItem.word.meanings", "contentItem.grammar"})
    List<StudyRecord> findByLearnerProfileLearnerKeyAndStudiedAtGreaterThanEqualAndStudiedAtLessThanOrderByStudiedAtAsc(
            String learnerKey, Instant startedAt, Instant endedAt);

    long countByLearnerProfileLearnerKeyAndResult(String learnerKey, StudyResult result);

    long countByLearnerProfileLearnerKeyAndStudiedAtGreaterThanEqual(String learnerKey, Instant startedAt);

    Optional<StudyRecord> findFirstByLearnerProfileLearnerKeyOrderByStudiedAtDesc(String learnerKey);

    long countByLearnerProfileLearnerKeyAndResultAndStudiedAtGreaterThanEqual(
            String learnerKey, StudyResult result, Instant startedAt);

    Optional<StudyRecord> findByLearnerProfileLearnerKeyAndSessionKeyAndContentItemId(
            String learnerKey, String sessionKey, Long contentItemId);

    @Query("""
            select count(record) from StudyRecord record
            where record.learnerProfile.learnerKey = :learnerKey
              and record.studiedAt >= :startedAt
              and (record.activityType is null or record.activityType <> :excludedType)
            """)
    long countRegularContentByLearnerAndStudiedAtGreaterThanEqual(
            @Param("learnerKey") String learnerKey,
            @Param("startedAt") Instant startedAt,
            @Param("excludedType") StudyActivityType excludedType);

    @Query("""
            select count(record) from StudyRecord record
            where record.learnerProfile.learnerKey = :learnerKey
              and record.studiedAt >= :startedAt
              and record.result = :result
              and (record.activityType is null or record.activityType <> :excludedType)
            """)
    long countRegularByLearnerAndResultAndStudiedAtGreaterThanEqual(
            @Param("learnerKey") String learnerKey,
            @Param("startedAt") Instant startedAt,
            @Param("result") StudyResult result,
            @Param("excludedType") StudyActivityType excludedType);

    @Query("""
            select count(record) from StudyRecord record
            join record.contentItem item
            where record.learnerProfile.learnerKey = :learnerKey
              and record.studiedAt >= :startedAt
              and record.activityType = :activityType
              and item.type = :contentType
            """)
    long countByLearnerAndStudiedAtGreaterThanEqualAndActivityType(
            @Param("learnerKey") String learnerKey,
            @Param("startedAt") Instant startedAt,
            @Param("activityType") StudyActivityType activityType,
            @Param("contentType") ContentType contentType);

    @Query("select count(record) from StudyRecord record where record.learnerProfile.learnerKey=:learnerKey and record.studiedAt>=:startedAt and record.activityType=:activityType")
    long countByLearnerAndStudiedAtGreaterThanEqualAndActivityType(
            @Param("learnerKey") String learnerKey, @Param("startedAt") Instant startedAt,
            @Param("activityType") StudyActivityType activityType);

    @Query("""
            select new com.japanese.learning.dto.WeaknessStudyAggregate(
                item.id,
                coalesce(sum(case when record.result = :incorrect then 1 else 0 end), 0),
                coalesce(sum(case when record.result = :correct then 1 else 0 end), 0),
                max(case when record.result = :incorrect then record.studiedAt else null end),
                max(case when record.result = :correct then record.studiedAt else null end))
            from StudyRecord record join record.contentItem item
            where record.learnerProfile.learnerKey = :learnerKey
              and record.studiedAt >= :since
              and item.published = true
            group by item.id
            having sum(case when record.result = :incorrect then 1 else 0 end) > 0
            order by max(case when record.result = :incorrect then record.studiedAt else null end) desc
            """)
    List<WeaknessStudyAggregate> summarizeWeaknesses(
            @Param("learnerKey") String learnerKey,
            @Param("since") Instant since,
            @Param("correct") StudyResult correct,
            @Param("incorrect") StudyResult incorrect,
            Pageable pageable);

    @Query("""
            select new com.japanese.learning.dto.WeaknessStreakAggregate(item.id, count(record))
            from StudyRecord record join record.contentItem item
            where record.learnerProfile.learnerKey = :learnerKey
              and record.result = :incorrect
              and record.studiedAt >= :since
              and item.published = true
              and not exists (
                  select later.id from StudyRecord later
                  where later.learnerProfile.learnerKey = :learnerKey
                    and later.contentItem.id = item.id
                    and later.result = :correct
                    and later.studiedAt > record.studiedAt)
            group by item.id
            having count(record) >= :minimum
            order by count(record) desc
            """)
    List<WeaknessStreakAggregate> findCurrentIncorrectStreaks(
            @Param("learnerKey") String learnerKey,
            @Param("since") Instant since,
            @Param("correct") StudyResult correct,
            @Param("incorrect") StudyResult incorrect,
            @Param("minimum") long minimum,
            Pageable pageable);

    @Query("""
            select new com.japanese.learning.dto.WeeklyStudyAggregate(
                coalesce(sum(case when record.activityType = :newType and item.type = :word then 1 else 0 end), 0),
                coalesce(sum(case when record.activityType = :newType and item.type = :grammar then 1 else 0 end), 0),
                coalesce(sum(case when record.activityType = :reviewType or record.activityType is null then 1 else 0 end), 0),
                coalesce(sum(case when record.activityType = :retrainType then 1 else 0 end), 0),
                coalesce(sum(case when (record.activityType <> :retrainType or record.activityType is null) and record.result = :correct then 1 else 0 end), 0),
                coalesce(sum(case when (record.activityType <> :retrainType or record.activityType is null) and record.result = :incorrect then 1 else 0 end), 0),
                coalesce(sum(case when (record.activityType <> :retrainType or record.activityType is null) and record.result = :correct then :correctExperience
                                  when (record.activityType <> :retrainType or record.activityType is null) and record.result = :incorrect then :incorrectExperience else 0 end), 0))
            from StudyRecord record join record.contentItem item
            where record.learnerProfile.learnerKey = :learnerKey
              and record.studiedAt >= :start and record.studiedAt < :end
            """)
    WeeklyStudyAggregate summarizeWeeklyStudy(
            @Param("learnerKey") String learnerKey, @Param("start") Instant start, @Param("end") Instant end,
            @Param("newType") StudyActivityType newType, @Param("reviewType") StudyActivityType reviewType,
            @Param("retrainType") StudyActivityType retrainType, @Param("word") ContentType word,
            @Param("grammar") ContentType grammar, @Param("correct") StudyResult correct,
            @Param("incorrect") StudyResult incorrect, @Param("correctExperience") int correctExperience,
            @Param("incorrectExperience") int incorrectExperience);

    @Query("""
            select new com.japanese.learning.dto.WeeklyJlptProgressChange(level.code, item.type, count(distinct item.id))
            from StudyRecord record join record.contentItem item join item.levels level
            where record.learnerProfile.learnerKey = :learnerKey
              and record.activityType = :newType
              and record.studiedAt >= :start and record.studiedAt < :end
              and level.system = 'JLPT'
            group by level.code, item.type
            order by level.code, item.type
            """)
    List<WeeklyJlptProgressChange> summarizeNewlyStartedByJlptLevel(
            @Param("learnerKey") String learnerKey, @Param("start") Instant start, @Param("end") Instant end,
            @Param("newType") StudyActivityType newType);
}
