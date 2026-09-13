package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.dto.ContentSummary;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Meaning;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.dto.StudyQueueItem;
import com.japanese.learning.dto.StudyQueueStatus;
import com.japanese.learning.entity.StudyQueueEntry;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.StudyQueueRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyQueueService {

    private final StudyQueueRepository queueRepository;
    private final ContentItemRepository contentItemRepository;
    private final LearnerProfileRepository profileRepository;
    private final LearningProgressRepository progressRepository;

    public StudyQueueService(
            StudyQueueRepository queueRepository,
            ContentItemRepository contentItemRepository,
            LearnerProfileRepository profileRepository,
            LearningProgressRepository progressRepository
    ) {
        this.queueRepository = queueRepository;
        this.contentItemRepository = contentItemRepository;
        this.profileRepository = profileRepository;
        this.progressRepository = progressRepository;
    }

    @Transactional(readOnly = true)
    public StudyQueueStatus status(UserAccount account, String slug) {
        if (queueRepository.findByUserAccountLoginIdAndContentItemSlug(account.getLoginId(), slug).isPresent()) {
            return StudyQueueStatus.alreadyQueued();
        }
        return hasProgress(account, slug) ? StudyQueueStatus.alreadyLearning() : StudyQueueStatus.ready();
    }

    @Transactional
    public StudyQueueStatus add(UserAccount account, String slug) {
        ContentItem content = contentItemRepository.findBySlugAndPublishedTrue(slug).orElseThrow();
        if (hasProgress(account, slug)) {
            return StudyQueueStatus.alreadyLearning();
        }
        if (queueRepository.findByUserAccountLoginIdAndContentItemSlug(account.getLoginId(), slug).isEmpty()) {
            queueRepository.save(new StudyQueueEntry(account, content));
        }
        return StudyQueueStatus.alreadyQueued();
    }

    @Transactional
    public void remove(UserAccount account, String slug) {
        queueRepository.findByUserAccountLoginIdAndContentItemSlug(account.getLoginId(), slug)
                .ifPresent(queueRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<StudyQueueItem> list(UserAccount account) {
        return queueRepository.findByUserAccountLoginIdOrderByCreatedAtAsc(account.getLoginId()).stream()
                .filter(entry -> entry.getContentItem().isPublished())
                .map(entry -> new StudyQueueItem(toSummary(entry.getContentItem()), entry.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long count(UserAccount account) {
        return queueRepository.countByUserAccountLoginId(account.getLoginId());
    }

    private boolean hasProgress(UserAccount account, String slug) {
        return profileRepository.findByUserAccountLoginId(account.getLoginId())
                .flatMap(profile -> contentItemRepository.findBySlugAndPublishedTrue(slug)
                        .flatMap(content -> progressRepository.findByLearnerProfileLearnerKeyAndContentItemId(
                                profile.getLearnerKey(), content.getId())))
                .isPresent();
    }

    static ContentSummary toSummary(ContentItem item) {
        String title;
        String reading = null;
        String description;
        if (item.getType() == ContentType.WORD) {
            title = item.getWord().getExpression();
            reading = item.getWord().getReading();
            description = item.getWord().getMeanings().stream()
                    .filter(meaning -> "ko".equals(meaning.getLanguageTag()))
                    .findFirst().map(Meaning::getText).orElse("");
        } else {
            title = item.getGrammar().getPattern();
            description = item.getGrammar().getExplanation();
        }
        return new ContentSummary(item.getId(), item.getSlug(), item.getType(), title, reading, description,
                item.getLevels().stream().map(level -> new ContentSummary.LevelSummary(
                        level.getSystem(), level.getCode(), level.getName())).toList(),
                item.getCategories().stream().map(category -> new ContentSummary.CategorySummary(
                        category.getSlug(), category.getName())).toList());
    }
}
