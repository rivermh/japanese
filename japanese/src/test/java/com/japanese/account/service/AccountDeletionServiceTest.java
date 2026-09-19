package com.japanese.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.account.dto.RegistrationRequest;
import com.japanese.account.entity.UserAccount;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.GrammarConfirmationQuestion;
import com.japanese.content.entity.GrammarConfirmationType;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.GrammarConfirmationQuestionRepository;
import com.japanese.learning.entity.GrammarConfirmationAttempt;
import com.japanese.learning.entity.QuizAttempt;
import com.japanese.learning.entity.QuizMode;
import com.japanese.learning.entity.QuizQuestionType;
import com.japanese.learning.entity.QuizSession;
import com.japanese.learning.entity.QuizSessionItem;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.entity.WeaknessReviewSession;
import com.japanese.learning.entity.WeaknessReviewSessionItem;
import com.japanese.learning.repository.GrammarConfirmationAttemptRepository;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearnerStudyPreferenceRepository;
import com.japanese.learning.repository.QuizAttemptRepository;
import com.japanese.learning.repository.QuizSessionItemRepository;
import com.japanese.learning.repository.QuizSessionRepository;
import com.japanese.learning.repository.WeaknessReviewSessionItemRepository;
import com.japanese.learning.repository.WeaknessReviewSessionRepository;
import com.japanese.learning.service.LearningService;
import com.japanese.learning.service.StudyCollectionService;
import com.japanese.learning.service.StudyQueueService;
import com.japanese.learning.service.TodayStudySessionService;
import com.japanese.content.service.BookmarkService;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.Mockito;

