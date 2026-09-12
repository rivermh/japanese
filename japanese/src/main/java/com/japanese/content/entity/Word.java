package com.japanese.content.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import com.japanese.content.search.ContentSearchNormalizer;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@BatchSize(size = 50)
@Table(name = "words", indexes = {
        @Index(name = "idx_word_expression_search", columnList = "expression_search"),
        @Index(name = "idx_word_reading_search", columnList = "reading_search")
})
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_item_id", nullable = false, unique = true)
    private ContentItem contentItem;

    @Column(nullable = false, length = 120)
    private String expression;

    @Column(nullable = false, length = 120)
    private String reading;

    @Column(name = "expression_search", length = 120)
    private String expressionSearch;

    @Column(name = "reading_search", length = 120)
    private String readingSearch;

    @Column(name = "part_of_speech", length = 120)
    private String partOfSpeech;

    @Column(name = "pitch_accent", length = 500)
    private String pitchAccent;

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("senseOrder asc")
    private List<Meaning> meanings = new ArrayList<>();

    protected Word() {
    }

    public Word(String expression, String reading, String partOfSpeech, String pitchAccent) {
        this.expression = expression;
        this.reading = reading;
        this.partOfSpeech = partOfSpeech;
        this.pitchAccent = pitchAccent;
        refreshSearchKeys();
    }

    public void setContentItem(ContentItem contentItem) {
        this.contentItem = contentItem;
    }

    public void addMeaning(Meaning meaning) {
        meanings.add(meaning);
        meaning.setWord(this);
    }

    public Long getId() {
        return id;
    }

    public ContentItem getContentItem() {
        return contentItem;
    }

    public String getExpression() {
        return expression;
    }

    public String getReading() {
        return reading;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public String getPitchAccent() {
        return pitchAccent;
    }

    public List<Meaning> getMeanings() {
        return meanings;
    }

    public void refreshSearchKeys() {
        this.expressionSearch = ContentSearchNormalizer.normalize(expression);
        this.readingSearch = ContentSearchNormalizer.normalize(reading);
        meanings.forEach(Meaning::refreshSearchKey);
    }
}
