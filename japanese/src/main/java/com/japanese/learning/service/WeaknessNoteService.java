package com.japanese.learning.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.dto.ContentSummary;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.Meaning;
import com.japanese.learning.dto.WeaknessNoteItem;
import com.japanese.learning.dto.WeaknessNotebook;
import com.japanese.learning.dto.WeaknessStatus;
import com.japanese.learning.dto.WeaknessStreakAggregate;
import com.japanese.learning.dto.WeaknessStudyAggregate;
import com.japanese.learning.entity.LearningProgress;
import com.japanese.learning.entity.StudyResult;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.learning.repository.GrammarConfirmationAttemptRepository;
import com.japanese.learning.repository.LearnerProfileRepository;
import com.japanese.learning.repository.LearningProgressRepository;
import com.japanese.learning.repository.StudyRecordRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds a transparent weakness notebook from bounded, database-side attempt aggregates. */
@Service
public class WeaknessNoteService {
    static final int LOOKBACK_DAYS = 30;
    private static final int DEFAULT_SECTION_SIZE = 20;
    private static final int MAX_SECTION_SIZE = 50;
    private static final int CANDIDATE_SCAN_SIZE = 250;
    private final LearnerProfileRepository profiles;
    private final StudyRecordRepository records;
    private final GrammarConfirmationAttemptRepository confirmations;
    private final LearningProgressRepository progresses;
    private final ContentItemRepository contents;

    public WeaknessNoteService(LearnerProfileRepository profiles, StudyRecordRepository records,
                               GrammarConfirmationAttemptRepository confirmations,
                               LearningProgressRepository progresses, ContentItemRepository contents) {
        this.profiles=profiles; this.records=records; this.confirmations=confirmations; this.progresses=progresses; this.contents=contents;
    }

    @Transactional(readOnly = true)
    public WeaknessNotebook notebook(UserAccount account) { return notebook(account, DEFAULT_SECTION_SIZE); }

    @Transactional(readOnly = true)
    public WeaknessNotebook notebook(UserAccount account, int requestedSize) {
        return notebookForDays(account, LOOKBACK_DAYS, requestedSize);
    }

