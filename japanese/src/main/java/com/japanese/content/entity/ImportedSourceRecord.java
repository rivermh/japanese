package com.japanese.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "imported_source_records", uniqueConstraints = @UniqueConstraint(
        name = "uk_imported_source_record",
        columnNames = {"source_ref", "note_type", "source_note_id"}))
public class ImportedSourceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_ref", nullable = false, length = 160)
    private String sourceRef;

    @Column(name = "note_type", nullable = false, length = 120)
    private String noteType;

    @Column(name = "source_note_id", nullable = false)
    private long sourceNoteId;

    @Column(name = "level_code", length = 20)
    private String levelCode;

    @Column(nullable = false, length = 1000)
    private String tags;

    @Lob
    @Column(name = "field_names", nullable = false, columnDefinition = "longtext")
    private String fieldNames;

    @Lob
    @Column(name = "field_values", nullable = false, columnDefinition = "longtext")
    private String fieldValues;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_item_id")
    private ContentItem contentItem;

    protected ImportedSourceRecord() {
    }

    public ImportedSourceRecord(String sourceRef, String noteType, long sourceNoteId,
                                String tags, String fieldNames, String fieldValues) {
        this(sourceRef, noteType, sourceNoteId, null, tags, fieldNames, fieldValues);
    }

    public ImportedSourceRecord(String sourceRef, String noteType, long sourceNoteId, String levelCode,
                                String tags, String fieldNames, String fieldValues) {
        this.sourceRef = sourceRef;
        this.noteType = noteType;
        this.sourceNoteId = sourceNoteId;
        this.levelCode = levelCode;
        this.tags = tags == null ? "" : tags;
        this.fieldNames = fieldNames;
        this.fieldValues = fieldValues;
    }

    public Long getId() {
        return id;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getNoteType() {
        return noteType;
    }

    public long getSourceNoteId() {
        return sourceNoteId;
    }

    public String getLevelCode() {
        return levelCode;
    }

    public void setLevelCode(String levelCode) {
        this.levelCode = levelCode;
    }

    public String getFieldNames() {
        return fieldNames;
    }

    public String getFieldValues() {
        return fieldValues;
    }

    public ContentItem getContentItem() { return contentItem; }
    public void linkContentItem(ContentItem contentItem) { this.contentItem = contentItem; }
    public void refresh(String levelCode, String tags, String fieldNames, String fieldValues) {
        this.levelCode = levelCode;
        this.tags = tags == null ? "" : tags;
        this.fieldNames = fieldNames;
        this.fieldValues = fieldValues;
    }
}
