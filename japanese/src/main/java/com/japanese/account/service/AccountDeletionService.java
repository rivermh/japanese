package com.japanese.account.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.account.entity.UserRole;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.learning.entity.LearnerProfile;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearnerStudyPreferenceRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes only data owned by a normal learner account; shared content is never deleted. */
@Service
public class AccountDeletionService {
    public enum Result { DELETED, INVALID_PASSWORD, NOT_SUPPORTED_FOR_ADMIN }

    private final UserAccountRepository accounts;
    private final LearnerProfileRepository profiles;
    private final LearnerStudyPreferenceRepository preferences;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public AccountDeletionService(UserAccountRepository accounts, LearnerProfileRepository profiles,
            LearnerStudyPreferenceRepository preferences, PasswordEncoder passwordEncoder, EntityManager entityManager) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.preferences = preferences;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    /** Deletes the authenticated account only after password re-authentication. */
    @Transactional
    public Result deleteOwnAccount(UserAccount authenticatedAccount, String currentPassword) {
        UserAccount account = accounts.findByIdForUpdate(authenticatedAccount.getId())
                .orElseThrow(() -> new IllegalStateException("계정을 찾을 수 없습니다."));
        if (account.getRole() != UserRole.USER) return Result.NOT_SUPPORTED_FOR_ADMIN;
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            return Result.INVALID_PASSWORD;
        }
        deleteDirectAccountData(account.getId());
        profiles.findByUserAccountLoginIdForUpdate(account.getLoginId()).ifPresent(this::deleteLearnerProfileData);
        // Bulk profile deletion leaves the locked profile instance stale in this persistence context.
        // Clear it before removing the parent account so Hibernate never tries to re-flush that graph.
        entityManager.clear();
        delete("delete from UserAccount account where account.id = :accountId", account.getId());
        return Result.DELETED;
    }

    private void deleteDirectAccountData(Long accountId) {
        delete("delete from Bookmark bookmark where bookmark.userAccount.id = :accountId", accountId);
        delete("delete from StudyQueueEntry entry where entry.userAccount.id = :accountId", accountId);
        delete("delete from StudyCollectionItem item where item.studyCollection.userAccount.id = :accountId", accountId);
        delete("delete from StudyCollection collection where collection.userAccount.id = :accountId", accountId);
        delete("delete from EmailVerificationToken token where token.user.id = :accountId", accountId);
        delete("delete from PasswordResetToken token where token.user.id = :accountId", accountId);
    }

    private void deleteLearnerProfileData(LearnerProfile profile) {
        Long profileId = profile.getId();
        delete("delete from QuizAttempt attempt where attempt.learnerProfile.id = :profileId", profileId);
        delete("delete from QuizSessionItem item where item.session.learnerProfile.id = :profileId", profileId);
        delete("delete from QuizSession session where session.learnerProfile.id = :profileId", profileId);
        delete("delete from TodayStudySessionItem item where item.session.learnerProfile.id = :profileId", profileId);
        delete("delete from TodayStudySession session where session.learnerProfile.id = :profileId", profileId);
        delete("delete from WeaknessReviewSessionItem item where item.session.learnerProfile.id = :profileId", profileId);
        delete("delete from WeaknessReviewSession session where session.learnerProfile.id = :profileId", profileId);
        delete("delete from GrammarConfirmationAttempt attempt where attempt.learnerProfile.id = :profileId", profileId);
        delete("delete from LearningProgress progress where progress.learnerProfile.id = :profileId", profileId);
        delete("delete from LearningStreak streak where streak.learnerProfile.id = :profileId", profileId);
        delete("delete from StudyRecord record where record.learnerProfile.id = :profileId", profileId);
        preferences.findByLearnerProfileId(profileId).ifPresent(preferences::delete);
        entityManager.flush();
        delete("delete from LearnerProfile profile where profile.id = :profileId", profileId);
    }

    private void delete(String jpql, Long id) {
        String parameter = jpql.contains(":accountId") ? "accountId" : "profileId";
        entityManager.createQuery(jpql).setParameter(parameter, id).executeUpdate();
    }
}
