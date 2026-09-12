package com.japanese.content.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A reviewed, authored check after reading a grammar lesson. */
@Entity
@Table(name = "grammar_confirmation_questions")
public class GrammarConfirmationQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "grammar_id", nullable = false) private Grammar grammar;
    @Enumerated(EnumType.STRING) @Column(name = "question_type", nullable = false, length = 30) private GrammarConfirmationType questionType;
    @Column(nullable = false, length = 3000) private String prompt;
    @Column(length = 3000) private String context;
    @Column(length = 3000) private String explanation;
    @Column(name = "source_ref", nullable = false, length = 300) private String sourceRef;
    @Column(name = "review_status", nullable = false, length = 20) private ReviewStatus reviewStatus = ReviewStatus.PENDING;
    @Column(nullable = false) private boolean published;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder asc") private List<GrammarConfirmationChoice> choices = new ArrayList<>();
    protected GrammarConfirmationQuestion() { }
    public GrammarConfirmationQuestion(Grammar grammar, GrammarConfirmationType questionType, String prompt, String context, String explanation, String sourceRef) {
        this.grammar=grammar; this.questionType=questionType; this.prompt=prompt; this.context=context; this.explanation=explanation; this.sourceRef=sourceRef;
    }
    public void addChoice(String text, boolean correct) { choices.add(new GrammarConfirmationChoice(this, text, correct, choices.size())); }
    public void revise(GrammarConfirmationType questionType, String prompt, String context, String explanation) {
        this.questionType=questionType; this.prompt=prompt; this.context=context; this.explanation=explanation;
        choices.clear();
    }
    public void approveForPublication() { if (choices.stream().filter(GrammarConfirmationChoice::isCorrect).count()!=1) throw new IllegalStateException("A confirmation question must have exactly one correct choice"); reviewStatus=ReviewStatus.APPROVED; published=true; reviewedAt=Instant.now(); }
    public void markPending(){reviewStatus=ReviewStatus.PENDING;published=false;reviewedAt=null;}
    public boolean isPubliclyVisible(){return published && reviewStatus==ReviewStatus.APPROVED && grammar.getContentItem().isPublished();}
    public Long getId(){return id;} public Grammar getGrammar(){return grammar;} public GrammarConfirmationType getQuestionType(){return questionType;} public String getPrompt(){return prompt;} public String getContext(){return context;} public String getExplanation(){return explanation;} public String getSourceRef(){return sourceRef;} public List<GrammarConfirmationChoice> getChoices(){return choices;}
    public boolean isCorrectChoice(Long choiceId) { return choices.stream().anyMatch(choice -> choice.getId().equals(choiceId) && choice.isCorrect()); }
    public GrammarConfirmationChoice correctChoice() { return choices.stream().filter(GrammarConfirmationChoice::isCorrect).findFirst().orElseThrow(); }
}
