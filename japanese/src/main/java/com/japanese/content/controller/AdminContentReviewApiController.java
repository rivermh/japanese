package com.japanese.content.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.content.entity.*;
import com.japanese.content.service.AdminContentReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminContentReviewApiController {
    private final AdminContentReviewService reviews;private final CurrentUserService current;
    public AdminContentReviewApiController(AdminContentReviewService reviews,CurrentUserService current){this.reviews=reviews;this.current=current;}
    public record ReviewRequest(@Size(max=1000) String note){}
    @GetMapping("/dashboard") public Object dashboard(){return reviews.dashboard();}
    @GetMapping("/contents") public Object contents(@RequestParam(required=false) ReviewStatus status,@RequestParam(required=false) Boolean published,@RequestParam(required=false) ContentType type,@RequestParam(required=false) String level,@RequestParam(required=false) String keyword,@RequestParam(required=false) String source,@RequestParam(required=false) Boolean qualityIssue,@RequestParam(required=false) QualitySeverity qualitySeverity,@RequestParam(required=false) QualityIssueType qualityIssueType,@RequestParam(defaultValue="false") boolean oldest,@RequestParam(defaultValue="0") int page){return reviews.contents(status,published,type,level,keyword,source,qualityIssue,qualitySeverity,qualityIssueType,oldest,page);}
    @GetMapping("/contents/{id}") public Object content(@PathVariable Long id){return reviews.content(id);}
    @PostMapping("/contents/{id}/approve") public Object approve(@PathVariable Long id,@Valid @RequestBody(required=false) ReviewRequest r){return reviews.approveContent(id,current.currentAccount(),r==null?null:r.note());}
    @PostMapping("/contents/{id}/reject") public Object reject(@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return reviews.rejectContent(id,current.currentAccount(),r.note());}
    @GetMapping("/grammar-curation") public Object curation(@RequestParam(defaultValue="ENRICHMENT") CurationRecordType recordType,@RequestParam(required=false) ReviewStatus status,@RequestParam(required=false) Boolean published,@RequestParam(required=false) String level,@RequestParam(required=false) String keyword,@RequestParam(required=false) GrammarRelationType relationType,@RequestParam(defaultValue="0") int page){return reviews.curation(recordType,status,published,level,keyword,relationType,page);}
    @GetMapping("/grammar-curation/{type}/{id}") public Object curation(@PathVariable CurationRecordType type,@PathVariable Long id){return reviews.curation(type,id);}
    @PostMapping("/grammar-curation/{type}/{id}/approve") public Object approve(@PathVariable CurationRecordType type,@PathVariable Long id,@Valid @RequestBody(required=false) ReviewRequest r){return reviews.reviewCuration(type,id,ReviewStatus.APPROVED,current.currentAccount(),r==null?null:r.note());}
    @PostMapping("/grammar-curation/{type}/{id}/reject") public Object reject(@PathVariable CurationRecordType type,@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return reviews.reviewCuration(type,id,ReviewStatus.REJECTED,current.currentAccount(),r.note());}
}
