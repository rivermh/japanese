package com.japanese.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.japanese.content.entity.Word;
import com.japanese.content.repository.BookmarkRepository;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentSourceRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.service.BookmarkService;
import com.japanese.content.service.ContentPublicationService;
import com.japanese.content.service.ContentSourceRightsService;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.QuizAttemptRepository;
import com.japanese.learning.repository.StudyCollectionItemRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class ContentVisibilitySafetyTest {

    @Autowired SampleContentDataLoader sample;
    @Autowired UserAccountRepository accounts;
    @Autowired ContentItemRepository contents;
    @Autowired ContentSourceRepository sources;
    @Autowired ContentSourceRightsService rights;
    @Autowired ContentPublicationService publication;
    @Autowired LevelRepository levels;
    @Autowired BookmarkService bookmarks;
    @Autowired BookmarkRepository bookmarkRelations;
    @Autowired StudyCollectionService collections;
    @Autowired StudyCollectionItemRepository collectionRelations;
    @Autowired LearningService learning;
    @Autowired LearnerProfileRepository profiles;
    @Autowired LearningProgressRepository progress;
    @Autowired StudyRecordRepository records;
    @Autowired QuizAttemptRepository quizAttempts;

    private UserAccount user;
    private UserAccount admin;
    private ContentItem content;

    @BeforeEach
    void setUp() throws Exception {
        sample.run();
        user = accounts.save(new UserAccount("visibility-user-" + UUID.randomUUID(), null,
                "hash", "Visibility user", UserRole.USER));
        admin = accounts.save(new UserAccount("visibility-admin-" + UUID.randomUUID(), null,
                "hash", "Visibility admin", UserRole.ADMIN));
        learning.overview(user);

        String sourceRef = "visibility-source-" + UUID.randomUUID();
        ContentSource source = sources.save(new ContentSource(sourceRef, sourceRef, "1", null, null, null, null));
        rights.reviewRights(source.getId(), ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED,
                "rights review", false, null);
        rights.reviewRights(source.getId(), ContentSourceRightsStatus.ALLOWED,
                "release allowed", false, null);

        content = new ContentItem("visibility-content-" + UUID.randomUUID(), ContentType.WORD, sourceRef, false);
        Word word = new Word("visibility-word", "visibility-reading", "noun", null);
        word.addMeaning(new Meaning("ko", "visibility meaning", 0));
        content.attachWord(word);
        content.addExample(new Example("Example sentence", null, "Example translation", 0));
        content.addLevel(levels.findBySystemAndCode("JLPT", "N5").orElseThrow());
        content.approve(false);
        content = contents.saveAndFlush(content);
        publication.publish(content.getId(), admin, "test release");
    }

    @Test
    void unpublishHidesBookmarkAndCollectionButPreservesRelationsAndLearningHistory() {
        bookmarks.toggle(user, content.getSlug());
        var collection = collections.create(user, "Visibility collection");
        collections.addContent(user, collection.id(), content.getSlug());
        learning.answer(user, content.getSlug(), StudyResult.CORRECT, "visibility-study", false);
        learning.recordQuizAnswer(user, 1L, true);
        String learnerKey = profiles.findByUserAccountLoginId(user.getLoginId()).orElseThrow().getLearnerKey();
        long progressBefore = progress.count();
        long recordsBefore = records.count();
        long quizBefore = quizAttempts.count();

        publication.unpublish(content.getId(), admin, "temporary withdrawal");

        assertThat(bookmarkRelations.countByUserAccountLoginId(user.getLoginId())).isEqualTo(1);
        assertThat(bookmarks.count(user)).isZero();
        assertThat(bookmarks.list(user, null, null)).isEmpty();
        assertThat(collectionRelations.countByStudyCollectionId(collection.id())).isEqualTo(1);
        assertThat(collections.list(user)).singleElement().satisfies(summary -> assertThat(summary.contentCount()).isZero());
        assertThat(collections.details(user, collection.id()).contents()).isEmpty();
        assertThat(progress.count()).isEqualTo(progressBefore);
        assertThat(records.count()).isEqualTo(recordsBefore);
        assertThat(quizAttempts.count()).isEqualTo(quizBefore);
        assertThat(progress.findByLearnerProfileLearnerKeyAndContentItemId(learnerKey, content.getId())).isPresent();

        publication.republish(content.getId(), admin, "withdrawal resolved");

        assertThat(bookmarks.list(user, null, null)).extracting(item -> item.slug()).containsExactly(content.getSlug());
        assertThat(collections.list(user)).singleElement().satisfies(summary -> assertThat(summary.contentCount()).isEqualTo(1));
        assertThat(collections.details(user, collection.id()).contents()).extracting(item -> item.slug())
                .containsExactly(content.getSlug());
    }
}
