package com.japanese.learning.repository;

import com.japanese.learning.entity.StudyCollection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyCollectionRepository extends JpaRepository<StudyCollection, Long> {
    List<StudyCollection> findByUserAccountLoginIdOrderByUpdatedAtDesc(String loginId);
    long countByUserAccountLoginId(String loginId);
    Optional<StudyCollection> findByIdAndUserAccountLoginId(Long id, String loginId);
}
