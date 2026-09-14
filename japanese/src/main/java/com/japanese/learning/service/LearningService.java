package com.japanese.learning.service;

import com.japanese.content.dto.ContentSummary;
import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.CategoryRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.BookmarkRepository;
import com.japanese.content.entity.Bookmark;
import com.japanese.learning.character.CharacterCatalog;
import com.japanese.learning.character.CharacterDefinition;
import com.japanese.learning.dto.CharacterStatus;
import com.japanese.learning.dto.DailyLearningProgress;
import com.japanese.learning.dto.StudyAnswer;
import com.japanese.learning.dto.StudyHistoryEntry;
import com.japanese.learning.dto.StudyOverview;
import com.japanese.learning.dto.TodayLearningPlan;
import com.japanese.learning.dto.QuizHistoryEntry;
import com.japanese.learning.entity.LearnerProfile;
import com.japanese.learning.entity.CharacterGrowthStage;
import com.japanese.learning.entity.LearningProgress;
import com.japanese.learning.entity.LearnerStudyPreference;
import com.japanese.learning.entity.QuizAttempt;
import com.japanese.learning.entity.StudyRecord;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import com.japanese.learning.repository.QuizAttemptRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.LearnerStudyPreferenceRepository;
import com.japanese.learning.repository.StudyQueueRepository;
import com.japanese.learning.dto.LearningScope;
import com.japanese.learning.dto.StudyPreferences;
import com.japanese.learning.dto.ContentLearningStatus;
import com.japanese.learning.entity.RelearningTarget;
import com.japanese.learning.entity.StudyActivityType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {

    private static final int MAX_CARDS = 20;
    private static final int MAX_HISTORY_ITEMS = 20;
    static final int CORRECT_EXP = 10;
    static final int INCORRECT_EXP = 2;
    private static final int MAX_NEW_CONTENT_LIMIT = 50;

    private final ContentItemRepository contentItemRepository;
    private final LearnerProfileRepository learnerProfileRepository;
    private final StudyRecordRepository studyRecordRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final LearnerStudyPreferenceRepository preferenceRepository;
    private final LevelRepository levelRepository;
    private final CategoryRepository categoryRepository;
    private final BookmarkRepository bookmarkRepository;
    private final StudyQueueRepository studyQueueRepository;
    private final CharacterCatalog characterCatalog;
    private final com.japanese.learning.character.HaruPresentationService haruPresentation;
    private final StreakService streakService;
    private final int dailyGoal;
    private final ZoneId learningZone;

    public LearningService(
            ContentItemRepository contentItemRepository,
            LearnerProfileRepository learnerProfileRepository,
            StudyRecordRepository studyRecordRepository,
            QuizAttemptRepository quizAttemptRepository,
            LearningProgressRepository learningProgressRepository,
            LearnerStudyPreferenceRepository preferenceRepository,
            LevelRepository levelRepository,
            CategoryRepository categoryRepository,
            BookmarkRepository bookmarkRepository,
            StudyQueueRepository studyQueueRepository,
            CharacterCatalog characterCatalog,
            com.japanese.learning.character.HaruPresentationService haruPresentation,
            StreakService streakService,
            @Value("${japanese.learning.daily-goal:10}") int dailyGoal,
            @Value("${japanese.learning.time-zone:Asia/Seoul}") String learningTimeZone
    ) {
        this.contentItemRepository = contentItemRepository;
        this.learnerProfileRepository = learnerProfileRepository;
        this.studyRecordRepository = studyRecordRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.preferenceRepository = preferenceRepository;
        this.levelRepository = levelRepository;
        this.categoryRepository = categoryRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.studyQueueRepository = studyQueueRepository;
        this.characterCatalog = characterCatalog;
        this.haruPresentation = haruPresentation;
        this.streakService = streakService;
        this.dailyGoal = Math.max(dailyGoal, 1);
        this.learningZone = ZoneId.of(learningTimeZone);
    }

    @Transactional
    public StudyOverview overview(UserAccount account) {
        LearnerProfile profile = profile(account);
        return toOverview(profile);
    }

    @Transactional(readOnly = true)
    public ContentLearningStatus contentLearningStatus(UserAccount account, String slug) {
        var profile = learnerProfileRepository.findByUserAccountLoginId(account.getLoginId());
        if (profile.isEmpty()) {
            return ContentLearningStatus.notStarted();
        }
        var content = contentItemRepository.findBySlugAndPublishedTrue(slug);
        if (content.isEmpty()) {
            throw new NoSuchElementException("Published content not found: " + slug);
        }
        return learningProgressRepository.findByLearnerProfileLearnerKeyAndContentItemId(
                        profile.get().getLearnerKey(), content.get().getId())
                .map(progress -> new ContentLearningStatus(
                        progress.getLearningState().name(),
                        learningStateLabel(progress.getLearningState()),
                        progress.getNextReviewAt()))
                .orElseGet(ContentLearningStatus::notStarted);
    }

    @Transactional(readOnly = true)
    public List<ContentSummary> cards(UserAccount account, String levelCode, ContentType type, boolean reviewOnly) {
        LearnerProfile profile = profile(account);
        Set<Long> levelIds = levelIds(levelCode);
        List<ContentItem> dueItems = learningProgressRepository.findDueForLearner(
                        profile.getLearnerKey(), Instant.now(), type, !levelIds.isEmpty(), queryIds(levelIds),
                        false, queryIds(Set.of()), PageRequest.of(0, MAX_CARDS)).stream()
                .map(LearningProgress::getContentItem)
                .toList();
        if (reviewOnly) {
            return dueItems.stream().map(this::toSummary).toList();
        }
        List<ContentItem> newItems = newItems(profile.getLearnerKey(), type, levelIds, Set.of(), MAX_CARDS);
        return java.util.stream.Stream.concat(dueItems.stream(), newItems.stream())
                .limit(MAX_CARDS)
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TodayLearningPlan todayPlan(UserAccount account) {
        LearnerProfile profile = profile(account);
        LearnerStudyPreference preference = preferenceOrDefault(profile);
        Set<Long> levelIds = new LinkedHashSet<>(preference.getLevelIds());
        Set<Long> categoryIds = new LinkedHashSet<>(preference.getCategoryIds());
        int totalDueReviewCount = (int) Math.min(Integer.MAX_VALUE, learningProgressRepository.countDueForLearner(
                profile.getLearnerKey(), Instant.now(), !levelIds.isEmpty(), queryIds(levelIds),
                !categoryIds.isEmpty(), queryIds(categoryIds)));
        int remaining = (int) Math.min(MAX_CARDS, todayProgress(account).remaining());
        if (remaining <= 0) return new TodayLearningPlan(List.of(), 0, 0, 0, totalDueReviewCount, totalDueReviewCount);
        List<ContentItem> reviewItems = learningProgressRepository.findDueForLearner(
                        profile.getLearnerKey(), Instant.now(), null, !levelIds.isEmpty(), queryIds(levelIds),
                        !categoryIds.isEmpty(), queryIds(categoryIds), PageRequest.of(0, remaining)).stream()
                .map(LearningProgress::getContentItem).toList();
        int reviewCount = reviewItems.size();
        List<ContentItem> selected = new java.util.ArrayList<>(reviewItems);
        int newSlots = remaining - reviewCount;
        Instant todayStart = startOfToday();
        long newWordsToday = studyRecordRepository.countByLearnerAndStudiedAtGreaterThanEqualAndActivityType(
                profile.getLearnerKey(), todayStart, StudyActivityType.NEW, ContentType.WORD);
        long newGrammarToday = studyRecordRepository.countByLearnerAndStudiedAtGreaterThanEqualAndActivityType(
                profile.getLearnerKey(), todayStart, StudyActivityType.NEW, ContentType.GRAMMAR);
        // New-content settings are per-type caps. If one type has no available content,
        // leave that slot empty instead of silently changing the learner's chosen pace.
        int wordSlots = Math.min(newSlots, Math.max(preference.getDailyNewWordLimit() - (int) newWordsToday, 0));
        int grammarSlots = Math.min(newSlots - wordSlots,
                Math.max(preference.getDailyNewGrammarLimit() - (int) newGrammarToday, 0));
        // A learner explicitly placing an item in the queue is an intentional override
        // of the general level/category scope. It remains behind due reviews and still
        // respects each content type's daily new-content cap.
        List<ContentItem> queuedWords = wordSlots == 0 ? List.of() : studyQueueRepository.findUnstartedPublishedForToday(
                account.getId(), profile.getLearnerKey(), ContentType.WORD, PageRequest.of(0, wordSlots));
        List<ContentItem> queuedGrammar = grammarSlots == 0 ? List.of() : studyQueueRepository.findUnstartedPublishedForToday(
                account.getId(), profile.getLearnerKey(), ContentType.GRAMMAR, PageRequest.of(0, grammarSlots));
        List<ContentItem> newWords = new java.util.ArrayList<>(queuedWords);
        List<ContentItem> newGrammar = new java.util.ArrayList<>(queuedGrammar);
        Set<Long> selectedNewContentIds = new LinkedHashSet<>();
        queuedWords.forEach(item -> selectedNewContentIds.add(item.getId()));
        queuedGrammar.forEach(item -> selectedNewContentIds.add(item.getId()));
        newWords.addAll(newItems(profile.getLearnerKey(), ContentType.WORD, levelIds, categoryIds,
                Math.max(wordSlots - queuedWords.size(), 0), selectedNewContentIds));
        selectedNewContentIds.addAll(newWords.stream().map(ContentItem::getId).toList());
        newGrammar.addAll(newItems(profile.getLearnerKey(), ContentType.GRAMMAR, levelIds, categoryIds,
                Math.max(grammarSlots - queuedGrammar.size(), 0), selectedNewContentIds));
        selected.addAll(newWords);
        selected.addAll(newGrammar);
        return new TodayLearningPlan(selected.stream().map(this::toSummary).toList(), reviewCount, newWords.size(), newGrammar.size(),
                totalDueReviewCount, Math.max(totalDueReviewCount - reviewCount, 0));
    }

    private List<ContentItem> newItems(String learnerKey, ContentType type, Set<Long> levelIds,
                                       Set<Long> categoryIds, int limit) {
        return newItems(learnerKey, type, levelIds, categoryIds, limit, Set.of());
    }

    private List<ContentItem> newItems(String learnerKey, ContentType type, Set<Long> levelIds,
                                       Set<Long> categoryIds, int limit, Set<Long> excludedContentIds) {
        if (limit <= 0) return List.of();
        return contentItemRepository.findNewPublishedForLearner(
                        learnerKey, type, !levelIds.isEmpty(), queryIds(levelIds),
                        !categoryIds.isEmpty(), queryIds(categoryIds),
                        PageRequest.of(0, Math.min(limit + excludedContentIds.size(), MAX_CARDS + MAX_NEW_CONTENT_LIMIT)))
                .getContent().stream()
                .filter(item -> !excludedContentIds.contains(item.getId()))
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public LearningScope learningScope(UserAccount account) {
        LearnerStudyPreference preference = preferenceOrDefault(profile(account));
        Set<Long> selectedLevelIds = preference.getLevelIds();
        Set<Long> selectedCategoryIds = preference.getCategoryIds();
        List<String> levelCodes = levelRepository.findAllById(selectedLevelIds).stream()
                .map(level -> level.getSystem() + ":" + level.getCode()).sorted().toList();
        List<String> categorySlugs = categoryRepository.findAllById(selectedCategoryIds).stream()
                .map(com.japanese.content.entity.Category::getSlug).sorted().toList();
        return new LearningScope(levelCodes, categorySlugs);
    }

    @Transactional
    public void updateLearningScope(UserAccount account, java.util.Collection<String> requestedLevels,
                                   java.util.Collection<String> requestedCategories) {
        LearnerProfile profile = profile(account);
        Set<String> levelKeys = normalize(requestedLevels);
        Set<String> categorySlugs = normalize(requestedCategories);
        var allLevels = levelRepository.findAll();
        var selectedLevels = allLevels.stream()
                .filter(level -> levelKeys.contains(level.getSystem() + ":" + level.getCode()))
                .toList();
        if (selectedLevels.size() != levelKeys.size()) {
            throw new IllegalArgumentException("선택한 학습 레벨을 찾을 수 없습니다.");
        }
        var allCategories = categoryRepository.findAll();
        var selectedCategories = allCategories.stream()
                .filter(category -> categorySlugs.contains(category.getSlug()))
                .toList();
        if (selectedCategories.size() != categorySlugs.size()) {
            throw new IllegalArgumentException("선택한 학습 분야를 찾을 수 없습니다.");
        }
        preference(profile).replace(
                selectedLevels.stream().map(com.japanese.content.entity.Level::getId)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                selectedCategories.stream().map(com.japanese.content.entity.Category::getId)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    @Transactional(readOnly = true)
    public StudyPreferences studyPreferences(UserAccount account) {
        LearnerStudyPreference preference = preferenceOrDefault(profile(account));
        return new StudyPreferences(learningScope(account), preference.getDailyNewWordLimit(), preference.getDailyNewGrammarLimit());
    }

    @Transactional
    public void updateNewContentLimits(UserAccount account, int wordLimit, int grammarLimit) {
        if (wordLimit < 0 || wordLimit > MAX_NEW_CONTENT_LIMIT
                || grammarLimit < 0 || grammarLimit > MAX_NEW_CONTENT_LIMIT) {
            throw new IllegalArgumentException("새 단어와 문법 수는 각각 0~50 사이여야 합니다.");
        }
        preference(profile(account)).updateDailyNewLimits(wordLimit, grammarLimit);
    }

    @Transactional(readOnly = true)
    public List<ContentSummary> relearningCards(UserAccount account, RelearningTarget target) {
        LearnerProfile profile = profile(account);
        if (target == RelearningTarget.BOOKMARKS) {
            return bookmarkRepository.findByUserAccountLoginIdOrderByCreatedAtDesc(account.getLoginId()).stream()
                    .map(Bookmark::getContentItem)
                    .filter(ContentItem::isPublished)
                    .limit(MAX_CARDS)
                    .map(this::toSummary)
                    .toList();
        }
        Map<Long, ContentItem> uniqueItems = new LinkedHashMap<>();
        studyRecordRepository.findByLearnerProfileLearnerKeyAndResultOrderByStudiedAtDesc(
                        profile.getLearnerKey(), StudyResult.INCORRECT, PageRequest.of(0, 200))
                .forEach(record -> uniqueItems.putIfAbsent(record.getContentItem().getId(), record.getContentItem()));
        return uniqueItems.values().stream().filter(ContentItem::isPublished).limit(MAX_CARDS).map(this::toSummary).toList();
    }

    @Transactional
    public StudyAnswer answer(UserAccount account, String slug, StudyResult result) {
        return answer(account, slug, result, null, false);
    }

    @Transactional
    public StudyAnswer answer(UserAccount account, String slug, StudyResult result, String sessionKey, boolean retraining) {
        ContentItem content = contentItemRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> new NoSuchElementException("Published content not found: " + slug));
        LearnerProfile profile = profileForUpdate(account);
        String effectiveSessionKey = normalizeSessionKey(sessionKey);
        var duplicate = studyRecordRepository.findByLearnerProfileLearnerKeyAndSessionKeyAndContentItemId(
                profile.getLearnerKey(), effectiveSessionKey, content.getId());
        if (duplicate.isPresent()) {
            return new StudyAnswer(toSummary(content), duplicate.get().getResult(), 0, toOverview(profile));
        }
        var progress = learningProgressRepository.findByLearnerProfileLearnerKeyAndContentItemId(
                profile.getLearnerKey(), content.getId()).orElse(null);
        StudyActivityType activityType = retraining
                ? StudyActivityType.RETRAIN
                : progress == null ? StudyActivityType.NEW : StudyActivityType.REVIEW;
        int earnedExperience = retraining ? 0 : result == StudyResult.CORRECT ? CORRECT_EXP : INCORRECT_EXP;
        if (earnedExperience > 0) earnExperience(profile, earnedExperience);
        studyRecordRepository.save(new StudyRecord(profile, content, result, activityType, effectiveSessionKey));
        if (activityType == StudyActivityType.NEW) {
            studyQueueRepository.deleteByUserAccountLoginIdAndContentItemId(account.getLoginId(), content.getId());
        }
        streakService.recordActivity(profile);
        if (progress != null) progress.record(result);
        else learningProgressRepository.save(new LearningProgress(profile, content, result));
        return new StudyAnswer(toSummary(content), result, earnedExperience, toOverview(profile));
    }

    @Transactional
    public StudyOverview recordQuizAnswer(UserAccount account, Long questionSourceRecordId, boolean correct) {
        return recordQuizAward(account, questionSourceRecordId, correct, true).overview();
    }

    @Transactional
    public com.japanese.learning.dto.QuizAward recordQuizSessionAnswer(
            UserAccount account, Long questionSourceRecordId, boolean correct) {
        return recordQuizAward(account, questionSourceRecordId, correct, false);
    }

    private com.japanese.learning.dto.QuizAward recordQuizAward(
            UserAccount account, Long questionSourceRecordId, boolean correct, boolean recordStreak) {
        LearnerProfile profile = profileForUpdate(account);
        StudyResult result = correct ? StudyResult.CORRECT : StudyResult.INCORRECT;
        int earnedExperience = correct ? CORRECT_EXP : INCORRECT_EXP;
        earnExperience(profile, earnedExperience);
        quizAttemptRepository.save(new QuizAttempt(profile, questionSourceRecordId, result, earnedExperience, recordStreak));
        if (recordStreak) streakService.recordActivity(profile);
        return new com.japanese.learning.dto.QuizAward(toOverview(profile), earnedExperience);
    }

    @Transactional(readOnly = true)
    public List<StudyHistoryEntry> recentHistory(UserAccount account, int size) {
        LearnerProfile profile = profile(account);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_HISTORY_ITEMS);
        return studyRecordRepository.findByLearnerProfileLearnerKeyOrderByStudiedAtDesc(
                        profile.getLearnerKey(), PageRequest.of(0, normalizedSize))
                .stream()
                .map(record -> {
                    ContentItem item = record.getContentItem();
                    String title = item.getWord() == null ? item.getGrammar().getPattern() : item.getWord().getExpression();
                    String reading = item.getWord() == null ? null : item.getWord().getReading();
                    return new StudyHistoryEntry(
                            item.getSlug(), item.getType(), title, reading, record.getResult(), record.getStudiedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuizHistoryEntry> recentQuizHistory(UserAccount account, int size) {
        LearnerProfile profile = profile(account);
        return quizAttemptRepository.findByLearnerProfileLearnerKeyOrderByAnsweredAtDesc(
                        profile.getLearnerKey(), PageRequest.of(0, Math.min(Math.max(size, 1), MAX_HISTORY_ITEMS)))
                .stream().map(attempt -> new QuizHistoryEntry(attempt.getQuestionSourceRecordId(), attempt.getResult(), attempt.getAnsweredAt())).toList();
    }

    @Transactional(readOnly = true)
    public DailyLearningProgress todayProgress(UserAccount account) {
        LearnerProfile profile = profile(account);
        String learnerKey = profile.getLearnerKey();
        Instant todayStart = LocalDate.now(learningZone).atStartOfDay(learningZone).toInstant();
        long completed = studyRecordRepository.countRegularContentByLearnerAndStudiedAtGreaterThanEqual(
                learnerKey, todayStart, StudyActivityType.RETRAIN);
        long correctAnswers = studyRecordRepository.countRegularByLearnerAndResultAndStudiedAtGreaterThanEqual(
                learnerKey, todayStart, StudyResult.CORRECT, StudyActivityType.RETRAIN);
        // The daily learning goal measures learned content. Quiz attempts remain
        // visible in activity/statistics but do not consume the content goal.
        return new DailyLearningProgress(profile.getDailyGoal() == null ? dailyGoal : profile.getDailyGoal(), completed, correctAnswers);
    }

    @Transactional
    public void updateDailyGoal(UserAccount account, int requestedGoal) {
        if (requestedGoal < 1 || requestedGoal > 100) {
            throw new IllegalArgumentException("일일 목표는 1~100 사이여야 합니다.");
        }
        profile(account).updateDailyGoal(requestedGoal);
    }

    @Transactional
    public void updateCharacter(UserAccount account, String requestedCharacterKey) {
        CharacterDefinition character = characterCatalog.find(requestedCharacterKey)
                .orElseThrow(() -> new IllegalArgumentException("선택할 수 없는 캐릭터입니다."));
        profile(account).updateCharacterKey(character.key());
    }

    @Transactional
    public void acknowledgeGrowth(UserAccount account) {
        profileForUpdate(account).acknowledgeGrowth();
    }

    @Transactional
    public boolean claimGrowthPresentation(UserAccount account, String stageKey) {
        return profileForUpdate(account).claimGrowthPresentation(stageKey);
    }

    @Transactional
    public void acknowledgeGrowth(UserAccount account, String stageKey) {
        profileForUpdate(account).acknowledgeGrowth(stageKey);
    }

    private LearnerProfile profile(UserAccount account) {
        return learnerProfileRepository.findByUserAccountLoginId(account.getLoginId())
                .orElseGet(() -> learnerProfileRepository.save(new LearnerProfile(account, dailyGoal, characterCatalog.defaultCharacter().key())));
    }

    private LearnerProfile profileForUpdate(UserAccount account) {
        return learnerProfileRepository.findByUserAccountLoginIdForUpdate(account.getLoginId())
                .orElseGet(() -> profile(account));
    }

    private LearnerStudyPreference preference(LearnerProfile profile) {
        return preferenceRepository.findByLearnerProfileId(profile.getId())
                .orElseGet(() -> preferenceRepository.save(new LearnerStudyPreference(profile)));
    }

    /** Read paths must support accounts created before preference rows existed. */
    private LearnerStudyPreference preferenceOrDefault(LearnerProfile profile) {
        return preferenceRepository.findByLearnerProfileId(profile.getId())
                .orElseGet(() -> new LearnerStudyPreference(profile));
    }

    private Set<Long> levelIds(String levelCode) {
        if (levelCode == null || levelCode.isBlank()) return Set.of();
        return levelRepository.findAll().stream()
                .filter(level -> levelCode.trim().equals(level.getCode()))
                .map(com.japanese.content.entity.Level::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> normalize(java.util.Collection<String> values) {
        if (values == null) return Set.of();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private java.util.Collection<Long> queryIds(Set<Long> values) {
        return values.isEmpty() ? java.util.List.of(-1L) : values;
    }

    private Instant startOfToday() {
        return LocalDate.now(learningZone).atStartOfDay(learningZone).toInstant();
    }

    private String normalizeSessionKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) return UUID.randomUUID().toString();
        String normalized = sessionKey.trim();
        if (normalized.length() > 120) throw new IllegalArgumentException("학습 세션 키가 너무 깁니다.");
        return normalized;
    }

    private String learningStateLabel(com.japanese.learning.entity.LearningState state) {
        return switch (state) {
            case LEARNING -> "학습 중";
            case REVIEW -> "복습 중";
            case MASTERED -> "숙달";
            case SUSPENDED -> "학습 보류";
        };
    }

    private StudyOverview toOverview(LearnerProfile profile) {
        long correctAnswers = studyRecordRepository.countByLearnerProfileLearnerKeyAndResult(
                profile.getLearnerKey(), StudyResult.CORRECT);
        long incorrectAnswers = studyRecordRepository.countByLearnerProfileLearnerKeyAndResult(
                profile.getLearnerKey(), StudyResult.INCORRECT);
        correctAnswers += quizAttemptRepository.countByLearnerProfileLearnerKeyAndResult(
                profile.getLearnerKey(), StudyResult.CORRECT);
        incorrectAnswers += quizAttemptRepository.countByLearnerProfileLearnerKeyAndResult(
                profile.getLearnerKey(), StudyResult.INCORRECT);
        long dueReviewCount = learningProgressRepository
                .countByLearnerProfileLearnerKeyAndNextReviewAtLessThanEqual(profile.getLearnerKey(), Instant.now());
        CharacterDefinition character = characterCatalog.resolve(profile.getCharacterKey());
        return new StudyOverview(
                profile.getDisplayName(),
                haruPresentation.status(profile, character),
                correctAnswers + incorrectAnswers,
                correctAnswers,
                dueReviewCount);
    }

    private void earnExperience(LearnerProfile profile, int earnedExperience) {
        CharacterGrowthStage before = CharacterGrowthStage.forExperience(profile.getExperience());
        profile.addExperience(earnedExperience);
        CharacterGrowthStage after = CharacterGrowthStage.forExperience(profile.getExperience());
        if (after != before) {
            profile.markGrowthPending(after.getKey());
        }
    }

    private ContentSummary toSummary(ContentItem item) {
        String title;
        String reading = null;
        String description;
        if (item.getWord() != null) {
            title = item.getWord().getExpression();
            reading = item.getWord().getReading();
            description = item.getWord().getMeanings().stream().findFirst()
                    .map(meaning -> meaning.getText()).orElse("");
        } else {
            title = item.getGrammar().getPattern();
            description = item.getGrammar().getExplanation();
        }
        return new ContentSummary(
                item.getId(), item.getSlug(), item.getType(), title, reading, description,
                item.getLevels().stream()
                        .map(level -> new ContentSummary.LevelSummary(level.getSystem(), level.getCode(), level.getName()))
                        .toList(),
                item.getCategories().stream()
                        .map(category -> new ContentSummary.CategorySummary(category.getSlug(), category.getName()))
                        .toList());
    }

}
