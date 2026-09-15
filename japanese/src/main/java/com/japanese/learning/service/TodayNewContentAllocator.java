package com.japanese.learning.service;

import java.time.LocalDate;

/** Deterministically balances today's word and grammar slots within their independent caps. */
final class TodayNewContentAllocator {

    private TodayNewContentAllocator() {
    }

    static Allocation allocate(int slots, int wordLimit, int grammarLimit,
            long wordsUsed, long grammarUsed, int wordAvailable, int grammarAvailable,
            String learnerKey, LocalDate date) {
        int safeSlots = Math.max(slots, 0);
        int wordCapacity = Math.min(remaining(wordLimit, wordsUsed), Math.max(wordAvailable, 0));
        int grammarCapacity = Math.min(remaining(grammarLimit, grammarUsed), Math.max(grammarAvailable, 0));
        if (safeSlots == 0 || wordCapacity + grammarCapacity == 0) {
            return new Allocation(0, 0);
        }

        int words = 0;
        int grammar = 0;
        if (safeSlots >= 2 && wordCapacity > 0 && grammarCapacity > 0) {
            words++;
            grammar++;
        }

        while (words + grammar < safeSlots
                && (words < wordCapacity || grammar < grammarCapacity)) {
            if (words >= wordCapacity) {
                grammar++;
            } else if (grammar >= grammarCapacity) {
                words++;
            } else if (preferWord(wordLimit, grammarLimit, wordsUsed + words, grammarUsed + grammar,
                    learnerKey, date)) {
                words++;
            } else {
                grammar++;
            }
        }
        return new Allocation(words, grammar);
    }

    private static int remaining(int limit, long used) {
        return (int) Math.max(Math.min((long) Math.max(limit, 0) - Math.max(used, 0), Integer.MAX_VALUE), 0);
    }

    private static boolean preferWord(int wordLimit, int grammarLimit, long wordsUsed, long grammarUsed,
            String learnerKey, LocalDate date) {
        if (wordLimit <= 0) return false;
        if (grammarLimit <= 0) return true;
        long wordProgress = wordsUsed * grammarLimit;
        long grammarProgress = grammarUsed * wordLimit;
        if (wordProgress != grammarProgress) return wordProgress < grammarProgress;
        String stableKey = (learnerKey == null ? "" : learnerKey) + "|" + date;
        return Math.floorMod(stableKey.hashCode(), 2) == 0;
    }

    record Allocation(int wordCount, int grammarCount) {
        int total() {
            return wordCount + grammarCount;
        }
    }
}
