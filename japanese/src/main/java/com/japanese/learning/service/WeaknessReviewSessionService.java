package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.dto.WeaknessReviewSessionView;
import com.japanese.learning.entity.LearnerProfile;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.entity.WeaknessReviewSession;
import com.japanese.learning.entity.WeaknessReviewSessionItem;
import com.japanese.learning.entity.WeaknessReviewSessionState;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.WeaknessReviewSessionItemRepository;
import com.japanese.learning.repository.WeaknessReviewSessionRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists an ordered, ten-card maximum RETRAIN session so it can be safely resumed. */
@Service
public class WeaknessReviewSessionService {
    private static final int MAX_ITEMS = 10;
    private final LearnerProfileRepository profiles;
    private final WeaknessReviewSessionRepository sessions;
    private final WeaknessReviewSessionItemRepository items;
    private final ContentItemRepository contents;
    private final WeaknessNoteService weaknessNotes;
    private final LearningService learning;

    public WeaknessReviewSessionService(LearnerProfileRepository profiles, WeaknessReviewSessionRepository sessions,
                                        WeaknessReviewSessionItemRepository items, ContentItemRepository contents,
                                        WeaknessNoteService weaknessNotes, LearningService learning) {
        this.profiles=profiles; this.sessions=sessions; this.items=items; this.contents=contents; this.weaknessNotes=weaknessNotes; this.learning=learning;
    }

    @Transactional
    public WeaknessReviewSessionView startOrResume(UserAccount account) {
        LearnerProfile profile=profileForUpdate(account);
        return sessions.findFirstByLearnerProfileIdAndStateOrderByIdDesc(profile.getId(), WeaknessReviewSessionState.ACTIVE)
                .map(this::view).orElseGet(() -> create(account, profile));
    }

    @Transactional(readOnly = true)
    public WeaknessReviewSessionView current(UserAccount account) {
        LearnerProfile profile=profile(account);
        return sessions.findFirstByLearnerProfileIdAndStateOrderByIdDesc(profile.getId(), WeaknessReviewSessionState.ACTIVE)
                .or(() -> sessions.findFirstByLearnerProfileIdOrderByIdDesc(profile.getId()))
                .map(this::view).orElse(null);
    }

    @Transactional
    public WeaknessReviewSessionView complete(UserAccount account, String slug, StudyResult result) {
        LearnerProfile profile=profileForUpdate(account);
        WeaknessReviewSession session=sessions.findFirstByLearnerProfileIdAndStateOrderByIdDesc(profile.getId(), WeaknessReviewSessionState.ACTIVE)
                .or(() -> sessions.findFirstByLearnerProfileIdOrderByIdDesc(profile.getId()))
                .orElseThrow(() -> new NoSuchElementException("No weakness review session"));
        WeaknessReviewSessionItem target=items.findBySessionIdAndContentItemSlug(session.getId(), slug)
                .orElseThrow(() -> new NoSuchElementException("Content is not in weakness review session"));
        if (target.isCompleted() || session.getState() == WeaknessReviewSessionState.COMPLETED) return view(session);
        WeaknessReviewSessionItem current=items.findIncompleteItems(session.getId()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Weakness review session has no remaining item"));
        if (!current.getId().equals(target.getId())) throw new IllegalArgumentException("Complete the current card first");
        learning.answer(account, slug, result, session.getSessionKey(), true);
        target.complete();
        if (items.findIncompleteItems(session.getId()).isEmpty()) session.complete();
        return view(session);
    }

    private WeaknessReviewSessionView create(UserAccount account, LearnerProfile profile) {
        WeaknessReviewSession session=sessions.save(new WeaknessReviewSession(profile,
                "weakness-" + profile.getId() + "-" + UUID.randomUUID()));
        int position=0;
        for (var summary : weaknessNotes.focusedReviewCandidates(account, MAX_ITEMS)) {
            var content=contents.findBySlugAndPublishedTrue(summary.slug()).orElseThrow();
            items.save(new WeaknessReviewSessionItem(session, content, position));
            position++;
        }
        if (position==0) session.complete();
        return view(session);
    }

    private WeaknessReviewSessionView view(WeaknessReviewSession session) {
        var incomplete=items.findIncompleteItems(session.getId());
        int completed=(int)items.countBySessionIdAndCompletedTrue(session.getId());
        int total=completed+incomplete.size();
        return new WeaknessReviewSessionView(session.getSessionKey(), session.getId(), session.getState(), total, completed,
                incomplete.isEmpty()?null:incomplete.get(0).getContentItem().getSlug());
    }
    private LearnerProfile profile(UserAccount account){return profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow();}
    private LearnerProfile profileForUpdate(UserAccount account){return profiles.findByUserAccountLoginIdForUpdate(account.getLoginId()).orElseThrow();}
}
