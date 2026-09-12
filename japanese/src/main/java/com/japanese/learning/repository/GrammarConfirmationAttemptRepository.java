package com.japanese.learning.repository;

import com.japanese.learning.entity.GrammarConfirmationAttempt;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrammarConfirmationAttemptRepository extends JpaRepository<GrammarConfirmationAttempt, Long> {
    List<GrammarConfirmationAttempt> findByLearnerProfileLearnerKeyOrderByAnsweredAtDesc(String learnerKey, Pageable pageable);
}
