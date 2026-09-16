package com.japanese.content.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.dto.AdminContentReleaseBatchModels.*;
import com.japanese.content.entity.*;
import com.japanese.content.repository.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentReleaseBatchService {
    private static final int MAX_TARGETS = 20_000;
    private final ContentItemRepository contents;
    private final ContentSourceRepository sources;
    private final ContentReleaseBatchRepository batches;
    private final ContentReleaseBatchItemRepository batchItems;
    private final ContentReleaseDryRunService dryRun;
    private final ContentPublicationService publication;

    public ContentReleaseBatchService(ContentItemRepository contents, ContentSourceRepository sources,
            ContentReleaseBatchRepository batches, ContentReleaseBatchItemRepository batchItems,
            ContentReleaseDryRunService dryRun, ContentPublicationService publication) {
        this.contents=contents; this.sources=sources; this.batches=batches; this.batchItems=batchItems;
        this.dryRun=dryRun; this.publication=publication;
    }

    @Transactional
    public BatchView execute(ExecuteRequest request, UserAccount reviewer) {
        ValidatedRequest validated = validateRequest(request);
        Optional<ContentReleaseBatch> prior = batches.findByGateVersionAndPreviewDigest(request.gateVersion(), request.digest());
        if (prior.isPresent()) return idempotent(prior.get(), validated.targetIds());

        List<ContentItem> items = contents.findAllByIdInForReleaseBatch(validated.targetIds());
        requireIds(items, validated.targetIds());
        Set<String> refs = items.stream().map(ContentItem::getSourceRef).filter(Objects::nonNull)
                .filter(ref -> !ref.isBlank()).collect(Collectors.toCollection(TreeSet::new));
        if (!refs.isEmpty()) sources.findBySourceRefInForReleaseBatch(refs);
        prior = batches.findByGateVersionAndPreviewDigest(request.gateVersion(), request.digest());
        if (prior.isPresent()) return idempotent(prior.get(), validated.targetIds());

        ContentReleaseDryRunService.ExecutionSnapshot snapshot = dryRun.executionSnapshot(items);
        if (!snapshot.targetIds().equals(validated.targetIds()) || !snapshot.digest().equals(request.digest()))
            throw new ContentReleaseBatchException("STALE_PREVIEW");

        Map<Long, ManualOverride> overrides = overrideMap(request.manualOverrides());
        int releasableCount=0, manualCount=0;
        for (ContentItem item : items) {
            if (item.isPublished()) throw new ContentReleaseBatchException("BATCH_TARGET_ALREADY_PUBLISHED");
            if (item.getReviewStatus()!=ReviewStatus.PENDING && item.getReviewStatus()!=ReviewStatus.APPROVED)
                throw new ContentReleaseBatchException("BATCH_TARGET_REVIEW_STATUS_INVALID");
            ContentReleaseGateService.Result decision=snapshot.decisions().get(item.getId());
            if (!decision.blockers().isEmpty()) throw new ContentReleaseBatchException("PUBLICATION_BLOCKED");
            if (decision.decision()==ContentReleaseDecision.MANUAL_REVIEW_REQUIRED) {
                validateOverride(item.getId(), decision, overrides.get(item.getId()), validated.mode()); manualCount++;
            } else { if(overrides.containsKey(item.getId()))throw new ContentReleaseBatchException("UNEXPECTED_MANUAL_OVERRIDE"); releasableCount++; }
        }
        if (!overrides.keySet().stream().allMatch(validated.targetIds()::contains))
            throw new ContentReleaseBatchException("MANUAL_OVERRIDE_TARGET_INVALID");

        ContentReleaseBatch batch = batches.saveAndFlush(new ContentReleaseBatch(reviewer, validated.mode(),
                request.gateVersion(), request.digest(), validated.note(), items.size(), releasableCount, manualCount, 0));
        for (int position=0; position<items.size(); position++) {
            ContentItem item=items.get(position); ReviewStatus before=item.getReviewStatus(); boolean beforePublished=item.isPublished();
            ContentReleaseGateService.Result decision=snapshot.decisions().get(item.getId());
            ManualOverride override=overrides.get(item.getId()); Set<ContentReleaseIssueCode> acknowledged=override==null?Set.of():Set.copyOf(override.acknowledgedIssueCodes());
            publication.publishForBatch(item, reviewer, batch.getId(), decision, acknowledged, override==null?null:override.reason());
            ContentReleaseBatchItem manifest=new ContentReleaseBatchItem(item,position,before,beforePublished,
                    item.getReviewStatus(),item.isPublished(),decision.decision(),codes(allIssues(decision)),
                    codes(acknowledged),override==null?null:clean(override.reason()),item.getSourceRef(),
                    decision.sourceRights().rightsStatus()==null?null:decision.sourceRights().rightsStatus().name());
            batch.addItem(manifest); batchItems.save(manifest);
        }
        return view(batch, batchItems.findByBatchIdOrderByPosition(batch.getId()));
    }

    @Transactional(readOnly=true)
    public org.springframework.data.domain.Page<BatchSummary> history(int page) {
        return batches.findAllByOrderByIdDesc(org.springframework.data.domain.PageRequest.of(Math.max(0, page), 20))
                .map(b -> new BatchSummary(b.getId(), b.getStatus(), b.getMode(), b.getExecutedAt(),
                        b.getReviewer().getLoginId(), b.getTargetCount(), b.getPreviewDigest(), b.getRolledBackAt()));
    }

    @Transactional(readOnly=true)
    public BatchView batch(Long id) {
        ContentReleaseBatch batch=batches.findById(id).orElseThrow();
        return view(batch,batchItems.findByBatchIdOrderByPosition(id));
    }

    @Transactional
    public BatchView rollback(Long id, String reason, UserAccount reviewer) {
        String normalized=required(reason,"ROLLBACK_REASON_REQUIRED");
        ContentReleaseBatch batch=batches.findByIdForUpdate(id).orElseThrow();
        if(batch.getStatus()!=ContentReleaseBatchStatus.EXECUTED)throw new ContentReleaseBatchException("BATCH_ALREADY_ROLLED_BACK");
        List<ContentReleaseBatchItem> manifests=batchItems.findByBatchIdOrderByPosition(id);
        List<Long> ids=manifests.stream().map(i->i.getContentItem().getId()).sorted().toList();
        List<ContentItem> items=contents.findAllByIdInForReleaseBatch(ids); requireIds(items,ids);
        Map<Long,ContentItem> byId=items.stream().collect(Collectors.toMap(ContentItem::getId,i->i));
        for(ContentReleaseBatchItem manifest:manifests){ContentItem item=byId.get(manifest.getContentItem().getId());
            if(item.getReviewStatus()!=manifest.getResultingReviewStatus()||item.isPublished()!=manifest.isResultingPublished())
                throw new ContentReleaseBatchException("BATCH_ROLLBACK_STATE_CONFLICT");}
        for(ContentReleaseBatchItem manifest:manifests){ContentItem item=byId.get(manifest.getContentItem().getId());
            publication.rollbackBatchItem(item,reviewer,batch.getId(),manifest.getPreviousReviewStatus(),manifest.isPreviousPublished(),normalized);}
        batch.markRolledBack(normalized);
        return view(batch,manifests);
    }

    private ValidatedRequest validateRequest(ExecuteRequest request){
        if(request==null)throw new IllegalArgumentException("BATCH_REQUEST_REQUIRED");
        if(!ContentReleaseDryRunService.GATE_VERSION.equals(request.gateVersion()))throw new ContentReleaseBatchException("GATE_VERSION_MISMATCH");
        String digest=required(request.digest(),"PREVIEW_DIGEST_REQUIRED"); if(digest.length()!=64)throw new IllegalArgumentException("PREVIEW_DIGEST_INVALID");
        String note=required(request.note(),"EXECUTION_NOTE_REQUIRED"); ContentReleaseMode mode=request.mode()==null?ContentReleaseMode.RELEASABLE_ONLY:request.mode();
        List<Long> ids=request.targetIds()==null?List.of():List.copyOf(request.targetIds());
        if(ids.isEmpty()||ids.size()>MAX_TARGETS||ids.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("TARGET_IDS_INVALID");
        List<Long> canonical=ids.stream().distinct().sorted().toList(); if(!canonical.equals(ids))throw new ContentReleaseBatchException("TARGET_ORDER_INVALID");
        return new ValidatedRequest(canonical,note,mode);
    }
    private void validateOverride(Long id,ContentReleaseGateService.Result decision,ManualOverride override,ContentReleaseMode mode){
        if(mode!=ContentReleaseMode.ALLOW_EXPLICIT_MANUAL_OVERRIDES||override==null)throw new ContentReleaseBatchException("MANUAL_OVERRIDE_REQUIRED");
        String reason=required(override.reason(),"MANUAL_OVERRIDE_REASON_REQUIRED");
        Set<ContentReleaseIssueCode> required=decision.manualReview().stream().map(ContentReleaseGateService.Issue::code).collect(Collectors.toSet());
        Set<ContentReleaseIssueCode> supplied=override.acknowledgedIssueCodes()==null?Set.of():Set.copyOf(override.acknowledgedIssueCodes());
        if(!required.equals(supplied))throw new ContentReleaseBatchException("MANUAL_OVERRIDE_INCOMPLETE");
        if(reason.length()>1000)throw new IllegalArgumentException("MANUAL_OVERRIDE_REASON_TOO_LONG");
    }
    private Map<Long,ManualOverride> overrideMap(List<ManualOverride> input){Map<Long,ManualOverride> map=new HashMap<>();if(input==null)return map;
        for(ManualOverride value:input)if(value==null||value.contentItemId()==null||map.put(value.contentItemId(),value)!=null)throw new IllegalArgumentException("MANUAL_OVERRIDE_INVALID");return map;}
    private BatchView idempotent(ContentReleaseBatch prior,List<Long> ids){List<ContentReleaseBatchItem> manifests=batchItems.findByBatchIdOrderByPosition(prior.getId());
        if(!manifests.stream().map(i->i.getContentItem().getId()).toList().equals(ids))throw new ContentReleaseBatchException("PREVIEW_DIGEST_COLLISION");return view(prior,manifests);}
    private void requireIds(List<ContentItem> items,List<Long> expected){if(!items.stream().map(ContentItem::getId).toList().equals(expected))throw new ContentReleaseBatchException("TARGET_SET_CHANGED");}
    private List<ContentReleaseIssueCode> allIssues(ContentReleaseGateService.Result r){List<ContentReleaseIssueCode> out=new ArrayList<>();r.blockers().forEach(i->out.add(i.code()));r.manualReview().forEach(i->out.add(i.code()));r.informational().forEach(i->out.add(i.code()));return out;}
    private String codes(Collection<?> values){return values.stream().map(Object::toString).sorted().collect(Collectors.joining(","));}
    private BatchView view(ContentReleaseBatch b,List<ContentReleaseBatchItem> items){return new BatchView(b.getId(),b.getStatus(),b.getMode(),b.getGateVersion(),b.getPreviewDigest(),b.getExecutionNote(),b.getTargetCount(),b.getSuccessCount(),b.getReleasableCount(),b.getManualOverrideCount(),b.getBlockedCount(),b.getReviewer().getLoginId(),b.getCreatedAt(),b.getExecutedAt(),b.getRolledBackAt(),b.getRollbackReason(),items.stream().map(this::view).toList());}
    private ItemView view(ContentReleaseBatchItem i){return new ItemView(i.getContentItem().getId(),i.getPosition(),i.getPreviousReviewStatus(),i.isPreviousPublished(),i.getResultingReviewStatus(),i.isResultingPublished(),i.getReleaseDecision(),split(i.getIssueCodes()),split(i.getManualOverrideIssueCodes()),i.getOverrideReason(),i.getSourceRefSnapshot(),i.getSourceRightsStatusSnapshot(),i.isSuccess());}
    private List<String> split(String v){return v==null||v.isBlank()?List.of():List.of(v.split(","));}
    private static String required(String value,String code){String normalized=clean(value);if(normalized==null)throw new IllegalArgumentException(code);if(normalized.length()>1000)throw new IllegalArgumentException(code+"_TOO_LONG");return normalized;}
    private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private record ValidatedRequest(List<Long> targetIds,String note,ContentReleaseMode mode){}
}
