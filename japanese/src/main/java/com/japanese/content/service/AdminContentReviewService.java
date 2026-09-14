package com.japanese.content.service;

import com.japanese.account.entity.UserAccount;
import com.japanese.content.dto.*;
import com.japanese.content.dto.AdminContentReviewModels.*;
import com.japanese.content.entity.*;
import com.japanese.content.repository.*;
import com.japanese.content.search.ContentSearchNormalizer;
import jakarta.persistence.criteria.JoinType;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminContentReviewService {
    public static final int PAGE_SIZE=25;
    private final ContentItemRepository contents; private final ContentReviewHistoryRepository histories;
    private final ImportedSourceRecordRepository rawRecords; private final ContentSourceRepository sources;
    private final GrammarEnrichmentRepository enrichments; private final GrammarRelationRepository relations;
    private final GrammarComparisonRepository comparisons; private final GrammarConfirmationQuestionRepository confirmations;
    private final CurationReviewHistoryRepository curationHistories;
    private final ContentQualityAuditService qualityAudit;
    public AdminContentReviewService(ContentItemRepository contents,ContentReviewHistoryRepository histories,
      ImportedSourceRecordRepository rawRecords,ContentSourceRepository sources,GrammarEnrichmentRepository enrichments,
      GrammarRelationRepository relations,GrammarComparisonRepository comparisons,
      GrammarConfirmationQuestionRepository confirmations,CurationReviewHistoryRepository curationHistories,
      ContentQualityAuditService qualityAudit){
      this.contents=contents;this.histories=histories;this.rawRecords=rawRecords;this.sources=sources;this.enrichments=enrichments;
      this.relations=relations;this.comparisons=comparisons;this.confirmations=confirmations;this.curationHistories=curationHistories;this.qualityAudit=qualityAudit;}

    @Transactional(readOnly=true)
    public Dashboard dashboard(){
      Instant since=Instant.now().minus(Duration.ofDays(7));
      long pending=contents.countUnpublishedByReviewStatus(ReviewStatus.PENDING,true);
      return new Dashboard(pending,countContents(ReviewStatus.PENDING,false,ContentType.WORD,null,null,null),
        countContents(ReviewStatus.PENDING,false,ContentType.GRAMMAR,null,null,null),
        histories.countByStatusAndReviewedAtGreaterThanEqual(ReviewStatus.APPROVED,since)+curationHistories.countByStatusAndReviewedAtGreaterThanEqual(ReviewStatus.APPROVED,since),
        histories.countByStatusAndReviewedAtGreaterThanEqual(ReviewStatus.REJECTED,since)+curationHistories.countByStatusAndReviewedAtGreaterThanEqual(ReviewStatus.REJECTED,since),
        enrichments.count(spec(ReviewStatus.PENDING,null,null,null))+relations.count(relationSpec(ReviewStatus.PENDING,null,null,null,null))+
          comparisons.count(comparisonSpec(ReviewStatus.PENDING,null,null,null))+confirmations.count(questionSpec(ReviewStatus.PENDING,null,null,null)),
        qualitySummary(ContentType.WORD),qualitySummary(ContentType.GRAMMAR));
    }

    @Transactional(readOnly=true)
    public Page<ContentRow> contents(ReviewStatus status,Boolean published,ContentType type,String level,String keyword,String source,
      Boolean qualityIssue,QualitySeverity qualitySeverity,QualityIssueType qualityIssueType,boolean oldest,int page){
      Pageable pageable=PageRequest.of(Math.max(0,page),PAGE_SIZE,Sort.by(oldest?Sort.Direction.ASC:Sort.Direction.DESC,"id"));
      Specification<ContentItem> spec=contentSpec(status,published,type,clean(level),normalize(keyword),clean(source));
      boolean filterQuality=Boolean.TRUE.equals(qualityIssue)||qualitySeverity!=null||qualityIssueType!=null;
      if(filterQuality)spec=spec.and(qualityAudit.issueFilter(qualitySeverity,qualityIssueType));
      Page<ContentItem> result=contents.findAll(spec,pageable);
      Map<Long,ContentQualityAuditService.Audit> audits=qualityAudit.audits(result.getContent());
      return result.map(item->row(item,audits.get(item.getId())));
    }
    private long countContents(ReviewStatus status,Boolean published,ContentType type,String level,String keyword,String source){return contents.count(contentSpec(status,published,type,clean(level),normalize(keyword),clean(source)));}
    private Specification<ContentItem> contentSpec(ReviewStatus status,Boolean published,ContentType type,String level,String keyword,String source){
      return (root,q,cb)->{var p=cb.conjunction(); if(status==ReviewStatus.PENDING)p=cb.and(p,cb.or(cb.equal(root.get("reviewStatus"),status),cb.isNull(root.get("reviewStatus"))));
        else if(status!=null)p=cb.and(p,cb.equal(root.get("reviewStatus"),status));
        if(published!=null)p=cb.and(p,cb.equal(root.get("published"),published)); if(type!=null)p=cb.and(p,cb.equal(root.get("type"),type));
        if(level!=null){var l=root.join("levels",JoinType.LEFT);p=cb.and(p,cb.equal(l.get("code"),level));q.distinct(true);}
        if(source!=null)p=cb.and(p,cb.like(cb.lower(root.get("sourceRef")),"%"+source.toLowerCase(Locale.ROOT)+"%"));
        if(keyword!=null){var w=root.join("word",JoinType.LEFT);var g=root.join("grammar",JoinType.LEFT);p=cb.and(p,cb.or(cb.like(w.get("expressionSearch"),"%"+keyword+"%"),cb.like(w.get("readingSearch"),"%"+keyword+"%"),cb.like(g.get("patternSearch"),"%"+keyword+"%"),cb.like(g.get("searchText"),"%"+keyword+"%")));}
        return p;};}

    @Transactional(readOnly=true)
    public ContentDetail content(Long id){ContentItem item=contents.findById(id).orElseThrow();return detail(item);}
    @Transactional
    public ActionResult approveContent(Long id,UserAccount reviewer,String note){ContentItem item=contents.findByIdForReview(id).orElseThrow();
      if(item.getReviewStatus()==ReviewStatus.APPROVED)return result(false,item,"이미 승인된 콘텐츠입니다.");
      requirePending(item.getReviewStatus()); validateQualityForApproval(item); validate(item);ReviewStatus before=item.getReviewStatus();item.publish();
      histories.save(new ContentReviewHistory(item,before,ReviewStatus.APPROVED,reviewer,clean(note)));return result(true,item,"승인하고 공개했습니다.");}
    @Transactional
    public ActionResult rejectContent(Long id,UserAccount reviewer,String note){String reason=clean(note);if(reason==null)throw new IllegalArgumentException("반려 사유를 입력해주세요.");
      ContentItem item=contents.findByIdForReview(id).orElseThrow();if(item.getReviewStatus()==ReviewStatus.REJECTED)return result(false,item,"이미 반려된 콘텐츠입니다.");
      requirePending(item.getReviewStatus());ReviewStatus before=item.getReviewStatus();item.reject(reason);
      histories.save(new ContentReviewHistory(item,before,ReviewStatus.REJECTED,reviewer,reason));return result(true,item,"반려했습니다.");}
    @Transactional
    public int approveContents(Collection<Long> ids,UserAccount reviewer){if(ids==null)return 0;int changed=0;
      for(Long id:ids.stream().filter(Objects::nonNull).distinct().limit(20).toList())if(approveContent(id,reviewer,null).changed())changed++;
      return changed;}
    private void validate(ContentItem item){if(item.getType()==ContentType.WORD&&(item.getWord()==null||blank(item.getWord().getExpression())||blank(item.getWord().getReading())))throw new IllegalStateException("단어 표기와 읽기가 필요합니다.");
      if(item.getType()==ContentType.GRAMMAR&&(item.getGrammar()==null||blank(item.getGrammar().getPattern())||blank(item.getGrammar().getExplanation())))throw new IllegalStateException("문법 패턴과 설명이 필요합니다.");}
    private void validateQualityForApproval(ContentItem item){List<QualityIssueType> errors=qualityAudit.audit(item).issues().stream().filter(issue->issue.severity()==QualitySeverity.ERROR).map(ContentQualityAuditService.Issue::type).toList();if(!errors.isEmpty())throw new IllegalStateException("품질 오류가 있어 승인할 수 없습니다: "+String.join(", ",errors.stream().map(Enum::name).toList()));}
    private ActionResult result(boolean changed,ContentItem item,String message){return new ActionResult(changed,item.getReviewStatus(),item.isPublished(),message);}
    private void requirePending(ReviewStatus status){if(status!=ReviewStatus.PENDING)throw new IllegalStateException("PENDING 콘텐츠만 검수할 수 있습니다.");}

    private ContentRow row(ContentItem item){return row(item,qualityAudit.audit(item));}
    private ContentRow row(ContentItem item,ContentQualityAuditService.Audit audit){String title=item.getType()==ContentType.WORD?item.getWord().getExpression():item.getGrammar().getPattern();String reading=item.getWord()==null?null:item.getWord().getReading();
      String summary=item.getWord()==null?item.getGrammar().getExplanation():item.getWord().getMeanings().stream().findFirst().map(Meaning::getText).orElse("");
      return new ContentRow(item.getId(),item.getSlug(),item.getType(),title,reading,summary,jlpt(item),item.getSourceRef(),item.getReviewStatus(),item.isPublished(),audit.issueCount(),audit.highestSeverity());}
    private ContentDetail detail(ContentItem item){ContentReviewDetails old=new ContentReviewDetails(toLegacy(item),source(item.getSourceRef()),item.getWord()==null?null:item.getWord().getPartOfSpeech(),item.getWord()==null?null:item.getWord().getPitchAccent(),
      item.getWord()==null?List.of():item.getWord().getMeanings().stream().map(m->new ContentDetails.MeaningDetails(m.getLanguageTag(),m.getText(),m.getSenseOrder())).toList(),
      item.getExamples().stream().map(e->new ContentDetails.ExampleDetails(e.getMeaning()==null?null:e.getMeaning().getText(),e.getJapaneseText(),e.getReading(),e.getTranslation(),e.getAudioFileName(),e.getDisplayOrder())).toList(),
      item.getGrammar()==null?null:new ContentDetails.GrammarDetails(item.getGrammar().getPattern(),item.getGrammar().getExplanation(),item.getGrammar().getConnection(),null),List.of());
      var audits=histories.findByContentItemIdOrderByReviewedAtDesc(item.getId()).stream().map(h->new Audit(h.getPreviousStatus(),h.getStatus(),h.getReviewer()==null?"system/import":h.getReviewer().getLoginId(),h.getNote(),h.getReviewedAt())).toList();
      var raw=rawRecords.findByContentItemIdOrderById(item.getId()).stream().map(r->new RawSource(r.getSourceRef(),r.getNoteType(),r.getSourceNoteId(),r.getLevelCode(),r.getTags(),r.getFieldNames(),r.getFieldValues())).toList();return new ContentDetail(old,item.isPublished(),raw,audits,toQuality(qualityAudit.audit(item)));}
    private ContentReviewSummary toLegacy(ContentItem i){var r=row(i);return new ContentReviewSummary(r.id(),r.slug(),r.type(),r.title(),r.reading(),r.summary(),r.jlpt(),i.getCategories().stream().map(Category::getName).toList(),r.reviewStatus(),i.getReviewNote(),i.getExamples().size(),r.sourceRef());}
    private SourceDetails source(String ref){if(ref==null)return null;return sources.findBySourceRef(ref).map(s->new SourceDetails(s.getSourceRef(),s.getDisplayName(),s.getVersion(),s.getLicenseSummary(),s.getLicenseUrl(),s.getAttribution(),s.getUsageNote())).orElse(null);}
    private QualityAudit toQuality(ContentQualityAuditService.Audit audit){return new QualityAudit(audit.issueCount(),audit.highestSeverity(),audit.issues().stream().map(i->new QualityIssue(i.type(),i.severity(),i.message())).toList());}
    private QualitySummary qualitySummary(ContentType type){Specification<ContentItem> base=n5Spec(type);long total=contents.count(base);long error=contents.count(base.and(qualityAudit.issueFilter(QualitySeverity.ERROR,null)));Specification<ContentItem> noError=base.and(not(qualityAudit.issueFilter(QualitySeverity.ERROR,null)));long warning=contents.count(noError.and(qualityAudit.issueFilter(QualitySeverity.WARNING,null)));Specification<ContentItem> noWarning=noError.and(not(qualityAudit.issueFilter(QualitySeverity.WARNING,null)));long info=contents.count(noWarning.and(qualityAudit.issueFilter(QualitySeverity.INFO,null)));return new QualitySummary(total,total-error-warning-info,info,warning,error);}
    private Specification<ContentItem> n5Spec(ContentType type){return (root,q,cb)->{q.distinct(true);return cb.and(cb.equal(root.get("type"),type),cb.equal(root.join("levels").get("code"),"N5"));};}
    private static Specification<ContentItem> not(Specification<ContentItem> specification){return (root,q,cb)->cb.not(specification.toPredicate(root,q,cb));}

    @Transactional(readOnly=true)
    public Page<CurationRow> curation(CurationRecordType type,ReviewStatus status,Boolean published,String level,String keyword,GrammarRelationType relationType,int page){
      Pageable p=PageRequest.of(Math.max(0,page),PAGE_SIZE,Sort.by(Sort.Direction.DESC,"id"));String l=clean(level),k=normalize(keyword);
      return switch(type){case ENRICHMENT->enrichments.findAll(spec(status,published,l,k),p).map(this::row);case RELATION->relations.findAll(relationSpec(status,published,l,k,relationType),p).map(this::row);case COMPARISON->comparisons.findAll(comparisonSpec(status,published,l,k),p).map(this::row);case CONFIRMATION->confirmations.findAll(questionSpec(status,published,l,k),p).map(this::row);};}
    private Specification<GrammarEnrichment> spec(ReviewStatus s,Boolean pub,String level,String key){return (r,q,c)->curated(c,r,s,pub,level,key,"grammar");}
    private Specification<GrammarConfirmationQuestion> questionSpec(ReviewStatus s,Boolean pub,String level,String key){return (r,q,c)->curated(c,r,s,pub,level,key,"grammar");}
    private <T> jakarta.persistence.criteria.Predicate curated(jakarta.persistence.criteria.CriteriaBuilder c,jakarta.persistence.criteria.Root<T> r,ReviewStatus s,Boolean pub,String level,String key,String grammarField){var p=c.conjunction();if(s!=null)p=c.and(p,c.equal(r.get("reviewStatus"),s));if(pub!=null)p=c.and(p,c.equal(r.get("published"),pub));var g=r.join(grammarField);if(level!=null)p=c.and(p,c.equal(g.join("contentItem").join("levels").get("code"),level));if(key!=null)p=c.and(p,c.like(g.get("patternSearch"),"%"+key+"%"));return p;}
    private Specification<GrammarRelation> relationSpec(ReviewStatus s,Boolean pub,String level,String key,GrammarRelationType rt){return (r,q,c)->{var p=c.conjunction();if(s!=null)p=c.and(p,c.equal(r.get("reviewStatus"),s));if(pub!=null)p=c.and(p,c.equal(r.get("published"),pub));if(rt!=null)p=c.and(p,c.equal(r.get("relationType"),rt));var a=r.join("leftGrammar");var b=r.join("rightGrammar");if(key!=null)p=c.and(p,c.or(c.like(a.get("patternSearch"),"%"+key+"%"),c.like(b.get("patternSearch"),"%"+key+"%")));if(level!=null)p=c.and(p,c.or(c.equal(a.join("contentItem").join("levels").get("code"),level),c.equal(b.join("contentItem").join("levels").get("code"),level)));q.distinct(true);return p;};}
    private Specification<GrammarComparison> comparisonSpec(ReviewStatus s,Boolean pub,String level,String key){return (r,q,c)->{var rel=r.join("relation");var a=rel.join("leftGrammar");var b=rel.join("rightGrammar");var p=c.conjunction();if(s!=null)p=c.and(p,c.equal(r.get("reviewStatus"),s));if(pub!=null)p=c.and(p,c.equal(r.get("published"),pub));if(key!=null)p=c.and(p,c.or(c.like(a.get("patternSearch"),"%"+key+"%"),c.like(b.get("patternSearch"),"%"+key+"%")));if(level!=null)p=c.and(p,c.or(c.equal(a.join("contentItem").join("levels").get("code"),level),c.equal(b.join("contentItem").join("levels").get("code"),level)));q.distinct(true);return p;};}
    private CurationRow row(GrammarEnrichment e){return crow(CurationRecordType.ENRICHMENT,e.getId(),e.getGrammar(),null,null,e.getNuance(),e.getSourceRef(),e.getReviewStatus(),e.isPublished());}
    private CurationRow row(GrammarRelation r){return crow(CurationRecordType.RELATION,r.getId(),r.getLeftGrammar(),r.getRightGrammar(),r.getRelationType(),r.getRelationType().name(),r.getSourceRef(),r.getReviewStatus(),r.isPublished());}
    private CurationRow row(GrammarComparison c){return crow(CurationRecordType.COMPARISON,c.getId(),c.getRelation().getLeftGrammar(),c.getRelation().getRightGrammar(),c.getRelation().getRelationType(),c.getSummary(),c.getSourceRef(),c.getReviewStatus(),c.isPublished());}
    private CurationRow row(GrammarConfirmationQuestion q){return crow(CurationRecordType.CONFIRMATION,q.getId(),q.getGrammar(),null,null,q.getPrompt(),q.getSourceRef(),q.getReviewStatus(),q.isPublished());}
    private CurationRow crow(CurationRecordType t,Long id,Grammar a,Grammar b,GrammarRelationType rt,String summary,String source,ReviewStatus s,boolean pub){return new CurationRow(t,id,a.getPattern(),b==null?null:b.getPattern(),rt,summary,jlpt(a.getContentItem()),source,s,pub);}

    @Transactional(readOnly=true) public CurationDetail curation(CurationRecordType type,Long id){return switch(type){case ENRICHMENT->detail(enrichments.findById(id).orElseThrow());case RELATION->detail(relations.findById(id).orElseThrow());case COMPARISON->detail(comparisons.findById(id).orElseThrow());case CONFIRMATION->detail(confirmations.findById(id).orElseThrow());};}
    private CurationDetail detail(GrammarEnrichment e){return cd(row(e),e.getNuance(),e.getUsageNote(),e.getFormationSupplement(),e.getCommonMistake(),e.getLearnerNote(),null,null,null,null,null,null,null,List.of());}
    private CurationDetail detail(GrammarRelation r){return cd(row(r),null,null,null,null,null,null,null,null,null,null,null,null,List.of());}
    private CurationDetail detail(GrammarComparison c){return cd(row(c),null,null,null,null,null,c.getKeyDifference(),c.getUsageDifference(),c.getCommonConfusion(),null,null,null,null,List.of());}
    private CurationDetail detail(GrammarConfirmationQuestion q){return cd(row(q),null,null,null,null,null,null,null,null,q.getQuestionType().name(),q.getPrompt(),q.getContext(),q.getExplanation(),q.getChoices().stream().map(c->new Choice(c.getId(),c.getChoiceText(),c.isCorrect(),c.getDisplayOrder())).toList());}
    private CurationDetail cd(CurationRow row,String nuance,String usage,String formation,String mistake,String learner,String key,String usageDiff,String confusion,String qt,String prompt,String context,String explanation,List<Choice> choices){var audits=curationHistories.findByRecordTypeAndRecordIdOrderByReviewedAtDesc(row.type(),row.id()).stream().map(h->new Audit(h.getPreviousStatus(),h.getStatus(),h.getReviewer().getLoginId(),h.getNote(),h.getReviewedAt())).toList();return new CurationDetail(row,nuance,usage,formation,mistake,learner,key,usageDiff,confusion,qt,prompt,context,explanation,choices,audits);}

    @Transactional public ActionResult reviewCuration(CurationRecordType type,Long id,ReviewStatus target,UserAccount reviewer,String note){if(target!=ReviewStatus.APPROVED&&target!=ReviewStatus.REJECTED)throw new IllegalArgumentException("승인 또는 반려만 가능합니다.");String n=clean(note);if(target==ReviewStatus.REJECTED&&n==null)throw new IllegalArgumentException("반려 사유를 입력해주세요.");return switch(type){case ENRICHMENT->review(type,enrichments.findByIdForReview(id).orElseThrow(),target,reviewer,n);case RELATION->review(type,relations.findByIdForReview(id).orElseThrow(),target,reviewer,n);case COMPARISON->review(type,comparisons.findByIdForReview(id).orElseThrow(),target,reviewer,n);case CONFIRMATION->review(type,confirmations.findByIdForReview(id).orElseThrow(),target,reviewer,n);};}
    private ActionResult review(CurationRecordType t,Object o,ReviewStatus target,UserAccount reviewer,String note){ReviewStatus before=status(o);if(before==target)return new ActionResult(false,target,published(o),"이미 처리된 항목입니다.");requirePending(before);if(target==ReviewStatus.APPROVED)approve(o);else reject(o);curationHistories.save(new CurationReviewHistory(t,id(o),before,target,reviewer,note));return new ActionResult(true,target,published(o),target==ReviewStatus.APPROVED?"승인하고 공개했습니다.":"반려했습니다.");}
    private ReviewStatus status(Object o){if(o instanceof GrammarEnrichment x)return x.getReviewStatus();if(o instanceof GrammarRelation x)return x.getReviewStatus();if(o instanceof GrammarComparison x)return x.getReviewStatus();return ((GrammarConfirmationQuestion)o).getReviewStatus();}
    private boolean published(Object o){if(o instanceof GrammarEnrichment x)return x.isPublished();if(o instanceof GrammarRelation x)return x.isPublished();if(o instanceof GrammarComparison x)return x.isPublished();return ((GrammarConfirmationQuestion)o).isPublished();}
    private Long id(Object o){if(o instanceof GrammarEnrichment x)return x.getId();if(o instanceof GrammarRelation x)return x.getId();if(o instanceof GrammarComparison x)return x.getId();return ((GrammarConfirmationQuestion)o).getId();}
    private void approve(Object o){if(o instanceof GrammarEnrichment x)x.approveForPublication();else if(o instanceof GrammarRelation x)x.approveForPublication();else if(o instanceof GrammarComparison x)x.approveForPublication();else ((GrammarConfirmationQuestion)o).approveForPublication();}
    private void reject(Object o){if(o instanceof GrammarEnrichment x)x.reject();else if(o instanceof GrammarRelation x)x.reject();else if(o instanceof GrammarComparison x)x.reject();else ((GrammarConfirmationQuestion)o).reject();}
    private String jlpt(ContentItem i){return i.getLevels().stream().filter(l->"JLPT".equals(l.getSystem())).map(Level::getCode).sorted().findFirst().orElse("");}
    private static String normalize(String s){String c=clean(s);return c==null?null:ContentSearchNormalizer.normalize(c);}private static String clean(String s){return s==null||s.isBlank()?null:s.trim();}private static boolean blank(String s){return s==null||s.isBlank();}
}
