package com.japanese.content.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.dto.ContentSummary;
import com.japanese.content.entity.Bookmark;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Meaning;
import com.japanese.content.repository.BookmarkRepository;
import com.japanese.content.repository.ContentItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final ContentItemRepository contentItemRepository;
    public BookmarkService(BookmarkRepository bookmarkRepository, ContentItemRepository contentItemRepository) {
        this.bookmarkRepository = bookmarkRepository; this.contentItemRepository = contentItemRepository;
    }
    @Transactional
    public boolean toggle(UserAccount account, String slug) {
        var existing = bookmarkRepository.findByUserAccountLoginIdAndContentItemSlug(account.getLoginId(), slug);
        if (existing.isPresent()) { bookmarkRepository.delete(existing.get()); return false; }
        var content = contentItemRepository.findBySlugAndPublishedTrue(slug).orElseThrow();
        bookmarkRepository.save(new Bookmark(account, content)); return true;
    }
    @Transactional(readOnly = true)
    public boolean isBookmarked(UserAccount account, String slug) {
        return bookmarkRepository.findByUserAccountLoginIdAndContentItemSlug(account.getLoginId(), slug).isPresent();
    }
    @Transactional(readOnly = true)
    public long count(UserAccount account) {
        return bookmarkRepository.countByUserAccountLoginIdAndContentItemPublishedTrue(account.getLoginId());
    }
    @Transactional(readOnly = true)
    public List<ContentSummary> list(UserAccount account, ContentType type, String level) {
        return bookmarkRepository.findByUserAccountLoginIdAndContentItemPublishedTrueOrderByCreatedAtDesc(account.getLoginId()).stream()
                .map(Bookmark::getContentItem).filter(item -> (type == null || item.getType() == type)
                        && (level == null || level.isBlank() || item.getLevels().stream().anyMatch(l -> level.equals(l.getCode()))))
                .map(this::toSummary).toList();
    }
    private ContentSummary toSummary(com.japanese.content.entity.ContentItem item) {
        String title; String reading=null; String description;
        if (item.getType()==ContentType.WORD) { title=item.getWord().getExpression(); reading=item.getWord().getReading(); description=item.getWord().getMeanings().stream().filter(m -> "ko".equals(m.getLanguageTag())).findFirst().map(Meaning::getText).orElse(""); }
        else { title=item.getGrammar().getPattern(); description=item.getGrammar().getExplanation(); }
        return new ContentSummary(item.getId(), item.getSlug(), item.getType(), title, reading, description,
                item.getLevels().stream().map(levelValue -> new ContentSummary.LevelSummary(levelValue.getSystem(), levelValue.getCode(), levelValue.getName())).toList(),
                item.getCategories().stream().map(category -> new ContentSummary.CategorySummary(category.getSlug(), category.getName())).toList());
    }
}
