package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Example;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.ReviewStatus;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentReviewHistoryRepository;
import com.japanese.content.repository.ContentSourceRepository;
import com.japanese.content.repository.LevelRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class ContentPublicationServiceTest {

    @Autowired ContentPublicationService publication;
    @Autowired ContentSourceRightsService rights;
    @Autowired ContentItemRepository contents;
    @Autowired ContentReviewHistoryRepository histories;
    @Autowired ContentSourceRepository sources;
    @Autowired LevelRepository levels;
    @Autowired UserAccountRepository accounts;
    @Autowired SampleContentDataLoader sample;

    private UserAccount admin;
    private String allowedSource;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        admin = accounts.save(new UserAccount("publication-admin-" + UUID.randomUUID(), null,
                "hash", "Publication admin", UserRole.ADMIN));
        allowedSource = allowedSource();
    }

    @Test
    void approvedReleasableContentCanPublishUnpublishAndRepublishWithHistory() {
        ContentItem item = approved(validWord("lifecycle", allowedSource, true));

        assertThat(publication.publish(item.getId(), admin, "initial release").published()).isTrue();
        assertThat(item.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(histories.findByContentItemIdOrderByReviewedAtDesc(item.getId()).get(0).getNote())
                .contains("[PUBLISH]", "published=false->true");

        assertThatThrownBy(() -> publication.unpublish(item.getId(), admin, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(publication.unpublish(item.getId(), admin, "content correction").published()).isFalse();
        assertThat(item.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(histories.findByContentItemIdOrderByReviewedAtDesc(item.getId()).get(0).getNote())
                .contains("[UNPUBLISH]", "content correction", "published=true->false");

        assertThat(publication.republish(item.getId(), admin, "correction verified").published()).isTrue();
        assertThat(histories.findByContentItemIdOrderByReviewedAtDesc(item.getId()).get(0).getNote())
                .contains("[REPUBLISH]");
    }

    @Test
    void pendingRejectedBlockedAndManualContentCannotPublish() {
        ContentItem pending = contents.saveAndFlush(validWord("pending", allowedSource, true));
        assertThatThrownBy(() -> publication.publish(pending.getId(), admin, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPROVED");
        assertThat(pending.isPublished()).isFalse();

        ContentItem rejected = validWord("rejected", allowedSource, true);
        rejected.reject("bad content");
        rejected = contents.saveAndFlush(rejected);
        Long rejectedId = rejected.getId();
        assertThatThrownBy(() -> publication.publish(rejectedId, admin, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPROVED");
        assertThat(rejected.isPublished()).isFalse();

        String unknownSource = "unknown-publication-" + UUID.randomUUID();
        sources.save(new ContentSource(unknownSource, unknownSource, "1", null, null, null, null));
        ContentItem blocked = approved(validWord("blocked", unknownSource, true));
        assertThatThrownBy(() -> publication.publish(blocked.getId(), admin, null))
                .isInstanceOf(ContentPublicationService.PublicationBlockedException.class)
                .hasMessageContaining("PUBLICATION_BLOCKED");

        ContentItem manual = approved(validWord("manual", allowedSource, false));
        assertThatThrownBy(() -> publication.publish(manual.getId(), admin, null))
                .isInstanceOf(ContentPublicationService.PublicationBlockedException.class)
                .hasMessageContaining("PUBLICATION_MANUAL_REVIEW_REQUIRED");
    }

    @Test
    void publishAndRepublishReevaluateTheGateImmediatelyBeforeVisibilityChange() {
        ContentItem item = approved(validWord("recheck", allowedSource, true));
        ContentSource source = sources.findBySourceRef(allowedSource).orElseThrow();
        rights.reviewRights(source.getId(), ContentSourceRightsStatus.BLOCKED,
                "rights withdrawn", false, null);

        assertThatThrownBy(() -> publication.publish(item.getId(), admin, null))
                .isInstanceOf(ContentPublicationService.PublicationBlockedException.class);
        assertThat(item.isPublished()).isFalse();
    }

    @Test
    void reopenAlwaysEndsPendingAndUnpublishedAndRecordsReason() {
        ContentItem item = approved(validWord("reopen", allowedSource, true));
        publication.publish(item.getId(), admin, null);

        assertThat(publication.reopenReview(item.getId(), admin, "meaning correction").changed()).isTrue();
        assertThat(item.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(item.isPublished()).isFalse();
        assertThat(histories.findByContentItemIdOrderByReviewedAtDesc(item.getId()).get(0).getNote())
                .contains("[REOPEN_REVIEW]", "meaning correction", "published=true->false");
    }

    @Test
    void diagnosticsDetectInvalidLegacyStatesWithoutChangingThem() {
        ContentItem pendingPublished = validWord("invalid-pending", allowedSource, true);
        ReflectionTestUtils.setField(pendingPublished, "published", true);
        pendingPublished = contents.saveAndFlush(pendingPublished);
        ContentItem rejectedPublished = validWord("invalid-rejected", allowedSource, true);
        rejectedPublished.reject("legacy invalid");
        ReflectionTestUtils.setField(rejectedPublished, "published", true);
        rejectedPublished = contents.saveAndFlush(rejectedPublished);

        var beforePending = pendingPublished.getReviewStatus();
        var beforeRejected = rejectedPublished.getReviewStatus();
        ContentPublicationService.Diagnostics result = publication.diagnostics();

        assertThat(result.invalidPublished()).isGreaterThanOrEqualTo(2);
        assertThat(result.pendingOrNullPublished()).isGreaterThanOrEqualTo(1);
        assertThat(result.rejectedPublished()).isGreaterThanOrEqualTo(1);
        assertThat(pendingPublished.getReviewStatus()).isEqualTo(beforePending);
        assertThat(rejectedPublished.getReviewStatus()).isEqualTo(beforeRejected);
        assertThat(pendingPublished.isPublished()).isTrue();
        assertThat(rejectedPublished.isPublished()).isTrue();
    }

    private ContentItem approved(ContentItem item) {
        item.approve(false);
        return contents.saveAndFlush(item);
    }

    private ContentItem validWord(String label, String sourceRef, boolean withExample) {
        ContentItem item = new ContentItem("publication-" + label + "-" + UUID.randomUUID(),
                ContentType.WORD, sourceRef, false);
        Word word = new Word("word-" + label, "reading-" + label, "noun", null);
        word.addMeaning(new Meaning("ko", "Korean meaning", 0));
        item.attachWord(word);
        item.addLevel(levels.findBySystemAndCode("JLPT", "N5").orElseThrow());
        if (withExample) item.addExample(new Example("Example sentence", null, "Example translation", 0));
        return item;
    }

    private String allowedSource() {
        String ref = "publication-source-" + UUID.randomUUID();
        ContentSource source = sources.save(new ContentSource(ref, ref, "1", null, null, null, null));
        rights.reviewRights(source.getId(), ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,
                "rights review", false, null);
        rights.reviewRights(source.getId(), ContentSourceRightsStatus.ALLOWED,
                "release allowed", false, null);
        return ref;
    }
}
