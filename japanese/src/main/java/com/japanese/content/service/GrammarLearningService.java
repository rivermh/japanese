package com.japanese.content.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.dto.GrammarComparisonDetails;
import com.japanese.content.dto.GrammarConfirmationAnswerResult;
import com.japanese.content.dto.GrammarConfirmationQuestionDetails;
import com.japanese.content.dto.GrammarLearningDetails;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.Grammar;
import com.japanese.content.entity.GrammarComparison;
import com.japanese.content.entity.GrammarConfirmationQuestion;
import com.japanese.content.entity.GrammarEnrichment;
import com.japanese.content.entity.GrammarRelation;
import com.japanese.content.entity.ReviewStatus;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.GrammarComparisonRepository;
import com.japanese.content.repository.GrammarConfirmationQuestionRepository;
import com.japanese.content.repository.GrammarEnrichmentRepository;
import com.japanese.content.repository.GrammarRelationRepository;
import com.japanese.learning.dto.GrammarWeakness;
import com.japanese.learning.entity.GrammarConfirmationAttempt;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.GrammarConfirmationAttemptRepository;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read/write rules for reviewed grammar-specific material, independent from imported source fields. */
@Service
public class GrammarLearningService {
    private final ContentItemRepository contentItemRepository;
    private final GrammarEnrichmentRepository enrichmentRepository;
    private final GrammarRelationRepository relationRepository;
    private final GrammarComparisonRepository comparisonRepository;
    private final GrammarConfirmationQuestionRepository questionRepository;
    private final GrammarConfirmationAttemptRepository attemptRepository;
    private final LearnerProfileRepository profileRepository;
    private final LearningProgressRepository progressRepository;

    public GrammarLearningService(ContentItemRepository contentItemRepository,
                                  GrammarEnrichmentRepository enrichmentRepository,
                                  GrammarRelationRepository relationRepository,
                                  GrammarComparisonRepository comparisonRepository,
                                  GrammarConfirmationQuestionRepository questionRepository,
                                  GrammarConfirmationAttemptRepository attemptRepository,
                                  LearnerProfileRepository profileRepository,
                                  LearningProgressRepository progressRepository) {
        this.contentItemRepository = contentItemRepository;
        this.enrichmentRepository = enrichmentRepository;
        this.relationRepository = relationRepository;
        this.comparisonRepository = comparisonRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.profileRepository = profileRepository;
        this.progressRepository = progressRepository;
    }

    @Transactional(readOnly = true)
    public GrammarLearningDetails publicDetails(Grammar grammar) {
        Optional<GrammarEnrichment> enrichment = enrichmentRepository
                .findByGrammarIdAndPublishedTrueAndReviewStatus(grammar.getId(), ReviewStatus.APPROVED);
        List<GrammarLearningDetails.RelatedGrammar> related = relationRepository
                .findPublicForGrammar(grammar.getId(), ReviewStatus.APPROVED).stream()
                .map(relation -> related(grammar, relation))
                .toList();
        boolean hasQuestion = questionRepository
                .findByGrammarContentItemSlugAndPublishedTrueAndReviewStatusOrderById(
                        grammar.getContentItem().getSlug(), ReviewStatus.APPROVED).stream()
                .anyMatch(GrammarConfirmationQuestion::isPubliclyVisible);
        return new GrammarLearningDetails(enrichment.map(this::toEnrichment).orElse(null), related, hasQuestion);
    }

    @Transactional(readOnly = true)
    public GrammarLearningDetails publicDetails(String grammarSlug) {
        return publicDetails(grammar(grammarSlug, true));
    }

