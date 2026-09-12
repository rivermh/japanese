package com.japanese.content.repository;

import com.japanese.content.entity.ContentReviewHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentReviewHistoryRepository extends JpaRepository<ContentReviewHistory, Long> {

    List<ContentReviewHistory> findByContentItemIdOrderByReviewedAtDesc(Long contentItemId);
}
