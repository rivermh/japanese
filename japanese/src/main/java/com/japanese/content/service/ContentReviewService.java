package com.japanese.content.service;

import com.japanese.content.dto.ContentDetails;
import com.japanese.content.dto.ContentReviewDetails;
import com.japanese.content.dto.ContentReviewSummary;
import com.japanese.content.dto.ReviewHistoryEntry;
import com.japanese.content.dto.SourceDetails;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.ContentReviewHistory;
import com.japanese.content.entity.ReviewStatus;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentReviewHistoryRepository;
import com.japanese.content.repository.ContentSourceRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentReviewService {

    private static final int MAX_REVIEW_ITEMS = 100;
    private static final int MAX_BULK_REVIEW_ITEMS = 20;

    private final ContentItemRepository contentItemRepository;
    private final ContentReviewHistoryRepository contentReviewHistoryRepository;
    private final ContentSourceRepository contentSourceRepository;

    public ContentReviewService(
            ContentItemRepository contentItemRepository,
            ContentReviewHistoryRepository contentReviewHistoryRepository,
            ContentSourceRepository contentSourceRepository
    ) {
        this.contentItemRepository = contentItemRepository;
        this.contentReviewHistoryRepository = contentReviewHistoryRepository;
        this.contentSourceRepository = contentSourceRepository;
    }

    @Transactional(readOnly = true)
    public List<ContentReviewSummary> findUnpublished() {
        return contentItemRepository.findByPublishedFalseOrderById(PageRequest.of(0, MAX_REVIEW_ITEMS))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContentReviewSummary> findUnpublished(ReviewStatus status) {
        return findUnpublished(status, 0);
    }

    @Transactional(readOnly = true)
    public List<ContentReviewSummary> findUnpublished(ReviewStatus status, int page) {
        return findUnpublished(status, null, null, page);
    }

    @Transactional(readOnly = true)
    public List<ContentReviewSummary> findUnpublished(
            ReviewStatus status, ContentType type, String levelCode, int page) {
        int normalizedPage = Math.max(page, 0);
        String normalizedLevel = levelCode == null || levelCode.isBlank() ? null : levelCode.trim();
        List<ContentItem> items = type == null && normalizedLevel == null && status == null
                ? contentItemRepository.findByPublishedFalseOrderById(PageRequest.of(normalizedPage, MAX_REVIEW_ITEMS))
                : contentItemRepository.findUnpublishedByFilters(
                        status == null ? ReviewStatus.PENDING : status,
                        status == null || status == ReviewStatus.PENDING,
                        type, normalizedLevel, PageRequest.of(normalizedPage, MAX_REVIEW_ITEMS));
        return items.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public long countUnpublished(ReviewStatus status) {
        return countUnpublished(status, null, null);
    }

    @Transactional(readOnly = true)
    public long countUnpublished(ReviewStatus status, ContentType type, String levelCode) {
        String normalizedLevel = levelCode == null || levelCode.isBlank() ? null : levelCode.trim();
        if (type != null || normalizedLevel != null) {
            return contentItemRepository.countUnpublishedByFilters(
                    status == null ? ReviewStatus.PENDING : status,
                    status == null || status == ReviewStatus.PENDING,
                    type, normalizedLevel);
        }
        return status == null
                ? contentItemRepository.countByPublishedFalse()
                : contentItemRepository.countUnpublishedByReviewStatus(status, status == ReviewStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Optional<ContentReviewDetails> findUnpublishedDetails(Long contentId) {
        return contentItemRepository.findByIdAndPublishedFalse(contentId).map(this::toDetails);
    }

    @Transactional
    public void publish(Long contentId) {
        ContentItem item = contentItemRepository.findByIdAndPublishedFalse(contentId)
                .orElseThrow(() -> new NoSuchElementException("Content not found: " + contentId));
        item.publish();
        contentReviewHistoryRepository.save(new ContentReviewHistory(item, ReviewStatus.APPROVED, "검토 완료 · 공개"));
    }

    @Transactional
    public int publishSelected(Collection<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return 0;
        }
        int publishedCount = 0;
        for (Long contentId : contentIds.stream().distinct().limit(MAX_BULK_REVIEW_ITEMS).toList()) {
            Optional<ContentItem> item = contentItemRepository.findByIdAndPublishedFalse(contentId);
            if (item.isEmpty()) {
                continue;
            }
            item.get().publish();
            contentReviewHistoryRepository.save(
                    new ContentReviewHistory(item.get(), ReviewStatus.APPROVED, "검토 완료 · 선택 공개"));
            publishedCount++;
        }
        return publishedCount;
    }

    @Transactional
    public void reject(Long contentId, String note) {
        String normalizedNote = note == null ? "" : note.trim();
        if (normalizedNote.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        ContentItem item = contentItemRepository.findByIdAndPublishedFalse(contentId)
                .orElseThrow(() -> new NoSuchElementException("Content not found: " + contentId));
        item.reject(normalizedNote);
        contentReviewHistoryRepository.save(new ContentReviewHistory(item, ReviewStatus.REJECTED, normalizedNote));
    }

    @Transactional
    public void reset(Long contentId) {
        ContentItem item = contentItemRepository.findByIdAndPublishedFalse(contentId)
                .orElseThrow(() -> new NoSuchElementException("Content not found: " + contentId));
        item.resetReview();
        contentReviewHistoryRepository.save(new ContentReviewHistory(item, ReviewStatus.PENDING, "재검토 요청"));
    }

    private ContentReviewSummary toSummary(ContentItem item) {
        String title;
        String reading = null;
        String meaning = null;
        if (item.getType() == ContentType.WORD) {
            title = item.getWord().getExpression();
            reading = item.getWord().getReading();
            meaning = item.getWord().getMeanings().stream()
                    .findFirst()
                    .map(com.japanese.content.entity.Meaning::getText)
                    .orElse("");
        } else {
            title = item.getGrammar().getPattern();
        }
        String level = item.getLevels().stream()
                .findFirst()
                .map(value -> value.getSystem() + " " + value.getCode())
                .orElse("");
        return new ContentReviewSummary(
                item.getId(), item.getSlug(), item.getType(), title, reading, meaning,
                level,
                item.getCategories().stream().map(value -> value.getName()).toList(),
                item.getReviewStatus(),
                item.getReviewNote(),
                item.getExamples().size(),
                item.getSourceRef());
    }

    private ContentReviewDetails toDetails(ContentItem item) {
        List<ContentDetails.MeaningDetails> meanings = item.getWord() == null
                ? List.of()
                : item.getWord().getMeanings().stream()
                .map(meaning -> new ContentDetails.MeaningDetails(
                        meaning.getLanguageTag(), meaning.getText(), meaning.getSenseOrder()))
                .toList();
        List<ContentDetails.ExampleDetails> examples = item.getExamples().stream()
                .map(example -> new ContentDetails.ExampleDetails(
                        example.getMeaning() == null ? null : example.getMeaning().getText(),
                        example.getJapaneseText(),
                        example.getReading(),
                        example.getTranslation(),
                        example.getAudioFileName(),
                        example.getDisplayOrder()))
                .toList();
        ContentDetails.GrammarDetails grammar = item.getGrammar() == null
                ? null
                : new ContentDetails.GrammarDetails(
                        item.getGrammar().getPattern(),
                        item.getGrammar().getExplanation(),
                        item.getGrammar().getConnection(),
                        null);
        return new ContentReviewDetails(
                toSummary(item),
                toSourceDetails(item.getSourceRef()),
                item.getWord() == null ? null : item.getWord().getPartOfSpeech(),
                item.getWord() == null ? null : item.getWord().getPitchAccent(),
                meanings,
                examples,
                grammar,
                contentReviewHistoryRepository.findByContentItemIdOrderByReviewedAtDesc(item.getId()).stream()
                        .map(history -> new ReviewHistoryEntry(
                                history.getStatus(), history.getNote(), history.getReviewedAt()))
                        .toList());
    }

    private SourceDetails toSourceDetails(String sourceRef) {
        if (sourceRef == null) {
            return null;
        }
        return contentSourceRepository.findBySourceRef(sourceRef)
                .map(source -> new SourceDetails(
                        source.getSourceRef(),
                        source.getDisplayName(),
                        source.getVersion(),
                        source.getLicenseSummary(),
                        source.getLicenseUrl(),
                        source.getAttribution(),
                        source.getUsageNote()))
                .orElse(null);
    }
}
