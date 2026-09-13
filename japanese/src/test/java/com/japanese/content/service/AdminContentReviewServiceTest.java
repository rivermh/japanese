package com.japanese.content.service;

import static org.assertj.core.api.Assertions.*;
import com.japanese.account.entity.*;
import com.japanese.account.repository.UserAccountRepository;
import com.japanese.config.SampleContentDataLoader;
import com.japanese.content.entity.*;
import com.japanese.content.dto.AdminContentReviewModels;
import com.japanese.content.repository.*;
import com.japanese.learning.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("sample") @Transactional
class AdminContentReviewServiceTest {
 @Autowired AdminContentReviewService reviews; @Autowired ContentItemRepository contents;
 @Autowired ContentReviewHistoryRepository histories; @Autowired UserAccountRepository accounts;
 @Autowired LevelRepository levels; @Autowired ContentQueryService publicQuery; @Autowired SampleContentDataLoader sample;
 @Autowired GrammarCurationService curation; @Autowired GrammarEnrichmentRepository enrichments;
 @Autowired GrammarRelationRepository relations; @Autowired GrammarComparisonRepository comparisons;
 @Autowired GrammarConfirmationQuestionRepository questions; @Autowired CurationReviewHistoryRepository curationHistory;
 @Autowired StudyRecordRepository studyRecords; @Autowired LearningProgressRepository progress;
 UserAccount admin;
 @BeforeEach void setup() throws Exception {sample.run();admin=accounts.save(new UserAccount("admin-"+UUID.randomUUID(),null,"hash","검수자",UserRole.ADMIN));}
 private ContentItem word(String suffix,String expression,String level){var item=new ContentItem("admin-word-"+suffix,ContentType.WORD,"admin-source",false);var w=new Word(expression,"よみ","명사",null);w.addMeaning(new Meaning("ko",expression+" 뜻",0));item.attachWord(w);levels.findBySystemAndCode("JLPT",level).ifPresent(item::addLevel);return contents.save(item);}
 private ContentItem grammar(String suffix,String pattern,String level){var item=new ContentItem("admin-grammar-"+suffix,ContentType.GRAMMAR,"admin-source",false);item.attachGrammar(new Grammar(pattern,"설명 "+pattern,"동사형"));levels.findBySystemAndCode("JLPT",level).ifPresent(item::addLevel);return contents.save(item);}

 @Test void filtersWithDatabasePaginationAndKeepsPublicPolicy(){for(int i=0;i<30;i++)word("page-"+i,"검수단어"+i,i%2==0?"N5":"N4");grammar("target","〜검수문법","N5");
   var page=reviews.contents(ReviewStatus.PENDING,false,ContentType.WORD,"N5","검수단어","admin-source",null,null,null,true,0);
   assertThat(page.getSize()).isEqualTo(25);assertThat(page.getContent()).hasSize(15).allMatch(r->r.type()==ContentType.WORD&&r.jlpt().equals("N5"));
   assertThat(reviews.contents(ReviewStatus.PENDING,false,ContentType.GRAMMAR,"N5","검수문법",null,null,null,null,false,0).getTotalElements()).isEqualTo(1);
 }

 @Test void approveAndRejectAreIdempotentAuditedAndDoNotTouchLearningData(){var approved=word("approve","비공개검수","N5");var rejected=grammar("reject","〜반려검수","N5");long records=studyRecords.count(),states=progress.count();
   assertThat(publicQuery.search("비공개검수")).isEmpty();assertThat(reviews.approveContent(approved.getId(),admin,"확인").changed()).isTrue();assertThat(reviews.approveContent(approved.getId(),admin,null).changed()).isFalse();
   assertThat(publicQuery.search("비공개검수")).hasSize(1);var ah=histories.findByContentItemIdOrderByReviewedAtDesc(approved.getId());assertThat(ah).hasSize(1);assertThat(ah.get(0).getPreviousStatus()).isEqualTo(ReviewStatus.PENDING);assertThat(ah.get(0).getReviewer().getLoginId()).isEqualTo(admin.getLoginId());
   assertThat(reviews.rejectContent(rejected.getId(),admin,"설명 불충분").changed()).isTrue();assertThat(reviews.rejectContent(rejected.getId(),admin,"again").changed()).isFalse();assertThat(contents.findById(rejected.getId()).orElseThrow().isPublished()).isFalse();assertThat(histories.findByContentItemIdOrderByReviewedAtDesc(rejected.getId())).hasSize(1);
   assertThatThrownBy(()->reviews.rejectContent(approved.getId(),admin,"전이 금지")).isInstanceOf(IllegalStateException.class);assertThat(studyRecords.count()).isEqualTo(records);assertThat(progress.count()).isEqualTo(states);
 }

 @Test void reviewsEachCuratedTypeIndependentlyAndExposesCorrectAnswerOnlyToAdminModel(){
   var e=curation.saveEnrichment("temo-ii","admin:e","뉘앙스","용법","접속","실수","노트");
    var other=grammar("curated-other","〜別文法","N5");
    var r=curation.saveRelation("temo-ii",other.getSlug(),GrammarRelationType.CONFUSABLE,"admin:r");
   var c=curation.saveComparison(r.getId(),"admin:c","요약","차이","사용","혼동");
   var q=curation.saveQuestion("temo-ii",GrammarConfirmationType.MEANING_MATCH,"허가 표현은?",null,"설명","admin:q",List.of(new GrammarCurationService.ChoiceDraft("정답",true),new GrammarCurationService.ChoiceDraft("오답",false)));
   for(var target:List.of(new Object[]{CurationRecordType.ENRICHMENT,e.getId()},new Object[]{CurationRecordType.RELATION,r.getId()},new Object[]{CurationRecordType.COMPARISON,c.getId()},new Object[]{CurationRecordType.CONFIRMATION,q.getId()})){
     var result=reviews.reviewCuration((CurationRecordType)target[0],(Long)target[1],ReviewStatus.APPROVED,admin,"검수");assertThat(result.changed()).isTrue();assertThat(reviews.reviewCuration((CurationRecordType)target[0],(Long)target[1],ReviewStatus.APPROVED,admin,null).changed()).isFalse();assertThat(curationHistory.findByRecordTypeAndRecordIdOrderByReviewedAtDesc((CurationRecordType)target[0],(Long)target[1])).hasSize(1);
   }
   assertThat(reviews.curation(CurationRecordType.CONFIRMATION,q.getId()).choices()).anyMatch(AdminContentReviewModels.Choice::correct);
    var pending=curation.saveEnrichment(other.getSlug(),"admin:reject","x",null,null,null,null);reviews.reviewCuration(CurationRecordType.ENRICHMENT,pending.getId(),ReviewStatus.REJECTED,admin,"부족");assertThat(enrichments.findById(pending.getId()).orElseThrow().isPublished()).isFalse();
 }
}
