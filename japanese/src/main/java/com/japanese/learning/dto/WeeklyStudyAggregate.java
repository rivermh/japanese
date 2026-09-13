package com.japanese.learning.dto;

/** One date-range aggregate for regular study and RETRAIN records. */
public record WeeklyStudyAggregate(long newWordCount, long newGrammarCount, long reviewCount, long retrainCount,
                                   long regularCorrectCount, long regularIncorrectCount, long regularExperience) {
    public long regularAttemptCount() { return regularCorrectCount + regularIncorrectCount; }
    public int regularAccuracyPercent() { return regularAttemptCount() == 0 ? 0 : (int) (regularCorrectCount * 100 / regularAttemptCount()); }
    public long regularContentCount() { return newWordCount + newGrammarCount + reviewCount; }
}
