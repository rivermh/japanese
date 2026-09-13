package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.content.entity.*;
import com.japanese.content.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("sample") @Transactional
class ContentQualityAuditServiceTest {
    @Autowired ContentQualityAuditService audit;
    @Autowired AdminContentReviewService reviews;
    @Autowired ContentItemRepository contents;
    @Autowired ContentSourceRepository sources;
    @Autowired LevelRepository levels;

    @Test void detectsWordAndGrammarCandidatesWithoutChangingTheirReviewOrPublicationState() {
        String ref="quality-source-"+UUID.randomUUID();
        sources.save(new ContentSource(ref,"Quality source","1",null,null,null,null));
        ContentItem word=word("quality-word-"+UUID.randomUUID(),"품질단어"," ",ref,true);
        ContentItem duplicate=word("quality-word-"+UUID.randomUUID(),"품질단어"," ",ref,true);
        ContentItem noMeaning=wordWithoutMeaning("quality-empty-"+UUID.randomUUID(),"의미없음","읽기",null);
        ContentItem grammar=grammar("quality-grammar-"+UUID.randomUUID(),"~품질", " ",ref,true);
        ContentItem duplicateGrammar=grammar("quality-grammar-"+UUID.randomUUID(),"~품질", "간단한 설명입니다.",ref,true);
        contents.flush();

        assertThat(types(audit.audit(word))).contains(QualityIssueType.WORD_READING_BLANK, QualityIssueType.EXAMPLE_MISSING, QualityIssueType.DUPLICATE_WORD);
        assertThat(types(audit.audit(noMeaning))).contains(QualityIssueType.MEANING_MISSING, QualityIssueType.SOURCE_REF_MISSING);
        assertThat(types(audit.audit(grammar))).contains(QualityIssueType.GRAMMAR_DESCRIPTION_BLANK, QualityIssueType.EXAMPLE_MISSING, QualityIssueType.DUPLICATE_GRAMMAR);
        assertThat(types(audit.audit(duplicateGrammar))).contains(QualityIssueType.DUPLICATE_GRAMMAR);
        assertThat(word.getReviewStatus()).isEqualTo(ReviewStatus.PENDING); assertThat(word.isPublished()).isFalse();
        assertThat(grammar.getReviewStatus()).isEqualTo(ReviewStatus.PENDING); assertThat(grammar.isPublished()).isFalse();
    }

    @Test void reportsCleanItemAndFiltersN5IssuesWithDatabasePagination() {
        String ref="quality-clean-"+UUID.randomUUID(); sources.save(new ContentSource(ref,"Clean source","1",null,null,null,null));
        ContentItem clean=word("quality-filter-clean-"+UUID.randomUUID(),"깨끗한단어","깨끗",ref,true);
        clean.addExample(new Example("これは例文です。",null,"예문입니다.",0));
        ContentItem bad=word("quality-filter-bad-"+UUID.randomUUID(),"문제단어"," ",ref,true);
        contents.flush();
        assertThat(audit.audit(clean).clean()).isTrue();
        var page=reviews.contents(ReviewStatus.PENDING,false,ContentType.WORD,"N5","",ref,true,QualitySeverity.ERROR,null,false,0);
        assertThat(page.getSize()).isEqualTo(25);
        assertThat(page.getContent()).extracting(r->r.id()).contains(bad.getId()).doesNotContain(clean.getId());
        var typed=reviews.contents(ReviewStatus.PENDING,false,ContentType.WORD,"N5","",ref,true,null,QualityIssueType.WORD_READING_BLANK,false,0);
        assertThat(typed.getContent()).extracting(r->r.id()).contains(bad.getId()).doesNotContain(clean.getId());
        assertThat(reviews.content(bad.getId()).qualityAudit().highestSeverity()).isEqualTo(QualitySeverity.ERROR);
    }

    private ContentItem word(String slug,String text,String reading,String ref,boolean n5) {
        ContentItem item=new ContentItem(slug,ContentType.WORD,ref,false); Word word=new Word(text,reading,"명사",null); word.addMeaning(new Meaning("ko",text+" 뜻",0)); item.attachWord(word); if(n5) level(item); return contents.save(item);
    }
    private ContentItem wordWithoutMeaning(String slug,String text,String reading,String ref) { ContentItem item=new ContentItem(slug,ContentType.WORD,ref,false);item.attachWord(new Word(text,reading,"명사",null));level(item);return contents.save(item); }
    private ContentItem grammar(String slug,String pattern,String description,String ref,boolean n5) { ContentItem item=new ContentItem(slug,ContentType.GRAMMAR,ref,false);item.attachGrammar(new Grammar(pattern,description,"연결"));if(n5)level(item);return contents.save(item); }
    private void level(ContentItem item){levels.findBySystemAndCode("JLPT","N5").ifPresent(item::addLevel);}
    private Set<QualityIssueType> types(ContentQualityAuditService.Audit audit){return audit.issues().stream().map(ContentQualityAuditService.Issue::type).collect(java.util.stream.Collectors.toSet());}
}
