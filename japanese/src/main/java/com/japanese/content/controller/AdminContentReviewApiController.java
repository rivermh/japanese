package com.japanese.content.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.content.entity.*;
import com.japanese.content.service.AdminContentReviewService;
import com.japanese.content.service.ContentReleaseDryRunService;
import com.japanese.content.dto.AdminContentReleaseDryRunModels;
import com.japanese.content.dto.AdminContentReleaseBatchModels;
import com.japanese.content.service.ContentReleaseBatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminContentReviewApiController {
    private final AdminContentReviewService reviews;private final CurrentUserService current; private final ContentReleaseDryRunService dryRun; private final ContentReleaseBatchService batches;
    public AdminContentReviewApiController(AdminContentReviewService reviews,CurrentUserService current,ContentReleaseDryRunService dryRun,ContentReleaseBatchService batches){this.reviews=reviews;this.current=current;this.dryRun=dryRun;this.batches=batches;}
    public record ReviewRequest(@Size(max=1000) String note){}
    @GetMapping("/dashboard") public Object dashboard(){return reviews.dashboard();}
    @GetMapping("/contents") public Object contents(@RequestParam(required=false) ReviewStatus status,@RequestParam(required=false) Boolean published,@RequestParam(required=false) ContentType type,@RequestParam(required=false) String level,@RequestParam(required=false) String keyword,@RequestParam(required=false) String source,@RequestParam(required=false) Boolean qualityIssue,@RequestParam(required=false) QualitySeverity qualitySeverity,@RequestParam(required=false) QualityIssueType qualityIssueType,@RequestParam(defaultValue="false") boolean oldest,@RequestParam(defaultValue="0") int page){return reviews.contents(status,published,type,level,keyword,source,qualityIssue,qualitySeverity,qualityIssueType,oldest,page);}
    @GetMapping("/contents/{id}") public Object content(@PathVariable Long id){return reviews.content(id);}
    @PostMapping("/contents/{id}/approve") public Object approve(@PathVariable Long id,@Valid @RequestBody(required=false) ReviewRequest r){return reviews.approveContent(id,current.currentAccount(),r==null?null:r.note());}
    @PostMapping("/contents/{id}/reject") public Object reject(@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return reviews.rejectContent(id,current.currentAccount(),r.note());}
    @PostMapping("/contents/{id}/publish") public Object publish(@PathVariable Long id,@Valid @RequestBody(required=false) ReviewRequest r){return reviews.publishContent(id,current.currentAccount(),r==null?null:r.note());}
    @PostMapping("/contents/{id}/unpublish") public Object unpublish(@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return reviews.unpublishContent(id,current.currentAccount(),r.note());}
    @PostMapping("/contents/{id}/republish") public Object republish(@PathVariable Long id,@Valid @RequestBody(required=false) ReviewRequest r){return reviews.republishContent(id,current.currentAccount(),r==null?null:r.note());}
    @PostMapping("/contents/{id}/reopen") public Object reopen(@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return reviews.reopenContent(id,current.currentAccount(),r.note());}
    @GetMapping("/publication-diagnostics") public Object publicationDiagnostics(){return reviews.publicationDiagnostics();}
    @PostMapping("/content-release/dry-run") public AdminContentReleaseDryRunModels.Result dryRun(@RequestBody(required=false) AdminContentReleaseDryRunModels.Request request){return dryRun.run(request==null?null:request.filter());}
    @PostMapping("/content-release/batches") public Object executeBatch(@RequestBody AdminContentReleaseBatchModels.ExecuteRequest request){return batches.execute(request,current.currentAccount());}
    @GetMapping("/content-release/batches/{id}") public Object batch(@PathVariable Long id){return batches.batch(id);}
    @PostMapping("/content-release/batches/{id}/rollback") public Object rollbackBatch(@PathVariable Long id,@RequestBody AdminContentReleaseBatchModels.RollbackRequest request){return batches.rollback(id,request.reason(),current.currentAccount());}
    @GetMapping("/grammar-curation") public Object curation(@RequestParam(defaultValue="ENRICHMENT") CurationRecordType recordType,@RequestParam(required=false) ReviewStatus status,@RequestParam(required=false) Boolean published,@RequestParam(required=false) String level,@RequestParam(required=false) String keyword,@RequestParam(required=false) GrammarRelationType relationType,@RequestParam(defaultValue="0") int page){return reviews.curation(recordType,status,published,level,keyword,relationType,page);}
    @GetMapping("/grammar-curation/{type}/{id}") public Object curation(@PathVariable CurationRecordType type,@PathVariable Long id){return reviews.curation(type,id);}
    @PostMapping("/grammar-curation/{type}/{id}/approve") public Object approve(@PathVariable CurationRecordType type,@PathVariable Long id,@Valid @RequestBody(required=false) ReviewRequest r){return reviews.reviewCuration(type,id,ReviewStatus.APPROVED,current.currentAccount(),r==null?null:r.note());}
    @PostMapping("/grammar-curation/{type}/{id}/reject") public Object reject(@PathVariable CurationRecordType type,@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return reviews.reviewCuration(type,id,ReviewStatus.REJECTED,current.currentAccount(),r.note());}
}
