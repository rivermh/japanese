package com.japanese.content.repository;

import com.japanese.content.entity.Level;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelRepository extends JpaRepository<Level, Long> {

    Optional<Level> findBySystemAndCode(String system, String code);

    List<Level> findAllByOrderBySystemAscCodeAsc();
}