    /** Reuses the same evidence rules for bounded reports such as a seven-day weekly view. */
    @Transactional(readOnly = true)
    public WeaknessNotebook notebookForDays(UserAccount account, int requestedDays, int requestedSize) {
        int size=Math.min(Math.max(requestedSize, 1), MAX_SECTION_SIZE);
        int days=Math.min(Math.max(requestedDays, 1), 90);
        String learnerKey=profile(account).getLearnerKey();
        Instant since=Instant.now().minusSeconds(days * 86_400L);
        PageRequest scan=PageRequest.of(0, CANDIDATE_SCAN_SIZE);
        Map<Long, Candidate> candidates=new LinkedHashMap<>();
        mergeStudy(candidates, records.summarizeWeaknesses(learnerKey, since, StudyResult.CORRECT, StudyResult.INCORRECT, scan), false);
        mergeStudy(candidates, confirmations.summarizeWeaknesses(learnerKey, since, StudyResult.CORRECT, StudyResult.INCORRECT, scan), true);
        mergeStreaks(candidates, records.findCurrentIncorrectStreaks(learnerKey, since, StudyResult.CORRECT, StudyResult.INCORRECT, 2, scan));
        mergeStreaks(candidates, confirmations.findCurrentIncorrectStreaks(learnerKey, since, StudyResult.CORRECT, StudyResult.INCORRECT, 2, scan));
        if (candidates.isEmpty()) return new WeaknessNotebook(List.of(), List.of(), List.of(), List.of(), 0);

        Map<Long, LearningProgress> progressByContent=progresses
                .findByLearnerProfileLearnerKeyAndContentItemIdIn(learnerKey, candidates.keySet()).stream()
                .collect(Collectors.toMap(progress -> progress.getContentItem().getId(), progress -> progress));
        Map<Long, ContentSummary> summaries=contents.findPublishedForSummaryByIdIn(candidates.keySet()).stream()
                .collect(Collectors.toMap(ContentItem::getId, this::summary));
        List<WeaknessNoteItem> items=candidates.values().stream()
                .filter(candidate -> summaries.containsKey(candidate.contentId))
                .map(candidate -> toItem(candidate, summaries.get(candidate.contentId), progressByContent.get(candidate.contentId)))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing((WeaknessNoteItem item) -> item.status() == WeaknessStatus.CURRENT ? 0 : 1)
                        .thenComparing(WeaknessNoteItem::consecutiveIncorrectCount, Comparator.reverseOrder())
                        .thenComparing(item -> item.regularIncorrectCount() + item.confirmationIncorrectCount(), Comparator.reverseOrder())
                        .thenComparing(WeaknessNoteItem::lastIncorrectAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<WeaknessNoteItem> current=items.stream().filter(item -> item.status()==WeaknessStatus.CURRENT).toList();
        return new WeaknessNotebook(
                current.stream().sorted(Comparator.comparing(WeaknessNoteItem::lastIncorrectAt, Comparator.nullsLast(Comparator.reverseOrder()))).limit(size).toList(),
                current.stream().filter(item -> totalIncorrect(item) >= 2).limit(size).toList(),
                current.stream().filter(item -> item.consecutiveIncorrectCount() >= 2).limit(size).toList(),
                items.stream().filter(item -> item.status()==WeaknessStatus.IMPROVING).limit(size).toList(),
                Math.min(current.size(), 10));
    }

    @Transactional(readOnly = true)
    public List<ContentSummary> focusedReviewCandidates(UserAccount account, int limit) {
        WeaknessNotebook notebook=notebook(account, MAX_SECTION_SIZE);
        Map<Long, ContentSummary> unique=new LinkedHashMap<>();
        add(unique, notebook.consecutive()); add(unique, notebook.frequent()); add(unique, notebook.recent());
        return unique.values().stream().limit(Math.min(Math.max(limit, 1), 10)).toList();
    }

    private void add(Map<Long, ContentSummary> target, Collection<WeaknessNoteItem> items) {
        items.forEach(item -> target.putIfAbsent(item.content().id(), item.content()));
    }

    private void mergeStudy(Map<Long, Candidate> candidates, List<WeaknessStudyAggregate> aggregates, boolean confirmation) {
        aggregates.forEach(value -> {
            Candidate candidate=candidates.computeIfAbsent(value.contentItemId(), Candidate::new);
            if (confirmation) candidate.confirmation=value; else candidate.regular=value;
        });
    }
    private void mergeStreaks(Map<Long, Candidate> candidates, List<WeaknessStreakAggregate> streaks) {
        streaks.forEach(value -> candidates.computeIfAbsent(value.contentItemId(), Candidate::new).consecutiveIncorrect += value.count());
    }
    private WeaknessNoteItem toItem(Candidate candidate, ContentSummary content, LearningProgress progress) {
        WeaknessStudyAggregate regular=candidate.regular; WeaknessStudyAggregate confirmation=candidate.confirmation;
        long regularAttempts=regular==null?0:regular.incorrectCount()+regular.correctCount();
        long regularIncorrect=regular==null?0:regular.incorrectCount();
        long confirmationAttempts=confirmation==null?0:confirmation.incorrectCount()+confirmation.correctCount();
        long confirmationIncorrect=confirmation==null?0:confirmation.incorrectCount();
        Instant lastIncorrect=latest(regular==null?null:regular.lastIncorrectAt(), confirmation==null?null:confirmation.lastIncorrectAt());
        Instant lastCorrect=latest(regular==null?null:regular.lastCorrectAt(), confirmation==null?null:confirmation.lastCorrectAt());
        boolean current=lastIncorrect!=null && (lastCorrect==null || !lastCorrect.isAfter(lastIncorrect));
        boolean improved=!current && total(regularIncorrect, confirmationIncorrect)>=2 && (
                (progress!=null && progress.getConsecutiveCorrect()>=2) ||
                (confirmation!=null && confirmation.correctCount()>=2));
        if (!current && !improved) return null;
        WeaknessStatus status=current ? WeaknessStatus.CURRENT : WeaknessStatus.IMPROVING;
        return new WeaknessNoteItem(content, status, regularAttempts, regularIncorrect, confirmationAttempts, confirmationIncorrect,
                candidate.consecutiveIncorrect, lastIncorrect, reason(regularIncorrect, confirmationIncorrect, candidate.consecutiveIncorrect, status));
    }
    private String reason(long regular, long confirmation, long streak, WeaknessStatus status) {
        if (status==WeaknessStatus.IMPROVING) return "최근 정답 기록으로 약점이 개선되고 있습니다.";
        if (streak>=2) return "최근 정답 이후 연속 " + streak + "회 오답";
        List<String> sources=new ArrayList<>();
        if (regular>0) sources.add("SRS 학습 오답 " + regular + "회");
        if (confirmation>0) sources.add("문법 확인 문제 오답 " + confirmation + "회");
        return String.join(" · ", sources);
    }
    private long totalIncorrect(WeaknessNoteItem item) { return item.regularIncorrectCount()+item.confirmationIncorrectCount(); }
    private long total(long first,long second){return first+second;}
    private Instant latest(Instant first, Instant second) { if(first==null)return second; if(second==null)return first; return first.isAfter(second)?first:second; }
    private com.japanese.learning.entity.LearnerProfile profile(UserAccount account) { return profiles.findByUserAccountLoginId(account.getLoginId()).orElseThrow(); }
    private ContentSummary summary(ContentItem item) {
        String title=item.getType()==com.japanese.content.entity.ContentType.WORD ? item.getWord().getExpression() : item.getGrammar().getPattern();
        String reading=item.getType()==com.japanese.content.entity.ContentType.WORD ? item.getWord().getReading() : null;
        String description=item.getType()==com.japanese.content.entity.ContentType.WORD ? item.getWord().getMeanings().stream().filter(meaning -> "ko".equals(meaning.getLanguageTag())).findFirst().map(Meaning::getText).orElse("") : item.getGrammar().getExplanation();
        return new ContentSummary(item.getId(), item.getSlug(), item.getType(), title, reading, description,
                item.getLevels().stream().map(level -> new ContentSummary.LevelSummary(level.getSystem(), level.getCode(), level.getName())).toList(),
                item.getCategories().stream().map(category -> new ContentSummary.CategorySummary(category.getSlug(), category.getName())).toList());
    }
    private static final class Candidate { private final Long contentId; private WeaknessStudyAggregate regular; private WeaknessStudyAggregate confirmation; private long consecutiveIncorrect; private Candidate(Long contentId){this.contentId=contentId;} }
}
