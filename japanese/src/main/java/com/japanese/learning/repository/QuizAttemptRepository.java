package com.japanese.learning.repository;

import com.japanese.learning.entity.QuizAttempt;
import com.japanese.learning.entity.StudyResult;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    long countByLearnerProfileLearnerKeyAndResult(String learnerKey, StudyResult result);

    long countByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqual(String learnerKey, Instant answeredAt);

    long countByLearnerProfileLearnerKeyAndResultAndAnsweredAtGreaterThanEqual(
            String learnerKey, StudyResult result, Instant answeredAt);
    List<QuizAttempt> findByLearnerProfileLearnerKeyOrderByAnsweredAtDesc(String learnerKey, Pageable pageable);

    List<QuizAttempt> findByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqualOrderByAnsweredAtAsc(
            String learnerKey, Instant startedAt);

    List<QuizAttempt> findByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThanOrderByAnsweredAtAsc(
            String learnerKey, Instant startedAt, Instant endedAt);
}
