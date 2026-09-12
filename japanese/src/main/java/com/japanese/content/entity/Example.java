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
@Table(name = "examples")
public class Example {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_item_id", nullable = false)
    private ContentItem contentItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meaning_id")
    private Meaning meaning;

    @Column(name = "japanese_text", nullable = false, length = 1000)
    private String japaneseText;

    @Column(length = 1000)
    private String reading;

    @Column(length = 1000)
    private String translation;

    @Column(name = "search_key", length = 2500)
    private String searchKey;

    @Column(name = "audio_file_name", length = 255)
    private String audioFileName;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Example() {
    }

    public Example(String japaneseText, String reading, String translation, int displayOrder) {
        this(japaneseText, reading, translation, displayOrder, null);
    }

    public Example(String japaneseText, String reading, String translation, int displayOrder, String audioFileName) {
        this.japaneseText = japaneseText;
        this.reading = reading;
        this.translation = translation;
        this.displayOrder = displayOrder;
        this.audioFileName = audioFileName;
        refreshSearchKey();
    }

    public void setContentItem(ContentItem contentItem) {
        this.contentItem = contentItem;
    }

    public void setMeaning(Meaning meaning) {
        this.meaning = meaning;
    }

    public Long getId() {
        return id;
    }

    public String getJapaneseText() {
        return japaneseText;
    }

    public Meaning getMeaning() {
        return meaning;
    }

    public String getReading() {
        return reading;
    }

    public String getTranslation() {
        return translation;
    }

    public String getAudioFileName() {
        return audioFileName;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void refreshSearchKey() {
        this.searchKey = ContentSearchNormalizer.normalize(String.join(" ",
                japaneseText == null ? "" : japaneseText,
                reading == null ? "" : reading,
                translation == null ? "" : translation));
    }
}
