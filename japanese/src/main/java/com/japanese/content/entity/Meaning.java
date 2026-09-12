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
import com.japanese.content.search.ContentSearchNormalizer;

@Entity
@Table(name = "meanings")
public class Meaning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(name = "language_tag", nullable = false, length = 10)
    private String languageTag;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "search_key", length = 500)
    private String searchKey;

    @Column(name = "sense_order", nullable = false)
    private int senseOrder;

    protected Meaning() {
    }

    public Meaning(String languageTag, String text, int senseOrder) {
        this.languageTag = languageTag;
        this.text = text;
        this.senseOrder = senseOrder;
        refreshSearchKey();
    }

    public void setWord(Word word) {
        this.word = word;
    }

    public Long getId() {
        return id;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public String getText() {
        return text;
    }

    public int getSenseOrder() {
        return senseOrder;
    }

    public void refreshSearchKey() {
        this.searchKey = ContentSearchNormalizer.normalize(text);
    }
}
