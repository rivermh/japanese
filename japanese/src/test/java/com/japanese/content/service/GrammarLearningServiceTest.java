package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Grammar;
import com.japanese.content.entity.GrammarConfirmationType;
import com.japanese.content.entity.GrammarRelationType;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.GrammarComparisonRepository;
import com.japanese.content.repository.GrammarConfirmationQuestionRepository;
import com.japanese.content.repository.GrammarEnrichmentRepository;
import com.japanese.content.repository.GrammarRelationRepository;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.GrammarConfirmationAttemptRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import com.japanese.learning.service.LearningService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class GrammarLearningServiceTest {
    @Autowired private SampleContentDataLoader sample;
    @Autowired private ContentItemRepository contents;
    @Autowired private UserAccountRepository accounts;
    @Autowired private GrammarCurationService curation;
    @Autowired private GrammarLearningService grammarLearning;
    @Autowired private GrammarEnrichmentRepository enrichments;
    @Autowired private GrammarRelationRepository relations;
    @Autowired private GrammarComparisonRepository comparisons;
    @Autowired private GrammarConfirmationQuestionRepository questions;
    @Autowired private GrammarConfirmationAttemptRepository attempts;
    @Autowired private LearningService learning;
    @Autowired private StudyRecordRepository records;
    @Autowired private LearningProgressRepository progress;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        if (contents.findBySlug("noni").isEmpty()) {
            ContentItem item = new ContentItem("noni", ContentType.GRAMMAR, "test-curated", true);
            item.attachGrammar(new Grammar("のに", "test explanation", "plain form + のに"));
            contents.saveAndFlush(item);
        }
    }

    @Test
    void pendingCuratedDataStaysHiddenAndApprovedDataIsExposedWithComparison() {
        var pending = curation.saveEnrichment("temo-ii", "editorial:test", "nuance", "usage", null, null, null);
        assertThat(grammarLearning.publicDetails(grammar("temo-ii")).enrichment()).isNull();

        pending.approveForPublication(); enrichments.saveAndFlush(pending);
        var relation = curation.saveRelation("temo-ii", "noni", GrammarRelationType.CONFUSABLE, "editorial:test");
        relation.approveForPublication(); relations.saveAndFlush(relation);
        var comparison = curation.saveComparison(relation.getId(), "editorial:test", "summary", "difference", "usage difference", "confusion");
        assertThat(grammarLearning.publicComparison("temo-ii", "noni")).isEmpty();
        comparison.approveForPublication(); comparisons.saveAndFlush(comparison);

        var details = grammarLearning.publicDetails(grammar("temo-ii"));
        assertThat(details.enrichment().usageNote()).isEqualTo("usage");
        assertThat(details.related()).singleElement().satisfies(related -> {
            assertThat(related.slug()).isEqualTo("noni");
            assertThat(related.comparisonAvailable()).isTrue();
        });
        assertThat(grammarLearning.publicComparison("noni", "temo-ii").orElseThrow().keyDifference()).isEqualTo("difference");
    }

    @Test
    void confirmationIsSupplementaryAndDoesNotCreateRegularLearningActivity() {
        var question = curation.saveQuestion("temo-ii", GrammarConfirmationType.CONTEXT_GAP,
                "Choose the appropriate grammar", "ここで写真を撮っても＿＿。", "Reviewed explanation", "editorial:test",
                List.of(new GrammarCurationService.ChoiceDraft("いいです", true), new GrammarCurationService.ChoiceDraft("のに", false)));
        question.approveForPublication(); questions.saveAndFlush(question);
        UserAccount account = accounts.saveAndFlush(new UserAccount("grammar-check", "grammar-check@example.test", "hash", "Grammar", UserRole.USER));
        learning.overview(account);
        int experienceBefore = learning.overview(account).character().experience();
        long recordsBefore = records.count();
        long progressBefore = progress.count();

        var publicQuestion = grammarLearning.confirmationQuestions("temo-ii").get(0);
        var result = grammarLearning.answer(account, publicQuestion.id(), publicQuestion.choices().get(1).id());

        assertThat(result.correct()).isFalse();
        assertThat(result.explanation()).isEqualTo("Reviewed explanation");
        assertThat(attempts.count()).isEqualTo(1);
        assertThat(records.count()).isEqualTo(recordsBefore);
        assertThat(progress.count()).isEqualTo(progressBefore);
        assertThat(learning.overview(account).character().experience()).isEqualTo(experienceBefore);
        assertThat(grammarLearning.weaknesses(account)).singleElement().satisfies(weakness ->
                assertThat(weakness.slug()).isEqualTo("temo-ii"));
    }

    @Test
    void canonicalRelationRejectsSelfAndDoesNotDuplicateReversedPair() {
        assertThatThrownBy(() -> curation.saveRelation("temo-ii", "temo-ii", GrammarRelationType.SIMILAR, "editorial:test"))
                .isInstanceOf(IllegalArgumentException.class);
        var first = curation.saveRelation("temo-ii", "noni", GrammarRelationType.SIMILAR, "editorial:test");
        var reversed = curation.saveRelation("noni", "temo-ii", GrammarRelationType.SIMILAR, "editorial:test");
        assertThat(reversed.getId()).isEqualTo(first.getId());
    }

    @Test
    void curationQuestionUsesItsSourceReferenceAsAnIdempotencyKeyAndRemainsPending() {
        var first = curation.saveQuestion("temo-ii", GrammarConfirmationType.CONTEXT_GAP,
                "First version", "Context", "Explanation", "editorial:n5:temo-ii:01",
                List.of(new GrammarCurationService.ChoiceDraft("correct", true),
                        new GrammarCurationService.ChoiceDraft("wrong", false)));
        var revised = curation.saveQuestion("temo-ii", GrammarConfirmationType.CONTEXT_GAP,
                "Revised version", "New context", "New explanation", "editorial:n5:temo-ii:01",
                List.of(new GrammarCurationService.ChoiceDraft("new correct", true),
                        new GrammarCurationService.ChoiceDraft("new wrong", false)));

        assertThat(revised.getId()).isEqualTo(first.getId());
        assertThat(questions.findAll()).hasSize(1);
        assertThat(revised.getPrompt()).isEqualTo("Revised version");
        assertThat(revised.isPubliclyVisible()).isFalse();
        assertThat(revised.getChoices()).hasSize(2);
    }

    private Grammar grammar(String slug) {
        return contents.findBySlug(slug).orElseThrow().getGrammar();
    }
}
