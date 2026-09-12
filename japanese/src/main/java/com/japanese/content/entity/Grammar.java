package com.japanese.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import com.japanese.content.search.ContentSearchNormalizer;
import org.hibernate.annotations.BatchSize;

@Entity
@BatchSize(size = 50)
@Table(name = "grammars", indexes = @Index(name = "idx_grammar_pattern_search", columnList = "pattern_search"))
public class Grammar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_item_id", nullable = false, unique = true)
    private ContentItem contentItem;

    @Column(nullable = false, length = 200)
    private String pattern;

    @Column(name = "pattern_search", length = 200)
    private String patternSearch;

    @Column(nullable = false, length = 2000)
    private String explanation;

    @Column(length = 500)
    private String connection;

    @Column(name = "search_text", length = 2500)
    private String searchText;

    protected Grammar() {
    }

    public Grammar(String pattern, String explanation, String connection) {
        this.pattern = pattern;
        this.explanation = explanation;
        this.connection = connection;
        refreshSearchKeys();
    }

    public void setContentItem(ContentItem contentItem) {
        this.contentItem = contentItem;
    }

    public Long getId() {
        return id;
    }

    public ContentItem getContentItem() {
        return contentItem;
    }

    public String getPattern() {
        return pattern;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getConnection() {
        return connection;
    }

    public void refreshSearchKeys() {
        this.patternSearch = ContentSearchNormalizer.normalize(pattern);
        this.searchText = ContentSearchNormalizer.normalize(String.join(" ",
                explanation == null ? "" : explanation,
                connection == null ? "" : connection));
    }
}
