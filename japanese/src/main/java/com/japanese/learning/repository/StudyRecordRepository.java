package com.japanese.learning.repository;

import com.japanese.learning.entity.StudyRecord;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.entity.StudyActivityType;
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
}