    @Transactional(readOnly = true)
    public Optional<GrammarComparisonDetails> publicComparison(String firstSlug, String secondSlug) {
        Grammar first = grammar(firstSlug, true);
        Grammar second = grammar(secondSlug, true);
        if (first.getId().equals(second.getId())) return Optional.empty();
        Grammar left = first.getId() < second.getId() ? first : second;
        Grammar right = left == first ? second : first;
        return relationRepository.findPublicPair(left.getId(), right.getId(), ReviewStatus.APPROVED).stream()
                .map(relation -> comparisonRepository.findByRelationIdAndPublishedTrueAndReviewStatus(
                                relation.getId(), ReviewStatus.APPROVED)
                        .filter(GrammarComparison::isPubliclyVisible)
                        .map(comparison -> toComparison(relation, comparison)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<GrammarConfirmationQuestionDetails> confirmationQuestions(String grammarSlug) {
        grammar(grammarSlug, true);
        return questionRepository.findByGrammarContentItemSlugAndPublishedTrueAndReviewStatusOrderById(
                        grammarSlug, ReviewStatus.APPROVED).stream()
                .filter(GrammarConfirmationQuestion::isPubliclyVisible)
                .map(this::toQuestion)
                .toList();
    }

    @Transactional
    public GrammarConfirmationAnswerResult answer(UserAccount account, Long questionId, Long choiceId) {
        GrammarConfirmationQuestion question = questionRepository
                .findByIdAndPublishedTrueAndReviewStatus(questionId, ReviewStatus.APPROVED)
                .filter(GrammarConfirmationQuestion::isPubliclyVisible)
                .orElseThrow(() -> new NoSuchElementException("Public grammar confirmation question not found"));
        boolean correct = question.isCorrectChoice(choiceId);
        var profile = profileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        attemptRepository.save(new GrammarConfirmationAttempt(profile, question,
                correct ? StudyResult.CORRECT : StudyResult.INCORRECT));
        return new GrammarConfirmationAnswerResult(correct, question.correctChoice().getChoiceText(),
                question.getExplanation(), question.getGrammar().getContentItem().getSlug(), question.getGrammar().getPattern());
    }

    @Transactional(readOnly = true)
    public List<GrammarWeakness> weaknesses(UserAccount account) {
        var profile = profileRepository.findByUserAccountLoginId(account.getLoginId()).orElseThrow();
        Map<Long, long[]> counts = new LinkedHashMap<>();
        Map<Long, Grammar> grammars = new LinkedHashMap<>();
        attemptRepository.findByLearnerProfileLearnerKeyOrderByAnsweredAtDesc(profile.getLearnerKey(), PageRequest.of(0, 200))
                .forEach(attempt -> {
                    Grammar grammar = attempt.getQuestion().getGrammar();
                    long[] count = counts.computeIfAbsent(grammar.getId(), ignored -> new long[2]);
                    count[0]++;
                    if (attempt.getResult() == StudyResult.INCORRECT) count[1]++;
                    grammars.putIfAbsent(grammar.getId(), grammar);
                });
        return counts.entrySet().stream().filter(entry -> entry.getValue()[1] > 0)
                .sorted(Comparator.<Map.Entry<Long, long[]>>comparingLong(entry -> entry.getValue()[1]).reversed())
                .limit(10).map(entry -> {
                    Grammar grammar = grammars.get(entry.getKey());
                    var progress = progressRepository.findByLearnerProfileLearnerKeyAndContentItemId(
                            profile.getLearnerKey(), grammar.getContentItem().getId()).orElse(null);
                    return new GrammarWeakness(grammar.getContentItem().getSlug(), grammar.getPattern(),
                            entry.getValue()[0], entry.getValue()[1], progress == null ? null : progress.getLearningState());
                }).toList();
    }

    private Grammar grammar(String slug, boolean publicOnly) {
        ContentItem item = (publicOnly ? contentItemRepository.findBySlugAndPublishedTrue(slug) : contentItemRepository.findBySlug(slug))
                .orElseThrow(() -> new NoSuchElementException("Grammar content not found"));
        if (item.getGrammar() == null) throw new NoSuchElementException("Grammar content not found");
        return item.getGrammar();
    }

    private GrammarLearningDetails.Enrichment toEnrichment(GrammarEnrichment value) {
        return new GrammarLearningDetails.Enrichment(value.getNuance(), value.getUsageNote(), value.getFormationSupplement(),
                value.getCommonMistake(), value.getLearnerNote(), value.getSourceRef());
    }

    private GrammarLearningDetails.RelatedGrammar related(Grammar current, GrammarRelation relation) {
        Grammar other = relation.getLeftGrammar().getId().equals(current.getId())
                ? relation.getRightGrammar() : relation.getLeftGrammar();
        boolean comparison = comparisonRepository.findByRelationIdAndPublishedTrueAndReviewStatus(
                relation.getId(), ReviewStatus.APPROVED).filter(GrammarComparison::isPubliclyVisible).isPresent();
        return new GrammarLearningDetails.RelatedGrammar(other.getContentItem().getSlug(), other.getPattern(),
                other.getExplanation(), relation.getRelationType(), comparison);
    }

    private GrammarComparisonDetails toComparison(GrammarRelation relation, GrammarComparison comparison) {
        return new GrammarComparisonDetails(asRelated(relation.getLeftGrammar(), relation, true),
                asRelated(relation.getRightGrammar(), relation, true), relation.getRelationType(), comparison.getSummary(),
                comparison.getKeyDifference(), comparison.getUsageDifference(), comparison.getCommonConfusion(), comparison.getSourceRef());
    }

    private GrammarLearningDetails.RelatedGrammar asRelated(Grammar grammar, GrammarRelation relation, boolean comparison) {
        return new GrammarLearningDetails.RelatedGrammar(grammar.getContentItem().getSlug(), grammar.getPattern(),
                grammar.getExplanation(), relation.getRelationType(), comparison);
    }

    private GrammarConfirmationQuestionDetails toQuestion(GrammarConfirmationQuestion question) {
        return new GrammarConfirmationQuestionDetails(question.getId(), question.getGrammar().getContentItem().getSlug(),
                question.getGrammar().getPattern(), question.getQuestionType(), question.getPrompt(), question.getContext(),
                question.getChoices().stream().map(choice -> new GrammarConfirmationQuestionDetails.Choice(choice.getId(), choice.getChoiceText())).toList());
    }
}
