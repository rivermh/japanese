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
        var severityOnly=reviews.contents(ReviewStatus.PENDING,false,ContentType.WORD,"N5","",ref,null,QualitySeverity.ERROR,null,false,0);
        assertThat(severityOnly.getContent()).extracting(r->r.id()).contains(bad.getId()).doesNotContain(clean.getId());
        var typed=reviews.contents(ReviewStatus.PENDING,false,ContentType.WORD,"N5","",ref,null,null,QualityIssueType.WORD_READING_BLANK,false,0);
        assertThat(typed.getContent()).extracting(r->r.id()).contains(bad.getId()).doesNotContain(clean.getId());
        assertThat(reviews.content(bad.getId()).qualityAudit().highestSeverity()).isEqualTo(QualitySeverity.ERROR);
    }

    @Test void runtimeAuditAndDatabaseFilterShareRepresentativeMeaningAndGrammarFormattingPolicies() {
        ContentItem representativeBlank=word("quality-parity-representative-"+UUID.randomUUID(),"대표의미","だいひょう",null,false);
        representativeBlank.getWord().getMeanings().clear();
        representativeBlank.getWord().addMeaning(new Meaning("ko"," ",0));
        representativeBlank.getWord().addMeaning(new Meaning("ko","두 번째 의미",1));
        contents.save(representativeBlank);

        ContentItem nonRepresentativeBlank=word("quality-parity-secondary-"+UUID.randomUUID(),"보조의미","ほじょ",null,false);
        nonRepresentativeBlank.getWord().addMeaning(new Meaning("ko"," ",1));
        contents.save(nonRepresentativeBlank);

        ContentItem shortDescription=grammarWithConnection("quality-parity-short-"+UUID.randomUUID(),"~짧은", "  짧은 설명  ",null);
        ContentItem markupConnection=grammarWithConnection("quality-parity-markup-"+UUID.randomUUID(),"~마크업", "충분히 긴 문법 설명입니다.","연결 <tag>");
        ContentItem whitespaceConnection=grammarWithConnection("quality-parity-space-"+UUID.randomUUID(),"~공백", "충분히 긴 문법 설명입니다."," 연결 ");
        contents.flush();

        assertParity(representativeBlank,QualityIssueType.MEANING_BLANK,true);
        assertParity(nonRepresentativeBlank,QualityIssueType.MEANING_BLANK,false);
        assertParity(shortDescription,QualityIssueType.GRAMMAR_DESCRIPTION_TOO_SHORT,true);
        assertParity(markupConnection,QualityIssueType.MARKUP_SUSPECTED,true);
        assertParity(whitespaceConnection,QualityIssueType.SURROUNDING_WHITESPACE,true);
    }

    private void assertParity(ContentItem item,QualityIssueType issue,boolean expected) {
        boolean runtime=types(audit.audit(item)).contains(issue);
        boolean database=contents.findAll(audit.issueFilter(null,issue)).stream().anyMatch(candidate->candidate.getId().equals(item.getId()));
        assertThat(runtime).as("runtime %s for %s",issue,item.getId()).isEqualTo(expected);
        assertThat(database).as("database %s for %s",issue,item.getId()).isEqualTo(runtime);
    }

    private ContentItem word(String slug,String text,String reading,String ref,boolean n5) {
        ContentItem item=new ContentItem(slug,ContentType.WORD,ref,false); Word word=new Word(text,reading,"명사",null); word.addMeaning(new Meaning("ko",text+" 뜻",0)); item.attachWord(word); if(n5) level(item); return contents.save(item);
    }
    private ContentItem wordWithoutMeaning(String slug,String text,String reading,String ref) { ContentItem item=new ContentItem(slug,ContentType.WORD,ref,false);item.attachWord(new Word(text,reading,"명사",null));level(item);return contents.save(item); }
    private ContentItem grammar(String slug,String pattern,String description,String ref,boolean n5) { ContentItem item=new ContentItem(slug,ContentType.GRAMMAR,ref,false);item.attachGrammar(new Grammar(pattern,description,"연결"));if(n5)level(item);return contents.save(item); }
    private ContentItem grammarWithConnection(String slug,String pattern,String description,String connection) { ContentItem item=new ContentItem(slug,ContentType.GRAMMAR,null,false);item.attachGrammar(new Grammar(pattern,description,connection));return contents.save(item); }
    private void level(ContentItem item){levels.findBySystemAndCode("JLPT","N5").ifPresent(item::addLevel);}
    private Set<QualityIssueType> types(ContentQualityAuditService.Audit audit){return audit.issues().stream().map(ContentQualityAuditService.Issue::type).collect(java.util.stream.Collectors.toSet());}
}
