package com.japanese.learning.entity;

import com.japanese.content.entity.ContentItem;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "study_collection_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_study_collection_content", columnNames = {"study_collection_id", "content_item_id"}))
public class StudyCollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_collection_id", nullable = false)
    private StudyCollection studyCollection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_item_id", nullable = false)
    private ContentItem contentItem;

    private Instant createdAt;

    protected StudyCollectionItem() {
    }

    public StudyCollectionItem(StudyCollection studyCollection, ContentItem contentItem) {
        this.studyCollection = studyCollection;
        this.contentItem = contentItem;
        this.createdAt = Instant.now();
    }

    public ContentItem getContentItem() {
        return contentItem;
    }
}
