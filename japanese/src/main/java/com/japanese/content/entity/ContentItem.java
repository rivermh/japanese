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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "content_items", indexes = @Index(name = "idx_content_published_type", columnList = "published,type"))
public class ContentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentType type;

    @Column(name = "source_ref", length = 160)
    private String sourceRef;

    @Column(nullable = false)
    private boolean published;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "content_item_categories",
            joinColumns = @JoinColumn(name = "content_item_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "content_item_levels",
            joinColumns = @JoinColumn(name = "content_item_id"),
            inverseJoinColumns = @JoinColumn(name = "level_id")
    )
    private Set<Level> levels = new LinkedHashSet<>();

    @OneToOne(mappedBy = "contentItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Word word;

    @OneToOne(mappedBy = "contentItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Grammar grammar;

    @OneToMany(mappedBy = "contentItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @jakarta.persistence.OrderBy("displayOrder asc")
    private List<Example> examples = new ArrayList<>();

    protected ContentItem() {
    }

    public ContentItem(String slug, ContentType type, String sourceRef, boolean published) {
        this.slug = slug;
        this.type = type;
        this.sourceRef = sourceRef;
        this.published = published;
    }

    public void addCategory(Category category) {
        categories.add(category);
    }

    public void addLevel(Level level) {
        levels.add(level);
    }

    public void attachWord(Word word) {
        this.word = word;
        word.setContentItem(this);
    }

    public void attachGrammar(Grammar grammar) {
        this.grammar = grammar;
        grammar.setContentItem(this);
    }

    public void replaceWord(Word word) {
        this.word = word;
        word.setContentItem(this);
    }

    public void replaceGrammar(Grammar grammar) {
        this.grammar = grammar;
        grammar.setContentItem(this);
    }

    public void replaceExamples(List<Example> replacement) {
        examples.clear();
        replacement.forEach(this::addExample);
    }

    public void addExample(Example example) {
        examples.add(example);
        example.setContentItem(this);
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public ContentType getType() {
        return type;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public boolean isPublished() {
        return published;
    }

    public void publish() {
        validatePublishable();
        this.published = true;
        this.reviewStatus = ReviewStatus.APPROVED;
        this.reviewNote = null;
        this.reviewedAt = Instant.now();
    }

    private void validatePublishable() {
        if (type == ContentType.WORD) {
            if (word == null || blank(word.getExpression()) || blank(word.getReading())) {
                throw new IllegalStateException("단어 표기와 읽기가 있어야 공개할 수 있습니다.");
            }
            if (word.getMeanings().isEmpty() || word.getMeanings().stream().anyMatch(meaning -> blank(meaning.getText()))) {
                throw new IllegalStateException("빈 뜻이 없는 단어 의미가 하나 이상 있어야 공개할 수 있습니다.");
            }
            return;
        }
        if (type == ContentType.GRAMMAR
                && (grammar == null || blank(grammar.getPattern()) || blank(grammar.getExplanation()))) {
            throw new IllegalStateException("문법 패턴과 설명이 있어야 공개할 수 있습니다.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public void reject(String note) {
        this.published = false;
        this.reviewStatus = ReviewStatus.REJECTED;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    public void resetReview() {
        this.published = false;
        this.reviewStatus = ReviewStatus.PENDING;
        this.reviewNote = null;
        this.reviewedAt = null;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus == null ? ReviewStatus.PENDING : reviewStatus;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public Set<Level> getLevels() {
        return levels;
    }

    public Word getWord() {
        return word;
    }

    public Grammar getGrammar() {
        return grammar;
    }

    public List<Example> getExamples() {
        return examples;
    }

    public void replaceLevels(Set<Level> replacement) {
        levels.clear();
        levels.addAll(replacement);
    }

    public void refreshSearchKeys() {
        if (word != null) {
            word.refreshSearchKeys();
        }
        if (grammar != null) {
            grammar.refreshSearchKeys();
        }
        examples.forEach(Example::refreshSearchKey);
    }
}
