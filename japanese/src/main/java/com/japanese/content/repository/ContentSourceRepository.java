package com.japanese.content.repository;

import com.japanese.content.entity.ContentSource;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentSourceRepository extends JpaRepository<ContentSource, Long> {

    Optional<ContentSource> findBySourceRef(String sourceRef);

    List<ContentSource> findBySourceRefIn(Collection<String> sourceRefs);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select source from ContentSource source where source.sourceRef in :sourceRefs order by source.id")
    List<ContentSource> findBySourceRefInForReleaseBatch(@Param("sourceRefs") Collection<String> sourceRefs);

    List<ContentSource> findAllByOrderByDisplayNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select source from ContentSource source where source.id = :id")
    Optional<ContentSource> findByIdForRightsReview(@Param("id") Long id);
}
