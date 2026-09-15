package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.ContentType;
import com.japanese.learning.dto.LevelStudyProgress;
import com.japanese.learning.entity.LearnerProfile;
import com.japanese.learning.entity.LearningProgress;
import com.japanese.learning.entity.LearningState;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearnerStudyPreferenceRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One definition of an active review candidate shared by user-facing read models. */
@Service
public class DueReviewQueryService {
    private final LearningProgressRepository progress;
    private final LearnerProfileRepository profiles;
    private final LearnerStudyPreferenceRepository preferences;

    public DueReviewQueryService(LearningProgressRepository progress,
            LearnerProfileRepository profiles, LearnerStudyPreferenceRepository preferences) {
        this.progress = progress;
        this.profiles = profiles;
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public DueReviewCriteria currentScope(UserAccount account, Instant asOf) {
        LearnerProfile profile = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        return currentScope(profile, asOf);
    }

    @Transactional(readOnly = true)
    public DueReviewCriteria currentScope(LearnerProfile profile, Instant asOf) {
        var preference = preferences.findByLearnerProfileId(profile.getId());
        Collection<Long> levels = preference.<Collection<Long>>map(value -> value.getLevelIds()).orElseGet(List::of);
        Collection<Long> categories = preference.<Collection<Long>>map(value -> value.getCategoryIds()).orElseGet(List::of);
        return new DueReviewCriteria(profile.getLearnerKey(), asOf, levels, categories);
    }

    public DueReviewCriteria explicitScope(String learnerKey, Instant asOf,
            Collection<Long> levelIds, Collection<Long> categoryIds) {
        return new DueReviewCriteria(learnerKey, asOf, levelIds, categoryIds);
    }

    @Transactional(readOnly = true)
    public long countDue(DueReviewCriteria criteria) {
        return progress.countEligibleDue(criteria.learnerKey(), criteria.asOf(), LearningState.SUSPENDED,
                criteria.filterLevels(), criteria.queryLevelIds(),
                criteria.filterCategories(), criteria.queryCategoryIds());
    }

    @Transactional(readOnly = true)
    public List<LearningProgress> findDue(DueReviewCriteria criteria, ContentType type, Pageable pageable) {
        return progress.findEligibleDue(criteria.learnerKey(), criteria.asOf(), type, LearningState.SUSPENDED,
                criteria.filterLevels(), criteria.queryLevelIds(),
                criteria.filterCategories(), criteria.queryCategoryIds(), pageable);
    }

    @Transactional(readOnly = true)
    public Instant findNextScheduledAt(DueReviewCriteria criteria) {
        return progress.findNextEligibleReviewAt(criteria.learnerKey(), LearningState.SUSPENDED,
                criteria.filterLevels(), criteria.queryLevelIds(),
                criteria.filterCategories(), criteria.queryCategoryIds());
    }

    @Transactional(readOnly = true)
    public long countScheduledBetween(DueReviewCriteria criteria, Instant start, Instant end) {
        return progress.countEligibleScheduledBetween(criteria.learnerKey(), start, end, LearningState.SUSPENDED,
                criteria.filterLevels(), criteria.queryLevelIds(),
                criteria.filterCategories(), criteria.queryCategoryIds());
    }

    @Transactional(readOnly = true)
    public List<LevelStudyProgress> summarizeByJlptLevel(UserAccount account, Instant asOf) {
        String learnerKey = profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow().getLearnerKey();
        return progress.summarizeEligibleByJlptLevel(learnerKey, asOf, LearningState.SUSPENDED);
    }
}
