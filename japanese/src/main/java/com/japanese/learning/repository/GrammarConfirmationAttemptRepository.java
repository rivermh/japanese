package com.japanese.learning.repository;

import com.japanese.learning.entity.GrammarConfirmationAttempt;
import com.japanese.learning.dto.WeaknessStudyAggregate;
import com.japanese.learning.dto.WeaknessStreakAggregate;
import com.japanese.learning.dto.WeeklyConfirmationAggregate;
import com.japanese.learning.entity.StudyResult;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrammarConfirmationAttemptRepository extends JpaRepository<GrammarConfirmationAttempt, Long> {
    List<GrammarConfirmationAttempt> findByLearnerProfileLearnerKeyOrderByAnsweredAtDesc(String learnerKey, Pageable pageable);

    @Query("""
            select new com.japanese.learning.dto.WeaknessStudyAggregate(
                grammar.contentItem.id,
                coalesce(sum(case when attempt.result = :incorrect then 1 else 0 end), 0),
                coalesce(sum(case when attempt.result = :correct then 1 else 0 end), 0),
                max(case when attempt.result = :incorrect then attempt.answeredAt else null end),
                max(case when attempt.result = :correct then attempt.answeredAt else null end))
            from GrammarConfirmationAttempt attempt
            join attempt.question question
            join question.grammar grammar
            where attempt.learnerProfile.learnerKey = :learnerKey
              and attempt.answeredAt >= :since
              and grammar.contentItem.published = true
            group by grammar.contentItem.id
            having sum(case when attempt.result = :incorrect then 1 else 0 end) > 0
            order by max(case when attempt.result = :incorrect then attempt.answeredAt else null end) desc
            """)
    List<WeaknessStudyAggregate> summarizeWeaknesses(
            @Param("learnerKey") String learnerKey,
            @Param("since") Instant since,
            @Param("correct") StudyResult correct,
            @Param("incorrect") StudyResult incorrect,
            Pageable pageable);

    @Query("""
            select new com.japanese.learning.dto.WeaknessStreakAggregate(grammar.contentItem.id, count(attempt))
            from GrammarConfirmationAttempt attempt
            join attempt.question question
            join question.grammar grammar
            where attempt.learnerProfile.learnerKey = :learnerKey
              and attempt.result = :incorrect
              and attempt.answeredAt >= :since
              and grammar.contentItem.published = true
              and not exists (
                  select later.id from GrammarConfirmationAttempt later
                  where later.learnerProfile.learnerKey = :learnerKey
                    and later.question.grammar.id = grammar.id
                    and later.result = :correct
                    and later.answeredAt > attempt.answeredAt)
            group by grammar.contentItem.id
            having count(attempt) >= :minimum
            order by count(attempt) desc
            """)
    List<WeaknessStreakAggregate> findCurrentIncorrectStreaks(
            @Param("learnerKey") String learnerKey,
            @Param("since") Instant since,
            @Param("correct") StudyResult correct,
            @Param("incorrect") StudyResult incorrect,
            @Param("minimum") long minimum,
            Pageable pageable);

    @Query("""
            select new com.japanese.learning.dto.WeeklyConfirmationAggregate(count(attempt),
                coalesce(sum(case when attempt.result = :correct then 1 else 0 end), 0),
                coalesce(sum(case when attempt.result = :incorrect then 1 else 0 end), 0))
            from GrammarConfirmationAttempt attempt where attempt.learnerProfile.learnerKey = :learnerKey
              and attempt.answeredAt >= :start and attempt.answeredAt < :end
            """)
    WeeklyConfirmationAggregate summarizeWeeklyConfirmation(@Param("learnerKey") String learnerKey,
            @Param("start") Instant start, @Param("end") Instant end, @Param("correct") StudyResult correct,
            @Param("incorrect") StudyResult incorrect);
}
