package com.japanese.learning.repository;

import com.japanese.learning.entity.QuizSessionItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizSessionItemRepository extends JpaRepository<QuizSessionItem, Long> {
    List<QuizSessionItem> findBySessionIdOrderByPosition(Long sessionId);
    Optional<QuizSessionItem> findByIdAndSessionId(Long id, Long sessionId);
}
