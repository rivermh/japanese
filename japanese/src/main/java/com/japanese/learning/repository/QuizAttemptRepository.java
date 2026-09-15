package com.japanese.learning.repository;

import com.japanese.learning.entity.QuizAttempt;
import com.japanese.learning.entity.StudyResult;
import java.time.Instant;
import java.util.List;
import com.japanese.learning.dto.WeeklyQuizAggregate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    boolean existsByQuizSessionItemId(Long quizSessionItemId);
    java.util.Optional<QuizAttempt> findFirstByLearnerProfileLearnerKeyOrderByAnsweredAtDesc(String learnerKey);

    long countByLearnerProfileLearnerKeyAndResult(String learnerKey, StudyResult result);

    long countByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqual(String learnerKey, Instant answeredAt);

    long countByLearnerProfileLearnerKeyAndResultAndAnsweredAtGreaterThanEqual(
            String learnerKey, StudyResult result, Instant answeredAt);
    List<QuizAttempt> findByLearnerProfileLearnerKeyOrderByAnsweredAtDesc(String learnerKey, Pageable pageable);

    List<QuizAttempt> findByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqualOrderByAnsweredAtAsc(
            String learnerKey, Instant startedAt);

    @Query("select attempt from QuizAttempt attempt where attempt.learnerProfile.learnerKey=:learnerKey and attempt.answeredAt>=:startedAt and (attempt.streakEligible=true or attempt.streakEligible is null) order by attempt.answeredAt")
    List<QuizAttempt> findStreakEligibleSince(@Param("learnerKey") String learnerKey, @Param("startedAt") Instant startedAt);

    List<QuizAttempt> findByLearnerProfileLearnerKeyAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThanOrderByAnsweredAtAsc(
            String learnerKey, Instant startedAt, Instant endedAt);

    @Query("""
            select new com.japanese.learning.dto.WeeklyQuizAggregate(count(attempt),
                coalesce(sum(case when attempt.result = :correct then 1 else 0 end), 0),
                coalesce(sum(case when attempt.result = :incorrect then 1 else 0 end), 0),
                coalesce(sum(attempt.earnedExperience), 0))
            from QuizAttempt attempt where attempt.learnerProfile.learnerKey = :learnerKey
              and attempt.answeredAt >= :start and attempt.answeredAt < :end
            """)
    WeeklyQuizAggregate summarizeWeeklyQuiz(@Param("learnerKey") String learnerKey, @Param("start") Instant start,
                                             @Param("end") Instant end, @Param("correct") StudyResult correct,
                                             @Param("incorrect") StudyResult incorrect);
}
