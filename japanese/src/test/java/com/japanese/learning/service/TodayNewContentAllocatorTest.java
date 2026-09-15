package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TodayNewContentAllocatorTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);

    @Test
    void guaranteesBothTypesAndWeightsRemainingSlotsByDailyProgress() {
        var allocation = allocate(10, 10, 5, 0, 0, 20, 20);

        assertThat(allocation.wordCount()).isGreaterThan(0);
        assertThat(allocation.grammarCount()).isGreaterThan(0);
        assertThat(allocation.total()).isEqualTo(10);
        assertThat(allocation.wordCount()).isGreaterThan(allocation.grammarCount());
    }

    @Test
    void redistributesWhenEitherCandidatePoolIsShort() {
        assertThat(allocate(10, 10, 10, 0, 0, 2, 20))
                .isEqualTo(new TodayNewContentAllocator.Allocation(2, 8));
        assertThat(allocate(10, 10, 10, 0, 0, 20, 1))
                .isEqualTo(new TodayNewContentAllocator.Allocation(9, 1));
    }

    @Test
    void respectsZeroCapsAndAvailableCapacity() {
        assertThat(allocate(8, 0, 8, 0, 0, 20, 20))
                .isEqualTo(new TodayNewContentAllocator.Allocation(0, 8));
        assertThat(allocate(8, 8, 0, 0, 0, 20, 20))
                .isEqualTo(new TodayNewContentAllocator.Allocation(8, 0));
    }

    @Test
    void oneSlotChoosesTheLessCompletedType() {
        assertThat(allocate(1, 10, 5, 8, 1, 10, 10))
                .isEqualTo(new TodayNewContentAllocator.Allocation(0, 1));
        assertThat(allocate(1, 10, 5, 1, 4, 10, 10))
                .isEqualTo(new TodayNewContentAllocator.Allocation(1, 0));
    }

    @Test
    void tieBreakIsStableForTheSameLearnerAndDate() {
        var first = allocate(1, 5, 5, 0, 0, 10, 10);
        var repeated = allocate(1, 5, 5, 0, 0, 10, 10);

        assertThat(repeated).isEqualTo(first);
        assertThat(first.total()).isEqualTo(1);
    }

    @Test
    void neverExceedsRemainingDailyCapsOrAvailableSlots() {
        var allocation = allocate(20, 10, 5, 8, 4, 20, 20);

        assertThat(allocation).isEqualTo(new TodayNewContentAllocator.Allocation(2, 1));
    }

    private TodayNewContentAllocator.Allocation allocate(int slots, int wordLimit, int grammarLimit,
            long wordsUsed, long grammarUsed, int wordAvailable, int grammarAvailable) {
        return TodayNewContentAllocator.allocate(slots, wordLimit, grammarLimit, wordsUsed, grammarUsed,
                wordAvailable, grammarAvailable, "learner-1", DATE);
    }
}