@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class AccountDeletionServiceTest {
    @Autowired private SampleContentDataLoader samples;
    @Autowired private AccountService registrations;
    @Autowired private AccountManagementService accountManagement;
    @Autowired private AccountDeletionService deletion;
    @Autowired private UserAccountRepository accounts;
    @Autowired private LearnerProfileRepository profiles;
    @Autowired private ContentItemRepository contents;
    @Autowired private LearningService learning;
    @Autowired private BookmarkService bookmarks;
    @Autowired private StudyQueueService queue;
    @Autowired private TodayStudySessionService todaySessions;
    @Autowired private StudyCollectionService collections;
    @Autowired private QuizSessionRepository quizSessions;
    @Autowired private QuizSessionItemRepository quizItems;
    @Autowired private QuizAttemptRepository quizAttempts;
    @Autowired private WeaknessReviewSessionRepository weaknessSessions;
    @Autowired private WeaknessReviewSessionItemRepository weaknessItems;
    @Autowired private GrammarConfirmationQuestionRepository questions;
    @Autowired private GrammarConfirmationAttemptRepository confirmationAttempts;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private LearnerStudyPreferenceRepository deletionPreferences;

    @BeforeEach
    void seed() throws Exception {
        samples.run();
    }

    @Test
    void deletesOnlyTargetOwnedDataAndPreservesSharedContent() {
        UserAccount target = account("delete-target");
        UserAccount other = account("delete-other");
        var targetProfile = profiles.findByUserAccountLoginId(target.getLoginId()).orElseThrow();
        var otherProfile = profiles.findByUserAccountLoginId(other.getLoginId()).orElseThrow();
        var word = contents.findBySlugAndPublishedTrue("taberu").orElseThrow();
        var grammar = contents.findBySlugAndPublishedTrue("temo-ii").orElseThrow();

        learning.answer(target, word.getSlug(), StudyResult.CORRECT, "target-record", false);
        learning.answer(other, word.getSlug(), StudyResult.CORRECT, "other-record", false);
        bookmarks.toggle(target, word.getSlug());
        queue.add(target, "server");
        var collection = collections.create(target, "삭제 대상 단어장");
        collections.addContent(target, collection.id(), word.getSlug());
        todaySessions.startOrResume(target);
        accountManagement.requestEmailVerification(target);
        accountManagement.requestPasswordReset(target.getEmail());

        QuizSession quiz = quizSessions.save(new QuizSession(targetProfile, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), QuizMode.QUICK, 1));
        QuizSessionItem quizItem = quizItems.save(new QuizSessionItem(quiz, word, 0,
                QuizQuestionType.WORD_JAPANESE_TO_MEANING, "뜻", "문제", "[]", "답", "설명"));
        quizAttempts.save(QuizAttempt.forSessionItem(targetProfile, quizItem, StudyResult.CORRECT, 10));
        WeaknessReviewSession weakness = weaknessSessions.save(new WeaknessReviewSession(targetProfile, UUID.randomUUID().toString()));
        weaknessItems.save(new WeaknessReviewSessionItem(weakness, word, 0));
        GrammarConfirmationQuestion question = questions.save(new GrammarConfirmationQuestion(
                grammar.getGrammar(), GrammarConfirmationType.MEANING_MATCH, "질문", null, "설명", "test"));
        confirmationAttempts.save(new GrammarConfirmationAttempt(targetProfile, question, StudyResult.INCORRECT));
        entityManager.flush();

        long contentItems = count("ContentItem");
        long words = count("Word");
        long grammars = count("Grammar");
        long meanings = count("Meaning");
        long examples = count("Example");
        long levels = count("Level");
        long imported = count("ImportedSourceRecord");
        long privateApkg = countTable("private_apkg_notes");
        long candidates = countTable("normalized_content_candidates");
        long matchPairs = countTable("normalized_candidate_match_pairs");
        long matchEvidence = countTable("normalized_candidate_match_evidence");
        long pairReviews = countTable("normalized_candidate_pair_reviews");
        long pairReviewHistory = countTable("normalized_candidate_pair_review_history");
        long canonicalGroups = countTable("normalized_candidate_canonical_groups");
        long canonicalMembers = countTable("normalized_candidate_canonical_group_members");
        long canonicalEdges = countTable("normalized_candidate_canonical_group_edges");

        assertThat(deletion.deleteOwnAccount(target, "delete-password-123"))
                .isEqualTo(AccountDeletionService.Result.DELETED);
        entityManager.flush();
        entityManager.clear();

        assertThat(accounts.findByLoginId(target.getLoginId())).isEmpty();
        assertThat(profiles.findByUserAccountLoginId(target.getLoginId())).isEmpty();
        assertThat(countWhere("Bookmark", "bookmark.userAccount.loginId", target.getLoginId())).isZero();
        assertThat(countWhere("StudyQueueEntry", "entry.userAccount.loginId", target.getLoginId())).isZero();
        assertThat(countWhere("StudyCollection", "collection.userAccount.loginId", target.getLoginId())).isZero();
        assertThat(countWhere("StudyRecord", "record.learnerProfile.learnerKey", target.getLoginId())).isZero();
        assertThat(countWhere("LearningProgress", "progress.learnerProfile.learnerKey", target.getLoginId())).isZero();
        assertThat(countWhere("LearningStreak", "streak.learnerProfile.learnerKey", target.getLoginId())).isZero();
        assertThat(countWhere("TodayStudySession", "session.learnerProfile.learnerKey", target.getLoginId())).isZero();
        assertThat(countWhere("QuizAttempt", "attempt.learnerProfile.learnerKey", target.getLoginId())).isZero();
        assertThat(countWhere("QuizSession", "session.learnerProfile.learnerKey", target.getLoginId())).isZero();
        assertThat(countWhere("WeaknessReviewSession", "session.learnerProfile.learnerKey", target.getLoginId())).isZero();
        assertThat(countWhere("GrammarConfirmationAttempt", "attempt.learnerProfile.learnerKey", target.getLoginId())).isZero();
        assertThat(countWhere("EmailVerificationToken", "token.user.loginId", target.getLoginId())).isZero();
        assertThat(countWhere("PasswordResetToken", "token.user.loginId", target.getLoginId())).isZero();

        assertThat(accounts.findByLoginId(other.getLoginId())).isPresent();
        assertThat(profiles.findByUserAccountLoginId(other.getLoginId())).isPresent();
        assertThat(countWhere("StudyRecord", "record.learnerProfile.learnerKey", otherProfile.getLearnerKey())).isEqualTo(1);
        assertThat(count("ContentItem")).isEqualTo(contentItems);
        assertThat(count("Word")).isEqualTo(words);
        assertThat(count("Grammar")).isEqualTo(grammars);
        assertThat(count("Meaning")).isEqualTo(meanings);
        assertThat(count("Example")).isEqualTo(examples);
        assertThat(count("Level")).isEqualTo(levels);
        assertThat(count("ImportedSourceRecord")).isEqualTo(imported);
        assertThat(countTable("private_apkg_notes")).isEqualTo(privateApkg);
        assertThat(countTable("normalized_content_candidates")).isEqualTo(candidates);
        assertThat(countTable("normalized_candidate_match_pairs")).isEqualTo(matchPairs);
        assertThat(countTable("normalized_candidate_match_evidence")).isEqualTo(matchEvidence);
        assertThat(countTable("normalized_candidate_pair_reviews")).isEqualTo(pairReviews);
        assertThat(countTable("normalized_candidate_pair_review_history")).isEqualTo(pairReviewHistory);
        assertThat(countTable("normalized_candidate_canonical_groups")).isEqualTo(canonicalGroups);
        assertThat(countTable("normalized_candidate_canonical_group_members")).isEqualTo(canonicalMembers);
        assertThat(countTable("normalized_candidate_canonical_group_edges")).isEqualTo(canonicalEdges);
    }

    @Test
    void invalidPasswordLeavesAccountAndDataUntouched() {
        UserAccount target = account("delete-password");
        learning.answer(target, "taberu", StudyResult.CORRECT, "before-invalid-delete", false);
        assertThat(deletion.deleteOwnAccount(target, "wrong-password"))
                .isEqualTo(AccountDeletionService.Result.INVALID_PASSWORD);
        assertThat(accounts.findByLoginId(target.getLoginId())).isPresent();
        assertThat(countWhere("StudyRecord", "record.learnerProfile.learnerKey", target.getLoginId())).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void rollsBackEarlierDependentDeletesWhenLaterDeletionFails() {
        UserAccount target = account("delete-rollback");
        var word = contents.findBySlugAndPublishedTrue("taberu").orElseThrow();
        bookmarks.toggle(target, word.getSlug());

        Mockito.doThrow(new RuntimeException("simulated account deletion failure"))
                .when(deletionPreferences).findByLearnerProfileId(Mockito.anyLong());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> deletion.deleteOwnAccount(target, "delete-password-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated account deletion failure");

        assertThat(accounts.findByLoginId(target.getLoginId())).isPresent();
        assertThat(profiles.findByUserAccountLoginId(target.getLoginId())).isPresent();
        assertThat(countWhere("Bookmark", "bookmark.userAccount.loginId", target.getLoginId())).isEqualTo(1);
    }

    private UserAccount account(String prefix) {
        String id = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        RegistrationRequest request = new RegistrationRequest();
        request.setLoginId(id);
        request.setEmail(id + "@example.test");
        request.setDisplayName(id);
        request.setPassword("delete-password-123");
        return registrations.register(request);
    }

    private long count(String entity) {
        return entityManager.createQuery("select count(value) from " + entity + " value", Long.class).getSingleResult();
    }

    private long countWhere(String entity, String property, String value) {
        String alias = property.substring(0, property.indexOf('.'));
        return entityManager.createQuery("select count(" + alias + ") from " + entity + " " + alias
                + " where " + property + " = :value", Long.class).setParameter("value", value).getSingleResult();
    }

    private long countTable(String table) {
        return ((Number) entityManager.createNativeQuery("select count(*) from " + table).getSingleResult()).longValue();
    }
}
