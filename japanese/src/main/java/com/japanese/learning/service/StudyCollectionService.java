package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.dto.StudyCollectionDetails;
import com.japanese.learning.dto.StudyCollectionSummary;
import com.japanese.learning.entity.StudyCollection;
import com.japanese.learning.entity.StudyCollectionItem;
import com.japanese.learning.repository.StudyCollectionItemRepository;
import com.japanese.learning.repository.StudyCollectionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyCollectionService {

    private final StudyCollectionRepository collectionRepository;
    private final StudyCollectionItemRepository itemRepository;
    private final ContentItemRepository contentItemRepository;

    public StudyCollectionService(
            StudyCollectionRepository collectionRepository,
            StudyCollectionItemRepository itemRepository,
            ContentItemRepository contentItemRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.itemRepository = itemRepository;
        this.contentItemRepository = contentItemRepository;
    }

    @Transactional(readOnly = true)
    public List<StudyCollectionSummary> list(UserAccount account) {
        return collectionRepository.findByUserAccountLoginIdOrderByUpdatedAtDesc(account.getLoginId()).stream()
                .map(collection -> new StudyCollectionSummary(
                        collection.getId(), collection.getName(), itemRepository.countByStudyCollectionIdAndContentItemPublishedTrue(collection.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public long count(UserAccount account) {
        return collectionRepository.countByUserAccountLoginId(account.getLoginId());
    }

    @Transactional(readOnly = true)
    public StudyCollectionDetails details(UserAccount account, Long id) {
        StudyCollection collection = owned(account, id);
        return new StudyCollectionDetails(collection.getId(), collection.getName(),
                itemRepository.findByStudyCollectionIdAndContentItemPublishedTrueOrderByCreatedAtAsc(collection.getId()).stream()
                        .map(StudyCollectionItem::getContentItem)
                        .map(StudyQueueService::toSummary)
                        .toList());
    }

    @Transactional
    public StudyCollectionSummary create(UserAccount account, String name) {
        StudyCollection collection = collectionRepository.save(new StudyCollection(account, normalizeName(name)));
        return new StudyCollectionSummary(collection.getId(), collection.getName(), 0);
    }

    @Transactional
    public StudyCollectionSummary rename(UserAccount account, Long id, String name) {
        StudyCollection collection = owned(account, id);
        collection.rename(normalizeName(name));
        return new StudyCollectionSummary(collection.getId(), collection.getName(), itemRepository.countByStudyCollectionIdAndContentItemPublishedTrue(id));
    }

    @Transactional
    public void delete(UserAccount account, Long id) {
        collectionRepository.delete(owned(account, id));
    }

    @Transactional
    public void addContent(UserAccount account, Long id, String slug) {
        StudyCollection collection = owned(account, id);
        ContentItem content = contentItemRepository.findBySlugAndPublishedTrue(slug).orElseThrow();
        if (itemRepository.findByStudyCollectionIdAndContentItemSlug(collection.getId(), slug).isEmpty()) {
            itemRepository.save(new StudyCollectionItem(collection, content));
        }
    }

    @Transactional
    public void removeContent(UserAccount account, Long id, String slug) {
        owned(account, id);
        itemRepository.deleteByStudyCollectionIdAndContentItemSlug(id, slug);
    }

    @Transactional(readOnly = true)
    public List<com.japanese.content.dto.ContentSummary> cards(UserAccount account, Long id) {
        return details(account, id).contents();
    }

    private StudyCollection owned(UserAccount account, Long id) {
        return collectionRepository.findByIdAndUserAccountLoginId(id, account.getLoginId())
                .orElseThrow(() -> new java.util.NoSuchElementException("Study collection not found"));
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("컬렉션 이름을 입력해 주세요.");
        }
        String name = value.trim();
        if (name.length() > 80) {
            throw new IllegalArgumentException("컬렉션 이름은 80자 이하여야 합니다.");
        }
        return name;
    }
}
