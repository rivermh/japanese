package com.japanese.content.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentReviewHistory;
import com.japanese.content.entity.ReviewStatus;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentReviewHistoryRepository;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns explicit visibility transitions while preserving content and learning relations. */
@Service
public class ContentPublicationService {

    private final ContentItemRepository contents;
    private final ContentReviewHistoryRepository histories;
    private final ContentReleaseGateService releaseGate;

    public ContentPublicationService(ContentItemRepository contents,
            ContentReviewHistoryRepository histories, ContentReleaseGateService releaseGate) {
        this.contents = contents;
        this.histories = histories;
        this.releaseGate = releaseGate;
    }

    @Transactional
    public Result publish(Long contentId, UserAccount reviewer, String note) {
        return makeVisible(contentId, reviewer, note, "PUBLISH");
    }

    @Transactional
    public Result republish(Long contentId, UserAccount reviewer, String note) {
        return makeVisible(contentId, reviewer, note, "REPUBLISH");
    }

    @Transactional
    public Result unpublish(Long contentId, UserAccount reviewer, String reason) {
        String normalizedReason = requiredReason(reason);
        ContentItem item = locked(contentId);
        if (!item.isPublished()) {
            return result(false, item, "이미 미공개 상태입니다.");
        }
        if (item.getReviewStatus() != ReviewStatus.APPROVED) {
            throw new IllegalStateException("잘못된 공개 상태입니다. 진단 후 별도로 정규화해야 합니다.");
        }
        item.unpublishApproved();
        record(item, reviewer, "UNPUBLISH", normalizedReason, ReviewStatus.APPROVED, ReviewStatus.APPROVED, true, false);
        return result(true, item, "콘텐츠를 비공개로 전환했습니다.");
    }

    @Transactional
    public Result reopenReview(Long contentId, UserAccount reviewer, String reason) {
        String normalizedReason = requiredReason(reason);
        ContentItem item = locked(contentId);
        if (item.getReviewStatus() == ReviewStatus.PENDING && !item.isPublished()) {
            return result(false, item, "이미 검토 대기 상태입니다.");
        }
        ReviewStatus previous = item.getReviewStatus();
        boolean wasPublished = item.isPublished();
        item.reopenReview();
        record(item, reviewer, "REOPEN_REVIEW", normalizedReason, previous, ReviewStatus.PENDING,
                wasPublished, false);
        return result(true, item, "콘텐츠를 비공개 처리하고 재검토 상태로 전환했습니다.");
    }

    @Transactional(readOnly = true)
    public Diagnostics diagnostics() {
        return new Diagnostics(
                contents.countInvalidPublishedState(),
                contents.countPublishedPendingOrNull(),
                contents.countByPublishedTrueAndReviewStatus(ReviewStatus.REJECTED));
    }

    @Transactional
    public Result publishForBatch(ContentItem item, UserAccount reviewer, Long batchId,
                                  ContentReleaseGateService.Result decision,
                                  Set<ContentReleaseIssueCode> acknowledgedManualIssues,
                                  String overrideReason) {
        if (item.isPublished()) throw new IllegalStateException("BATCH_TARGET_ALREADY_PUBLISHED");
        if (item.getReviewStatus() != ReviewStatus.PENDING && item.getReviewStatus() != ReviewStatus.APPROVED)
            throw new IllegalStateException("BATCH_TARGET_REVIEW_STATUS_INVALID");
        if (!decision.blockers().isEmpty()) throw new PublicationBlockedException(decision.decision(), decision.reason());
        Set<ContentReleaseIssueCode> required = decision.manualReview().stream().map(ContentReleaseGateService.Issue::code).collect(java.util.stream.Collectors.toSet());
        if (!required.equals(acknowledgedManualIssues)) throw new IllegalStateException("MANUAL_OVERRIDE_INCOMPLETE");
        if (!required.isEmpty() && clean(overrideReason) == null) throw new IllegalArgumentException("MANUAL_OVERRIDE_REASON_REQUIRED");
        ReviewStatus before = item.getReviewStatus();
        if (before == ReviewStatus.PENDING) item.approve(true); else item.publishApproved();
        record(item, reviewer, "BATCH_RELEASE:" + batchId,
                required.isEmpty() ? null : overrideReason, before, ReviewStatus.APPROVED, false, true);
        return result(true, item, "BATCH_RELEASED");
    }

