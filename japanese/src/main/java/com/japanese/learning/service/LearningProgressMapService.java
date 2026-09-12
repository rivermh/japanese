package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.ContentType;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.learning.dto.ContentTypeProgress;
import com.japanese.learning.dto.JlptLevelProgress;
import com.japanese.learning.dto.JlptProgressMap;
import com.japanese.learning.dto.LevelContentProgressAggregate;
import com.japanese.learning.dto.LearningScope;
import com.japanese.learning.entity.LearningState;
import com.japanese.learning.repository.LearnerProfileRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningProgressMapService {
    private final ContentItemRepository contentItems;
    private final LearnerProfileRepository profiles;
    private final LevelRepository levels;
    private final LearningService learningService;

    public LearningProgressMapService(ContentItemRepository contentItems, LearnerProfileRepository profiles,
                                      LevelRepository levels, LearningService learningService) {
        this.contentItems = contentItems;
        this.profiles = profiles;
        this.levels = levels;
        this.learningService = learningService;
    }

    @Transactional(readOnly = true)
    public JlptProgressMap map(UserAccount account) {
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        Map<String, List<LevelContentProgressAggregate>> aggregates = new LinkedHashMap<>();
        for (LevelContentProgressAggregate row : contentItems.summarizeJlptProgress(profile.getId(),
                LearningState.LEARNING, LearningState.REVIEW, LearningState.MASTERED, LearningState.SUSPENDED,
                com.japanese.learning.entity.StudyResult.CORRECT, com.japanese.learning.entity.StudyResult.INCORRECT)) {
            aggregates.computeIfAbsent(row.levelCode(), ignored -> new ArrayList<>()).add(row);
        }
        LearningScope scope = learningService.learningScope(account);
        List<JlptLevelProgress> result = new ArrayList<>();
        levels.findAllByOrderBySystemAscCodeAsc().stream().filter(level -> "JLPT".equals(level.getSystem()))
                .sorted(Comparator.comparingInt((com.japanese.content.entity.Level level) -> jlptOrder(level.getCode())).reversed())
                .forEach(level -> result.add(toLevel(level.getCode(), level.getName(),
                        scope.levelCodes().isEmpty() || scope.levelCodes().contains("JLPT:" + level.getCode()),
                        aggregates.getOrDefault(level.getCode(), List.of()))));
        return new JlptProgressMap(result);
    }

    @Transactional(readOnly = true)
    public JlptLevelProgress level(UserAccount account, String code) {
        return map(account).levels().stream().filter(level -> level.code().equalsIgnoreCase(code))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("JLPT 레벨을 찾을 수 없습니다."));
    }

    private JlptLevelProgress toLevel(String code, String name, boolean inScope, List<LevelContentProgressAggregate> rows) {
        Map<ContentType, LevelContentProgressAggregate> byType = new EnumMap<>(ContentType.class);
        rows.forEach(row -> byType.put(row.contentType(), row));
        List<ContentTypeProgress> types = new ArrayList<>();
        for (ContentType type : List.of(ContentType.WORD, ContentType.GRAMMAR)) {
            LevelContentProgressAggregate row = byType.get(type);
            long total = row == null ? 0 : row.total();
            long mastered = row == null ? 0 : row.mastered();
            int completion = total == 0 ? 0 : (int) Math.round((mastered * 100.0) / total);
            types.add(new ContentTypeProgress(type, total, row == null ? 0 : row.unstarted(), row == null ? 0 : row.learning(),
                    row == null ? 0 : row.review(), mastered, row == null ? 0 : row.suspended(), completion));
        }
        return new JlptLevelProgress(code, name, inScope, types);
    }

    private int jlptOrder(String code) {
        try { return Integer.parseInt(code.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException ignored) { return Integer.MAX_VALUE; }
    }
}
