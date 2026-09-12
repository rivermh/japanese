package com.japanese.learning.dto;

import java.util.List;

public record StreakStatus(int currentStreak, int longestStreak, boolean studiedToday, List<StreakDay> recentDays) {
}
