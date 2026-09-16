package com.japanese.content.repository;

import com.japanese.content.entity.ContentReleaseBatchItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentReleaseBatchItemRepository extends JpaRepository<ContentReleaseBatchItem, Long> {
    List<ContentReleaseBatchItem> findByBatchIdOrderByPosition(Long batchId);
}
