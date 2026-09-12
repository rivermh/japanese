package com.japanese.content.repository;

import com.japanese.content.entity.ContentSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentSourceRepository extends JpaRepository<ContentSource, Long> {

    Optional<ContentSource> findBySourceRef(String sourceRef);

    List<ContentSource> findAllByOrderByDisplayNameAsc();
}
