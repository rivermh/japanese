package com.japanese.content.entity;

import com.japanese.account.entity.UserAccount;
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
@Table(name = "bookmarks", uniqueConstraints = @UniqueConstraint(
        name = "uk_bookmark_user_content", columnNames = {"user_account_id", "content_item_id"}))
public class Bookmark {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_item_id", nullable = false)
    private ContentItem contentItem;

    private Instant createdAt;
    protected Bookmark() { }
    public Bookmark(UserAccount userAccount, ContentItem contentItem) {
        this.userAccount = userAccount; this.contentItem = contentItem; this.createdAt = Instant.now();
    }
    public ContentItem getContentItem() { return contentItem; }
}
