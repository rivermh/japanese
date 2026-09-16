package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.dto.TodayLearningPlan;
import com.japanese.learning.dto.TodayStudySessionView;
import com.japanese.learning.entity.LearnerProfile;
import com.japanese.learning.entity.StudyActivityType;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.entity.TodayStudySession;
import com.japanese.learning.entity.TodayStudySessionItem;
import com.japanese.learning.entity.TodayStudySessionState;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.TodayStudySessionItemRepository;
import com.japanese.learning.repository.TodayStudySessionRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.Optional;
import com.japanese.content.entity.ContentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists one fixed regular-learning plan per learner and Korean calendar day. */
@Service
public class TodayStudySessionService {
    private final LearnerProfileRepository profiles;
    private final TodayStudySessionRepository sessions;
    private final TodayStudySessionItemRepository items;
    private final ContentItemRepository contents;
    private final LearningService learningService;
    private final ZoneId zone;

    public TodayStudySessionService(LearnerProfileRepository profiles, TodayStudySessionRepository sessions,
                                    TodayStudySessionItemRepository items, ContentItemRepository contents,
                                    LearningService learningService,
                                    @Value("${japanese.learning.time-zone:Asia/Seoul}") String timeZone) {
        this.profiles=profiles; this.sessions=sessions; this.items=items; this.contents=contents; this.learningService=learningService; this.zone=ZoneId.of(timeZone);
    }

    @Transactional
    public TodayStudySessionView startOrResume(UserAccount account) {
        LearnerProfile profile = profile(account);
        LocalDate date = LocalDate.now(zone);
        TodayStudySession session = sessions.findByLearnerProfileIdAndSessionDate(profile.getId(), date)
                .orElseGet(() -> create(profile, date, learningService.todayPlan(account)));
        return view(session);
    }

    @Transactional
    public TodayStudySessionView current(UserAccount account) {
        LearnerProfile profile = profile(account);
        LocalDate date = LocalDate.now(zone);
        var session = sessions.findForUpdate(profile.getId(), date);
        return session.map(this::view).orElseGet(() -> startOrResume(account));
    }

    @Transactional
    public Optional<TodayStudySessionView> findCurrent(UserAccount account) {
        LearnerProfile profile = profile(account);
        return sessions.findByLearnerProfileIdAndSessionDate(profile.getId(), LocalDate.now(zone)).map(this::view);
    }

    @Transactional
    public TodayStudySessionView complete(UserAccount account, String slug, StudyResult result) {
        LearnerProfile profile = profile(account);
        LocalDate date = LocalDate.now(zone);
        if (sessions.findByLearnerProfileIdAndSessionDate(profile.getId(), date).isEmpty()) startOrResume(account);
        TodayStudySession session = sessions.findForUpdate(profile.getId(), date)
                .orElseThrow(() -> new NoSuchElementException("Today session not found"));
        withdrawUnavailable(session);
        completeIfFinished(session);
        var target = items.findBySessionIdAndContentItemSlug(session.getId(), slug)
                .orElseThrow(() -> new NoSuchElementException("Content is not in today session"));
        if (target.isCompleted() || session.getState() == TodayStudySessionState.COMPLETED) return view(session);
        var lockedContent = contents.findByIdForReview(target.getContentItem().getId())
                .orElseThrow(() -> new NoSuchElementException("Content not found"));
        if (!lockedContent.isPublished()) {
            target.withdraw();
            completeIfFinished(session);
            return view(session);
        }
        TodayStudySessionItem current = items.findIncompleteItems(session.getId()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Today session has no remaining item"));
        if (!current.getId().equals(target.getId())) throw new IllegalArgumentException("Complete the current card first");

        learningService.answer(account, slug, result, session.getSessionKey(), false);
        target.complete(result);
        withdrawUnavailable(session);
        completeIfFinished(session);
        return view(session);
    }

    private TodayStudySession create(LearnerProfile profile, LocalDate date, TodayLearningPlan plan) {
        TodayStudySession session = sessions.save(new TodayStudySession(profile, date,
                "today-" + profile.getId() + "-" + date + "-" + UUID.randomUUID(),
                plan.reviewCount(), plan.newWordCount(), plan.newGrammarCount()));
        int position = 0;
        for (var summary : plan.items()) {
            StudyActivityType type = position < plan.reviewCount() ? StudyActivityType.REVIEW : StudyActivityType.NEW;
            var content = contents.findBySlugAndPublishedTrue(summary.slug()).orElseThrow();
            items.save(new TodayStudySessionItem(session, content, position++, type));
        }
        if (plan.items().isEmpty()) session.complete();
        return session;
    }

    private TodayStudySessionView view(TodayStudySession session) {
        withdrawUnavailable(session);
        completeIfFinished(session);
        List<TodayStudySessionItem> all = items.findItems(session.getId());
        List<TodayStudySessionItem> active = all.stream().filter(item -> !item.isWithdrawn()).toList();
        List<TodayStudySessionItem> incomplete = active.stream().filter(item -> !item.isCompleted()).toList();
        int completed = active.size() - incomplete.size();
        int total = active.size();
        TodayStudySessionItem current = incomplete.isEmpty() ? null : incomplete.get(0);
        int completedReview = (int) active.stream().filter(TodayStudySessionItem::isCompleted)
                .filter(item -> item.getPlannedActivityType() == StudyActivityType.REVIEW).count();
        int completedWords = (int) active.stream().filter(TodayStudySessionItem::isCompleted)
                .filter(item -> item.getPlannedActivityType() == StudyActivityType.NEW)
                .filter(item -> item.getContentItem().getType() == ContentType.WORD).count();
        int completedGrammar = (int) active.stream().filter(TodayStudySessionItem::isCompleted)
                .filter(item -> item.getPlannedActivityType() == StudyActivityType.NEW)
                .filter(item -> item.getContentItem().getType() == ContentType.GRAMMAR).count();
        int reviewTarget = (int) active.stream().filter(item -> item.getPlannedActivityType() == StudyActivityType.REVIEW).count();
        int wordTarget = (int) active.stream().filter(item -> item.getPlannedActivityType() == StudyActivityType.NEW)
                .filter(item -> item.getContentItem().getType() == ContentType.WORD).count();
        int grammarTarget = (int) active.stream().filter(item -> item.getPlannedActivityType() == StudyActivityType.NEW)
                .filter(item -> item.getContentItem().getType() == ContentType.GRAMMAR).count();
        return new TodayStudySessionView(session.getSessionKey(), session.getState(), total, completed,
                reviewTarget, wordTarget, grammarTarget,
                current == null ? null : current.getContentItem().getSlug(), current == null ? null : current.getPlannedActivityType(),
                completedReview, completedWords, completedGrammar);
    }

    private void withdrawUnavailable(TodayStudySession session) {
        items.findIncompleteItems(session.getId()).stream()
                .filter(item -> !item.getContentItem().isPublished())
                .forEach(TodayStudySessionItem::withdraw);
    }

    private void completeIfFinished(TodayStudySession session) {
        if (session.getState() != TodayStudySessionState.COMPLETED
                && items.findIncompleteItems(session.getId()).isEmpty()) {
            session.complete();
        }
    }

    private LearnerProfile profile(UserAccount account) {
        return profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow(() -> new NoSuchElementException("Learner profile not found"));
    }
}
