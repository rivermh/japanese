package com.japanese.content.repository;

import com.japanese.content.entity.ContentReleaseBatch;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentReleaseBatchRepository extends JpaRepository<ContentReleaseBatch, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "reviewer")
    org.springframework.data.domain.Page<ContentReleaseBatch> findAllByOrderByIdDesc(org.springframework.data.domain.Pageable pageable);
    Optional<ContentReleaseBatch> findByGateVersionAndPreviewDigest(String gateVersion, String previewDigest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ContentReleaseBatch b where b.id=:id")
    Optional<ContentReleaseBatch> findByIdForUpdate(@Param("id") Long id);
}