    @Transactional
    public Result rollbackBatchItem(ContentItem item, UserAccount reviewer, Long batchId,
                                    ReviewStatus beforeStatus, boolean beforePublished, String reason) {
        if (item.getReviewStatus() != ReviewStatus.APPROVED || !item.isPublished())
            throw new IllegalStateException("BATCH_ROLLBACK_STATE_CONFLICT");
        if (beforePublished || (beforeStatus != ReviewStatus.PENDING && beforeStatus != ReviewStatus.APPROVED))
            throw new IllegalStateException("BATCH_ROLLBACK_MANIFEST_INVALID");
        if (beforeStatus == ReviewStatus.PENDING) item.reopenReview(); else item.unpublishApproved();
        record(item, reviewer, "BATCH_ROLLBACK:" + batchId, requiredReason(reason),
                ReviewStatus.APPROVED, beforeStatus, true, beforePublished);
        return result(true, item, "BATCH_ROLLED_BACK");
    }

    private Result makeVisible(Long contentId, UserAccount reviewer, String note, String action) {
        ContentItem item = locked(contentId);
        if (item.isPublished()) {
            if (item.getReviewStatus() != ReviewStatus.APPROVED) {
                throw new IllegalStateException("잘못된 공개 상태입니다. 진단 후 별도로 정규화해야 합니다.");
            }
            return result(false, item, "이미 공개 상태입니다.");
        }
        if (item.getReviewStatus() != ReviewStatus.APPROVED) {
            throw new IllegalStateException("APPROVED 콘텐츠만 공개할 수 있습니다.");
        }
        ContentReleaseGateService.Result decision = releaseGate.evaluate(item);
        if (!decision.releasable()) {
            throw new PublicationBlockedException(decision.decision(), decision.reason());
        }
        item.publishApproved();
        record(item, reviewer, action, clean(note), ReviewStatus.APPROVED, ReviewStatus.APPROVED, false, true);
        return result(true, item, "콘텐츠를 공개했습니다.");
    }

    private ContentItem locked(Long id) {
        return contents.findByIdForReview(id)
                .orElseThrow(() -> new NoSuchElementException("Content not found: " + id));
    }

    private void record(ContentItem item, UserAccount reviewer, String action, String reason,
            ReviewStatus previousStatus, ReviewStatus nextStatus,
            boolean previousPublished, boolean nextPublished) {
        String transition = "[" + action + "] published=" + previousPublished + "->" + nextPublished;
        String note = reason == null ? transition : transition + " · " + reason;
        histories.save(new ContentReviewHistory(item, previousStatus, nextStatus, reviewer, limit(note)));
    }

    private static Result result(boolean changed, ContentItem item, String message) {
        return new Result(changed, item.getReviewStatus(), item.isPublished(), message);
    }

    private static String requiredReason(String value) {
        String result = clean(value);
        if (result == null) throw new IllegalArgumentException("변경 사유를 입력해 주세요.");
        return result;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String limit(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public record Result(boolean changed, ReviewStatus status, boolean published, String message) { }
    public record Diagnostics(long invalidPublished, long pendingOrNullPublished, long rejectedPublished) { }

    public static class PublicationBlockedException extends IllegalStateException {
        private final ContentReleaseDecision decision;
        public PublicationBlockedException(ContentReleaseDecision decision, String reason) {
            super("PUBLICATION_" + decision.name() + ": " + reason);
            this.decision = decision;
        }
        public ContentReleaseDecision getDecision() { return decision; }
    }
}
