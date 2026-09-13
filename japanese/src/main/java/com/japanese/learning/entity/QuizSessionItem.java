package com.japanese.learning.entity;

import com.japanese.content.entity.ContentItem;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "quiz_session_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_quiz_session_position", columnNames = {"session_id", "position"})})
public class QuizSessionItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "session_id", nullable = false) private QuizSession session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "content_item_id", nullable = false) private ContentItem contentItem;
    @Column(nullable = false) private int position;
    @Enumerated(EnumType.STRING) @Column(name = "question_type", nullable = false, length = 40) private QuizQuestionType questionType;
    @Column(nullable = false, length = 300) private String instruction;
    @Lob @Column(nullable = false) private String prompt;
    @Lob @Column(name = "choices_json") private String choicesJson;
    @Column(name = "correct_answer", nullable = false, length = 2000) private String correctAnswer;
    @Lob @Column(name = "explanation") private String explanation;
    @Column(nullable = false) private boolean answered;
    @Column(name = "submitted_answer", length = 2000) private String submittedAnswer;
    @Column(name = "correct") private Boolean correct;
    @Column(name = "earned_experience", nullable = false) private int earnedExperience;
    @Column(name = "answered_at") private Instant answeredAt;

    protected QuizSessionItem() { }

    public QuizSessionItem(QuizSession session, ContentItem contentItem, int position, QuizQuestionType questionType,
            String instruction, String prompt, String choicesJson, String correctAnswer, String explanation) {
        this.session = session;
        this.contentItem = contentItem;
        this.position = position;
        this.questionType = questionType;
        this.instruction = instruction;
        this.prompt = prompt;
        this.choicesJson = choicesJson;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
    }

    public void answer(String submittedAnswer, boolean correct, int earnedExperience) {
        if (answered) return;
        this.answered = true;
        this.submittedAnswer = submittedAnswer;
        this.correct = correct;
        this.earnedExperience = earnedExperience;
        this.answeredAt = Instant.now();
    }

    public Long getId() { return id; }
    public ContentItem getContentItem() { return contentItem; }
    public int getPosition() { return position; }
    public QuizQuestionType getQuestionType() { return questionType; }
    public String getInstruction() { return instruction; }
    public String getPrompt() { return prompt; }
    public String getChoicesJson() { return choicesJson; }
    public String getCorrectAnswer() { return correctAnswer; }
    public String getExplanation() { return explanation; }
    public boolean isAnswered() { return answered; }
    public String getSubmittedAnswer() { return submittedAnswer; }
    public Boolean getCorrect() { return correct; }
    public int getEarnedExperience() { return earnedExperience; }
}
