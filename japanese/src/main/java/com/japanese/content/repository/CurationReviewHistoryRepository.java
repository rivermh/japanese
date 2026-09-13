package com.japanese.content.repository;
import com.japanese.content.entity.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CurationReviewHistoryRepository extends JpaRepository<CurationReviewHistory,Long> {
    List<CurationReviewHistory> findByRecordTypeAndRecordIdOrderByReviewedAtDesc(CurationRecordType type, Long id);
    long countByStatusAndReviewedAtGreaterThanEqual(ReviewStatus status, Instant since);
}
