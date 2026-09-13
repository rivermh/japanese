package com.japanese.content.repository;

import com.japanese.content.entity.ContentReviewHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import com.japanese.content.entity.ReviewStatus;

public interface ContentReviewHistoryRepository extends JpaRepository<ContentReviewHistory, Long> {

    List<ContentReviewHistory> findByContentItemIdOrderByReviewedAtDesc(Long contentItemId);
    long countByStatusAndReviewedAtGreaterThanEqual(ReviewStatus status, Instant since);
}
