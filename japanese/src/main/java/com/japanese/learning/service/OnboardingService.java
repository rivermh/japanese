package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.repository.CategoryRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.learning.dto.OnboardingOption;
import com.japanese.learning.dto.OnboardingOptions;
import com.japanese.learning.dto.OnboardingPreset;
import com.japanese.learning.dto.OnboardingRequest;
import com.japanese.learning.dto.OnboardingStatus;
import com.japanese.learning.dto.StudyPreferences;
import com.japanese.learning.dto.LearningScope;
import com.japanese.learning.repository.LearnerProfileRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
    private static final List<OnboardingPreset> PRESETS = List.of(
            new OnboardingPreset("light", "가볍게", 3, 2, 5),
            new OnboardingPreset("normal", "보통", 5, 5, 10),
            new OnboardingPreset("focus", "집중", 10, 5, 15));

    private final LearnerProfileRepository profiles;
    private final LevelRepository levels;
    private final CategoryRepository categories;
    private final com.japanese.content.repository.ContentItemRepository contentItems;
    private final LearningService learningService;

    public OnboardingService(LearnerProfileRepository profiles, LevelRepository levels,
                             CategoryRepository categories, com.japanese.content.repository.ContentItemRepository contentItems,
                             LearningService learningService) {
        this.profiles = profiles;
        this.levels = levels;
        this.categories = categories;
        this.contentItems = contentItems;
        this.learningService = learningService;
    }

    @Transactional(readOnly = true)
    public OnboardingStatus status(UserAccount account) {
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElse(null);
        boolean required = profile != null && profile.requiresOnboarding();
        // Merely opening an incomplete onboarding must not create a partial preference row.
        StudyPreferences plan = required
                ? new StudyPreferences(new LearningScope(List.of(), List.of()), 5, 5)
                : learningService.studyPreferences(account);
        return new OnboardingStatus(required, options(), plan);
    }

    @Transactional(readOnly = true)
    public OnboardingOptions options() {
        List<OnboardingOption> jlpt = levels.findAllByOrderBySystemAscCodeAsc().stream()
                .filter(level -> "JLPT".equals(level.getSystem()))
                .sorted(Comparator.comparingInt((com.japanese.content.entity.Level level) -> jlptRank(level.getCode())).reversed())
                .map(level -> new OnboardingOption(level.getCode(), level.getName())).toList();
        List<OnboardingOption> actualCategories = categories.findAllByOrderByNameAsc().stream()
                .filter(category -> contentItems.countByPublishedTrueAndCategoriesSlug(category.getSlug()) > 0)
                .map(category -> new OnboardingOption(category.getSlug(), category.getName())).toList();
        return new OnboardingOptions(jlpt, actualCategories, PRESETS);
    }

    @Transactional
    public void complete(UserAccount account, OnboardingRequest request) {
        var profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        OnboardingOptions options = options();
        String target = resolveTarget(request == null ? null : request.targetLevel(), options);
        LearningAmounts amounts = amounts(request);
        List<String> selectedCategories = request == null || request.categories() == null ? List.of()
                : request.categories().stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        if (!options.categories().stream().map(OnboardingOption::key).collect(java.util.stream.Collectors.toSet())
                .containsAll(selectedCategories)) {
            throw new IllegalArgumentException("선택한 학습 분야를 찾을 수 없습니다.");
        }
        // Current level is a recommendation-only answer. The target level becomes the initial scope.
        learningService.updateLearningScope(account, List.of("JLPT:" + target), selectedCategories);
        learningService.updateNewContentLimits(account, amounts.words(), amounts.grammar());
        learningService.updateDailyGoal(account, amounts.goal());
        profile.completeOnboarding();
    }

    private String resolveTarget(String requested, OnboardingOptions options) {
        if (options.jlptLevels().isEmpty()) {
            throw new IllegalStateException("선택 가능한 JLPT 레벨이 없습니다.");
        }
        if (requested != null && !requested.isBlank()) {
            String target = requested.trim().toUpperCase(Locale.ROOT);
            if (options.jlptLevels().stream().anyMatch(option -> option.key().equals(target))) return target;
            throw new IllegalArgumentException("선택한 목표 레벨을 찾을 수 없습니다.");
        }
        // “잘 모르겠음” starts with the lowest available JLPT range.
        return options.jlptLevels().get(0).key();
    }

    private LearningAmounts amounts(OnboardingRequest request) {
        if (request != null && request.preset() != null && !request.preset().isBlank()) {
            return PRESETS.stream().filter(preset -> preset.key().equalsIgnoreCase(request.preset().trim()))
                    .findFirst().map(preset -> new LearningAmounts(preset.dailyNewWordLimit(), preset.dailyNewGrammarLimit(), preset.dailyGoal()))
                    .orElseThrow(() -> new IllegalArgumentException("선택한 학습량 프리셋을 찾을 수 없습니다."));
        }
        if (request == null || request.dailyNewWordLimit() == null || request.dailyNewGrammarLimit() == null) {
            return new LearningAmounts(5, 5, 10);
        }
        int words = request.dailyNewWordLimit();
        int grammar = request.dailyNewGrammarLimit();
        int goal = request.dailyGoal() == null ? Math.max(words + grammar, 1) : request.dailyGoal();
        if (words < 0 || grammar < 0 || words > 50 || grammar > 50 || goal < 1 || goal > 100) {
            throw new IllegalArgumentException("학습량은 허용 범위 안에서 선택해 주세요.");
        }
        return new LearningAmounts(words, grammar, goal);
    }

    private int jlptRank(String code) {
        try { return Integer.parseInt(code.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private record LearningAmounts(int words, int grammar, int goal) { }
}
