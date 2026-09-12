package com.japanese.learning.entity;

import com.japanese.content.entity.Category;
import com.japanese.content.entity.Level;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A learner's content scope. An empty collection means "all" so adding a new
 * level or category does not silently hide it from existing learners.
 */
@Entity
@Table(name = "learner_study_preferences")
public class LearnerStudyPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "learner_profile_id", nullable = false, unique = true)
    @jakarta.persistence.OneToOne(fetch = FetchType.LAZY, optional = false)
    private LearnerProfile learnerProfile;

    @ElementCollection
    @CollectionTable(name = "learner_preference_levels",
            joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "level_id", nullable = false)
    private Set<Long> levelIds = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "learner_preference_categories",
            joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "category_id", nullable = false)
    private Set<Long> categoryIds = new LinkedHashSet<>();

    @Column(name = "daily_new_word_limit")
    private Integer dailyNewWordLimit = 5;

    @Column(name = "daily_new_grammar_limit")
    private Integer dailyNewGrammarLimit = 5;

    protected LearnerStudyPreference() {
    }

    public LearnerStudyPreference(LearnerProfile learnerProfile) {
        this.learnerProfile = learnerProfile;
    }

    public Set<Long> getLevelIds() {
        return levelIds;
    }

    public Set<Long> getCategoryIds() {
        return categoryIds;
    }

    public int getDailyNewWordLimit() {
        return dailyNewWordLimit == null ? 5 : dailyNewWordLimit;
    }

    public int getDailyNewGrammarLimit() {
        return dailyNewGrammarLimit == null ? 5 : dailyNewGrammarLimit;
    }

    public void replace(Set<Long> levelIds, Set<Long> categoryIds) {
        this.levelIds.clear();
        this.levelIds.addAll(levelIds);
        this.categoryIds.clear();
        this.categoryIds.addAll(categoryIds);
    }

    public void updateDailyNewLimits(int wordLimit, int grammarLimit) {
        this.dailyNewWordLimit = wordLimit;
        this.dailyNewGrammarLimit = grammarLimit;
    }
}
