package com.japanese.content.service;

import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.Grammar;
import com.japanese.content.entity.GrammarComparison;
import com.japanese.content.entity.GrammarConfirmationQuestion;
import com.japanese.content.entity.GrammarConfirmationType;
import com.japanese.content.entity.GrammarEnrichment;
import com.japanese.content.entity.GrammarRelation;
import com.japanese.content.entity.GrammarRelationType;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.GrammarComparisonRepository;
import com.japanese.content.repository.GrammarConfirmationQuestionRepository;
import com.japanese.content.repository.GrammarEnrichmentRepository;
import com.japanese.content.repository.GrammarRelationRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service boundary for a future reviewer/admin UI or verified fixture importer. Nothing is public until approved. */
@Service
public class GrammarCurationService {
    private final ContentItemRepository contents;
    private final GrammarEnrichmentRepository enrichments;
    private final GrammarRelationRepository relations;
    private final GrammarComparisonRepository comparisons;
    private final GrammarConfirmationQuestionRepository questions;

    public GrammarCurationService(ContentItemRepository contents, GrammarEnrichmentRepository enrichments,
                                  GrammarRelationRepository relations, GrammarComparisonRepository comparisons,
                                  GrammarConfirmationQuestionRepository questions) {
        this.contents=contents; this.enrichments=enrichments; this.relations=relations; this.comparisons=comparisons; this.questions=questions;
    }

    @Transactional
    public GrammarEnrichment saveEnrichment(String grammarSlug, String sourceRef, String nuance, String usageNote,
                                            String formation, String commonMistake, String learnerNote) {
        Grammar grammar = grammar(grammarSlug);
        GrammarEnrichment enrichment = enrichments.findByGrammarId(grammar.getId())
                .orElseGet(() -> new GrammarEnrichment(grammar, sourceRef));
        enrichment.revise(nuance, usageNote, formation, commonMistake, learnerNote);
        enrichment.markPending();
        return enrichments.save(enrichment);
    }

    @Transactional
    public GrammarRelation saveRelation(String firstSlug, String secondSlug, GrammarRelationType type, String sourceRef) {
        Grammar first = grammar(firstSlug); Grammar second = grammar(secondSlug);
        if (first.getId().equals(second.getId())) throw new IllegalArgumentException("A grammar cannot be related to itself");
        Grammar left = first.getId() < second.getId() ? first : second;
        Grammar right = left == first ? second : first;
        GrammarRelation relation = relations.findByLeftGrammarIdAndRightGrammarIdAndRelationType(left.getId(), right.getId(), type)
                .orElseGet(() -> new GrammarRelation(left, right, type, sourceRef));
        relation.markPending();
        return relations.save(relation);
    }

    @Transactional
    public GrammarComparison saveComparison(Long relationId, String sourceRef, String summary, String keyDifference,
                                            String usageDifference, String commonConfusion) {
        GrammarRelation relation = relations.findById(relationId).orElseThrow();
        GrammarComparison comparison = comparisons.findByRelationId(relationId)
                .orElseGet(() -> new GrammarComparison(relation, sourceRef));
        comparison.revise(summary, keyDifference, usageDifference, commonConfusion);
        comparison.markPending();
        return comparisons.save(comparison);
    }

    @Transactional
    public GrammarConfirmationQuestion saveQuestion(String grammarSlug, GrammarConfirmationType type, String prompt,
                                                    String context, String explanation, String sourceRef,
                                                    List<ChoiceDraft> choices) {
        Grammar grammar = grammar(grammarSlug);
        GrammarConfirmationQuestion question = questions.findByGrammarIdAndSourceRef(grammar.getId(), sourceRef)
                .orElseGet(() -> new GrammarConfirmationQuestion(grammar, type, prompt, context, explanation, sourceRef));
        question.revise(type, prompt, context, explanation);
        choices.forEach(choice -> question.addChoice(choice.text(), choice.correct()));
        question.markPending();
        return questions.save(question);
    }

    private Grammar grammar(String slug) {
        ContentItem content = contents.findBySlug(slug).orElseThrow(() -> new NoSuchElementException("Grammar not found"));
        if (content.getGrammar() == null) throw new NoSuchElementException("Grammar not found");
        return content.getGrammar();
    }

    public record ChoiceDraft(String text, boolean correct) { }
}
