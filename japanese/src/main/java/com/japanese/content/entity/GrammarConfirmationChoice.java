package com.japanese.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "grammar_confirmation_choices")
public class GrammarConfirmationChoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id", nullable = false) private GrammarConfirmationQuestion question;
    @Column(name = "choice_text", nullable = false, length = 1000) private String choiceText;
    @Column(name = "correct_answer", nullable = false) private boolean correct;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    protected GrammarConfirmationChoice() { }
    GrammarConfirmationChoice(GrammarConfirmationQuestion question, String choiceText, boolean correct, int displayOrder) { this.question=question;this.choiceText=choiceText;this.correct=correct;this.displayOrder=displayOrder; }
    public Long getId(){return id;} public String getChoiceText(){return choiceText;} public boolean isCorrect(){return correct;} public int getDisplayOrder(){return displayOrder;}
}
