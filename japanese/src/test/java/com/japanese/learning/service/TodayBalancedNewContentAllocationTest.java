package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.content.entity.Category;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Grammar;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.CategoryRepository;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.learning.entity.StudyResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class TodayBalancedNewContentAllocationTest {
    @Autowired private LearningService learning;
    @Autowired private DailyMissionService missions;
    @Autowired private UserAccountRepository accounts;
    @Autowired private ContentItemRepository contents;
    @Autowired private LevelRepository levels;
    @Autowired private CategoryRepository categories;

    @Test
    void preventsGrammarStarvationWithTenWordAndFiveGrammarCapacity() {
        Fixture fixture = fixture("balanced");
        createWords(fixture, 10);
        createGrammar(fixture, 5);
        learning.updateDailyGoal(fixture.account(), 10);
        learning.updateNewContentLimits(fixture.account(), 10, 5);

        var first = learning.todayPlan(fixture.account());
        var repeated = learning.todayPlan(fixture.account());

        assertThat(first.totalCount()).isEqualTo(10);
        assertThat(first.newWordCount()).isGreaterThan(0).isLessThanOrEqualTo(10);
        assertThat(first.newGrammarCount()).isGreaterThan(0).isLessThanOrEqualTo(5);
        assertThat(first.items()).extracting(item -> item.slug())
                .containsExactlyElementsOf(repeated.items().stream().map(item -> item.slug()).toList());
        assertThat(first.items().subList(0, first.newWordCount()))
                .allMatch(item -> item.type() == ContentType.WORD);
        assertThat(first.items().subList(first.newWordCount(), first.totalCount()))
                .allMatch(item -> item.type() == ContentType.GRAMMAR);
        assertThat(missions.today(fixture.account()).newWords().target()).isEqualTo(first.newWordCount());
        assertThat(missions.today(fixture.account()).newGrammar().target()).isEqualTo(first.newGrammarCount());
    }

    @Test
    void reallocatesShortCandidateCapacityWithoutLeavingTheSelectedScope() {
        Fixture fixture = fixture("short-word");
        createWords(fixture, 1);
        createGrammar(fixture, 8);
        createWords(fixture("outside-scope"), 12);
        learning.updateDailyGoal(fixture.account(), 8);
        learning.updateNewContentLimits(fixture.account(), 8, 8);

        var plan = learning.todayPlan(fixture.account());

        assertThat(plan.totalCount()).isEqualTo(8);
        assertThat(plan.newWordCount()).isEqualTo(1);
        assertThat(plan.newGrammarCount()).isEqualTo(7);
        assertThat(plan.items()).allMatch(item -> item.levels().stream()
                .anyMatch(level -> level.code().equals(fixture.level().getCode())));
    }

    @Test
    void subtractsNewContentAlreadyStudiedTodayFromEachDailyCap() {
        Fixture fixture = fixture("used-cap");
        List<String> words = createWords(fixture, 5);
        createGrammar(fixture, 5);
        learning.updateDailyGoal(fixture.account(), 10);
        learning.updateNewContentLimits(fixture.account(), 3, 3);
        learning.answer(fixture.account(), words.get(0), StudyResult.CORRECT, "used-word-1", false);
        learning.answer(fixture.account(), words.get(1), StudyResult.CORRECT, "used-word-2", false);

        var plan = learning.todayPlan(fixture.account());

        assertThat(plan.newWordCount()).isEqualTo(1);
        assertThat(plan.newGrammarCount()).isEqualTo(3);
        assertThat(plan.totalCount()).isEqualTo(4);
        assertThat(learning.todayProgress(fixture.account()).completed() + plan.totalCount()).isLessThanOrEqualTo(10);
    }

    @Test
    void neverExceedsTheTwentyCardPlanLimit() {
        Fixture fixture = fixture("max-cards");
        createWords(fixture, 24);
        createGrammar(fixture, 24);
        learning.updateDailyGoal(fixture.account(), 100);
        learning.updateNewContentLimits(fixture.account(), 50, 50);

        var plan = learning.todayPlan(fixture.account());

        assertThat(plan.totalCount()).isEqualTo(20);
        assertThat(plan.newWordCount()).isGreaterThan(0);
        assertThat(plan.newGrammarCount()).isGreaterThan(0);
    }

    private Fixture fixture(String prefix) {
        String suffix = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        UserAccount account = accounts.save(new UserAccount(
                "allocation-" + suffix, null, "hash", "Allocation", UserRole.USER));
        learning.overview(account);
        Level level = levels.save(new Level("TEST", suffix, suffix));
        Category category = categories.save(new Category("allocation-" + suffix, suffix));
        learning.updateLearningScope(account, List.of("TEST:" + suffix), List.of(category.getSlug()));
        return new Fixture(account, level, category, suffix);
    }

    private List<String> createWords(Fixture fixture, int count) {
        List<String> slugs = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String slug = fixture.prefix() + "-word-" + index;
            ContentItem item = new ContentItem(slug, ContentType.WORD, "test", true);
            Word word = new Word("word-" + index, "reading-" + index, "noun", null);
            word.addMeaning(new Meaning("ko", "meaning-" + index, 1));
            item.attachWord(word);
            attachScope(item, fixture);
            contents.save(item);
            slugs.add(slug);
        }
        contents.flush();
        return slugs;
    }

    private void createGrammar(Fixture fixture, int count) {
        for (int index = 0; index < count; index++) {
            ContentItem item = new ContentItem(fixture.prefix() + "-grammar-" + index,
                    ContentType.GRAMMAR, "test", true);
            item.attachGrammar(new Grammar("grammar-" + index, "explanation-" + index, null));
            attachScope(item, fixture);
            contents.save(item);
        }
        contents.flush();
    }

    private void attachScope(ContentItem item, Fixture fixture) {
        item.addLevel(fixture.level());
        item.addCategory(fixture.category());
    }

    private record Fixture(UserAccount account, Level level, Category category, String prefix) {
    }
}
