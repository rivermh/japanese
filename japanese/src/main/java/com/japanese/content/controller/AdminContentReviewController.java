package com.japanese.content.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.content.entity.*;
import com.japanese.content.service.AdminContentReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminContentReviewController {
    private final AdminContentReviewService reviews; private final CurrentUserService current;
    public AdminContentReviewController(AdminContentReviewService reviews,CurrentUserService current){this.reviews=reviews;this.current=current;}
    @GetMapping public String dashboard(Model model){model.addAttribute("dashboard",reviews.dashboard());return "admin/dashboard";}
    @GetMapping("/contents") public String contents(@RequestParam(required=false) ReviewStatus status,@RequestParam(required=false) Boolean published,
      @RequestParam(required=false) ContentType type,@RequestParam(required=false) String level,@RequestParam(required=false) String keyword,
      @RequestParam(required=false) String source,@RequestParam(required=false) Boolean qualityIssue,@RequestParam(required=false) QualitySeverity qualitySeverity,@RequestParam(required=false) QualityIssueType qualityIssueType,@RequestParam(defaultValue="false") boolean oldest,@RequestParam(defaultValue="0") int page,Model model){
      Boolean effectiveQualityIssue = Boolean.TRUE.equals(qualityIssue) || qualitySeverity != null || qualityIssueType != null ? Boolean.TRUE : qualityIssue;
      model.addAttribute("result",reviews.contents(status,published,type,level,keyword,source,effectiveQualityIssue,qualitySeverity,qualityIssueType,oldest,page));filters(model,status,published,type,level,keyword,source,effectiveQualityIssue,qualitySeverity,qualityIssueType,oldest);return "admin/content-list";}
    @GetMapping("/contents/{id}") public String content(@PathVariable Long id,Model model){model.addAttribute("content",reviews.content(id));return "admin/content-detail";}
    @PostMapping("/contents/{id}/approve") public String approve(@PathVariable Long id,@RequestParam(required=false) String note,RedirectAttributes flash){return contentAction(id,flash,()->reviews.approveContent(id,current.currentAccount(),note));}
    @PostMapping("/contents/{id}/reject") public String reject(@PathVariable Long id,@RequestParam String note,RedirectAttributes flash){return contentAction(id,flash,()->reviews.rejectContent(id,current.currentAccount(),note));}
    @GetMapping("/grammars/curation") public String curation(@RequestParam(defaultValue="ENRICHMENT") CurationRecordType recordType,
      @RequestParam(required=false) ReviewStatus status,@RequestParam(required=false) Boolean published,@RequestParam(required=false) String level,
      @RequestParam(required=false) String keyword,@RequestParam(required=false) GrammarRelationType relationType,@RequestParam(defaultValue="0") int page,Model model){
      model.addAttribute("result",reviews.curation(recordType,status,published,level,keyword,relationType,page));model.addAttribute("recordType",recordType);model.addAttribute("status",status);model.addAttribute("published",published);model.addAttribute("level",level);model.addAttribute("keyword",keyword);model.addAttribute("relationType",relationType);return "admin/curation-list";}
    @GetMapping("/grammars/curation/{type}/{id}") public String curationDetail(@PathVariable CurationRecordType type,@PathVariable Long id,Model model){model.addAttribute("record",reviews.curation(type,id));return "admin/curation-detail";}
    @PostMapping("/grammars/curation/{type}/{id}/approve") public String approveCuration(@PathVariable CurationRecordType type,@PathVariable Long id,@RequestParam(required=false) String note,RedirectAttributes flash){return curationAction(type,id,flash,()->reviews.reviewCuration(type,id,ReviewStatus.APPROVED,current.currentAccount(),note));}
    @PostMapping("/grammars/curation/{type}/{id}/reject") public String rejectCuration(@PathVariable CurationRecordType type,@PathVariable Long id,@RequestParam String note,RedirectAttributes flash){return curationAction(type,id,flash,()->reviews.reviewCuration(type,id,ReviewStatus.REJECTED,current.currentAccount(),note));}
    private String contentAction(Long id,RedirectAttributes f,Action action){try{f.addFlashAttribute("adminMessage",action.run().message());}catch(IllegalArgumentException|IllegalStateException e){f.addFlashAttribute("adminError",e.getMessage());}return "redirect:/admin/contents/"+id;}
    private String curationAction(CurationRecordType t,Long id,RedirectAttributes f,Action action){try{f.addFlashAttribute("adminMessage",action.run().message());}catch(IllegalArgumentException|IllegalStateException e){f.addFlashAttribute("adminError",e.getMessage());}return "redirect:/admin/grammars/curation/"+t+"/"+id;}
    private void filters(Model m,Object... values){String[] names={"status","published","type","level","keyword","source","qualityIssue","qualitySeverity","qualityIssueType","oldest"};for(int i=0;i<names.length;i++)m.addAttribute(names[i],values[i]);}
    private interface Action{com.japanese.content.dto.AdminContentReviewModels.ActionResult run();}
}
