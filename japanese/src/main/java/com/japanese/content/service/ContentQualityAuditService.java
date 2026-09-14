package com.japanese.content.service;

import com.japanese.content.entity.*;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.ContentSourceRepository;
import jakarta.persistence.criteria.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only deterministic hints for a reviewer; no content or review state is changed here. */
@Service
public class ContentQualityAuditService {
    public record Issue(QualityIssueType type, QualitySeverity severity, String message) {}
    public record Audit(List<Issue> issues) {
        public int issueCount() { return issues.size(); }
        public boolean clean() { return issues.isEmpty(); }
        public QualitySeverity highestSeverity() {
            if (issues.stream().anyMatch(i -> i.severity() == QualitySeverity.ERROR)) return QualitySeverity.ERROR;
            if (issues.stream().anyMatch(i -> i.severity() == QualitySeverity.WARNING)) return QualitySeverity.WARNING;
            return issues.isEmpty() ? null : QualitySeverity.INFO;
        }
    }

    private final ContentItemRepository contents;
    private final ContentSourceRepository sources;
    public ContentQualityAuditService(ContentItemRepository contents, ContentSourceRepository sources) { this.contents=contents; this.sources=sources; }

    @Transactional(readOnly = true)
    public Audit audit(ContentItem item) { return audits(List.of(item)).get(item.getId()); }

    @Transactional(readOnly = true)
    public Map<Long, Audit> audits(Collection<ContentItem> items) {
        if (items.isEmpty()) return Map.of();
        Set<Long> ids=new LinkedHashSet<>(); Set<String> refs=new LinkedHashSet<>();
        for (ContentItem item:items) { ids.add(item.getId()); if (!blank(item.getSourceRef())) refs.add(item.getSourceRef().trim()); }
        Set<Long> duplicateWords=new HashSet<>(contents.findDuplicateWordContentIds(ids));
        Set<Long> duplicateGrammars=new HashSet<>(contents.findDuplicateGrammarContentIds(ids));
        Set<String> known=new HashSet<>(); for (ContentSource source:sources.findBySourceRefIn(refs)) known.add(source.getSourceRef());
        Map<Long,Audit> result=new LinkedHashMap<>();
        for (ContentItem item:items) result.put(item.getId(),new Audit(issues(item,duplicateWords.contains(item.getId()),duplicateGrammars.contains(item.getId()),known.contains(trim(item.getSourceRef())))));
        return result;
    }

    private List<Issue> issues(ContentItem item,boolean duplicateWord,boolean duplicateGrammar,boolean knownSource) {
        List<Issue> result=new ArrayList<>();
        if (blank(item.getSourceRef())) add(result,QualityIssueType.SOURCE_REF_MISSING,QualitySeverity.WARNING,"sourceRef is missing");
        else { if (!item.getSourceRef().equals(item.getSourceRef().trim())) add(result,QualityIssueType.SURROUNDING_WHITESPACE,QualitySeverity.INFO,"sourceRef has surrounding whitespace"); if (!knownSource) add(result,QualityIssueType.SOURCE_MISSING,QualitySeverity.WARNING,"No ContentSource matches sourceRef"); format(item.getSourceRef(),result); }
        if (item.getLevels().stream().noneMatch(l -> "JLPT".equals(l.getSystem()))) add(result,QualityIssueType.JLPT_LEVEL_MISSING,QualitySeverity.WARNING,"JLPT level is missing");
        if (item.getType()==ContentType.WORD) word(item,duplicateWord,result); else grammar(item,duplicateGrammar,result);
        examples(item,result); return List.copyOf(result);
    }
    private void word(ContentItem item,boolean duplicate,List<Issue> out) {
        Word word=item.getWord(); String text=word==null?null:word.getExpression(); String reading=word==null?null:word.getReading();
        if (blank(text)) add(out,QualityIssueType.WORD_TEXT_BLANK,QualitySeverity.ERROR,"Word text is blank");
        if (blank(reading)) add(out,QualityIssueType.WORD_READING_BLANK,QualitySeverity.ERROR,"Reading is blank");
        if (word==null||word.getMeanings().isEmpty()) add(out,QualityIssueType.MEANING_MISSING,QualitySeverity.ERROR,"No meaning is stored");
        else { int representativeOrder=word.getMeanings().stream().mapToInt(Meaning::getSenseOrder).min().orElse(0); if(word.getMeanings().stream().filter(m->m.getSenseOrder()==representativeOrder).anyMatch(m->blank(m.getText()))) add(out,QualityIssueType.MEANING_BLANK,QualitySeverity.ERROR,"Representative meaning is blank"); for(Meaning m:word.getMeanings()){if(m.getText()!=null&&m.getText().length()>300)add(out,QualityIssueType.MEANING_TOO_LONG,QualitySeverity.INFO,"Meaning is longer than 300 characters");format(m.getText(),out);} }
        if(reading!=null&&reading.length()>50)add(out,QualityIssueType.READING_TOO_LONG,QualitySeverity.WARNING,"Reading is longer than 50 characters");
        if(duplicate)add(out,QualityIssueType.DUPLICATE_WORD,QualitySeverity.WARNING,"Another item has the same normalized word and reading"); format(text,out);format(reading,out);
    }
    private void grammar(ContentItem item,boolean duplicate,List<Issue> out) {
        Grammar grammar=item.getGrammar();String pattern=grammar==null?null:grammar.getPattern(),description=grammar==null?null:grammar.getExplanation();
        if(blank(pattern))add(out,QualityIssueType.GRAMMAR_PATTERN_BLANK,QualitySeverity.ERROR,"Grammar pattern is blank");
        if(blank(description))add(out,QualityIssueType.GRAMMAR_DESCRIPTION_BLANK,QualitySeverity.ERROR,"Grammar description is blank");
        else {if(description.trim().length()<12)add(out,QualityIssueType.GRAMMAR_DESCRIPTION_TOO_SHORT,QualitySeverity.INFO,"Grammar description is shorter than 12 characters");if(description.length()>1000)add(out,QualityIssueType.GRAMMAR_DESCRIPTION_TOO_LONG,QualitySeverity.INFO,"Grammar description is longer than 1,000 characters");}
        if(duplicate)add(out,QualityIssueType.DUPLICATE_GRAMMAR,QualitySeverity.WARNING,"Another item has the same normalized grammar pattern");format(pattern,out);format(description,out);if(grammar!=null)format(grammar.getConnection(),out);
    }
    private void examples(ContentItem item,List<Issue> out){if(item.getExamples().isEmpty())add(out,QualityIssueType.EXAMPLE_MISSING,QualitySeverity.WARNING,"No example is stored");for(Example e:item.getExamples()){if(blank(e.getJapaneseText()))add(out,QualityIssueType.EXAMPLE_TEXT_BLANK,QualitySeverity.WARNING,"Example text is blank");if(blank(e.getTranslation()))add(out,QualityIssueType.EXAMPLE_TRANSLATION_BLANK,QualitySeverity.WARNING,"Example translation is blank");format(e.getJapaneseText(),out);format(e.getTranslation(),out);}}
    private void format(String value,List<Issue> out){if(value==null)return;if(!value.equals(value.trim()))add(out,QualityIssueType.SURROUNDING_WHITESPACE,QualitySeverity.INFO,"Text has surrounding whitespace");if(value.contains("<")||value.contains("&lt;"))add(out,QualityIssueType.MARKUP_SUSPECTED,QualitySeverity.INFO,"HTML or broken markup is present");}
    private static void add(List<Issue> out,QualityIssueType type,QualitySeverity severity,String message){if(out.stream().noneMatch(i->i.type()==type))out.add(new Issue(type,severity,message));}
    private static boolean blank(String value){return value==null||value.trim().isEmpty();} private static String trim(String value){return value==null?null:value.trim();}

    public Specification<ContentItem> issueFilter(QualitySeverity severity,QualityIssueType type){return (root,q,cb)->predicate(root,q,cb,severity,type);}
    private Predicate predicate(Root<ContentItem> root,CriteriaQuery<?> q,CriteriaBuilder cb,QualitySeverity severity,QualityIssueType requested){if(requested!=null){if(severity!=null&&severityFor(requested)!=severity)return cb.disjunction();return issue(root,q,cb,requested);}List<Predicate> all=new ArrayList<>();for(QualityIssueType type:QualityIssueType.values())if(severity==null||severityFor(type)==severity)all.add(issue(root,q,cb,type));return cb.or(all.toArray(Predicate[]::new));}
    private Predicate issue(Root<ContentItem> root,CriteriaQuery<?> q,CriteriaBuilder cb,QualityIssueType type){Join<ContentItem,Word>w=root.join("word",JoinType.LEFT);Join<ContentItem,Grammar>g=root.join("grammar",JoinType.LEFT);return switch(type){
        case WORD_TEXT_BLANK->cb.and(cb.equal(root.get("type"),ContentType.WORD),blank(cb,w.get("expression"))); case WORD_READING_BLANK->cb.and(cb.equal(root.get("type"),ContentType.WORD),blank(cb,w.get("reading")));
        case MEANING_MISSING->cb.and(cb.equal(root.get("type"),ContentType.WORD),cb.not(cb.exists(meaning(q,cb,root,m->cb.conjunction())))); case MEANING_BLANK->cb.and(cb.equal(root.get("type"),ContentType.WORD),cb.exists(representativeMeaning(q,cb,root,m->blank(cb,m.get("text")))));
        case EXAMPLE_MISSING->cb.not(cb.exists(example(q,cb,root,e->cb.conjunction()))); case EXAMPLE_TEXT_BLANK->cb.exists(example(q,cb,root,e->blank(cb,e.get("japaneseText")))); case EXAMPLE_TRANSLATION_BLANK->cb.exists(example(q,cb,root,e->blank(cb,e.get("translation"))));
        case SOURCE_REF_MISSING->blank(cb,root.get("sourceRef")); case SOURCE_MISSING->cb.and(cb.not(blank(cb,root.get("sourceRef"))),cb.not(cb.exists(source(q,cb,root)))); case JLPT_LEVEL_MISSING->cb.not(cb.exists(jlpt(q,cb,root)));
        case DUPLICATE_WORD->cb.and(cb.equal(root.get("type"),ContentType.WORD),cb.exists(dupWord(q,cb,w))); case DUPLICATE_GRAMMAR->cb.and(cb.equal(root.get("type"),ContentType.GRAMMAR),cb.exists(dupGrammar(q,cb,g)));
        case MEANING_TOO_LONG->cb.exists(meaning(q,cb,root,m->cb.greaterThan(cb.length(m.get("text")),300))); case READING_TOO_LONG->cb.and(cb.equal(root.get("type"),ContentType.WORD),cb.greaterThan(cb.length(w.get("reading")),50));
        case GRAMMAR_PATTERN_BLANK->cb.and(cb.equal(root.get("type"),ContentType.GRAMMAR),blank(cb,g.get("pattern"))); case GRAMMAR_DESCRIPTION_BLANK->cb.and(cb.equal(root.get("type"),ContentType.GRAMMAR),blank(cb,g.get("explanation"))); case GRAMMAR_DESCRIPTION_TOO_SHORT->cb.and(cb.equal(root.get("type"),ContentType.GRAMMAR),cb.not(blank(cb,g.get("explanation"))),cb.lessThan(cb.length(cb.trim(g.get("explanation"))),12)); case GRAMMAR_DESCRIPTION_TOO_LONG->cb.and(cb.equal(root.get("type"),ContentType.GRAMMAR),cb.greaterThan(cb.length(g.get("explanation")),1000));
        case MARKUP_SUSPECTED->cb.or(markup(cb,root.get("sourceRef")),markup(cb,w.get("expression")),markup(cb,w.get("reading")),markup(cb,g.get("pattern")),markup(cb,g.get("explanation")),markup(cb,g.get("connection")),cb.exists(meaning(q,cb,root,m->markup(cb,m.get("text")))),cb.exists(example(q,cb,root,e->cb.or(markup(cb,e.get("japaneseText")),markup(cb,e.get("translation"))))));
        case SURROUNDING_WHITESPACE->cb.or(cb.and(cb.not(blank(cb,root.get("sourceRef"))),spaces(cb,root.get("sourceRef"))),spaces(cb,w.get("expression")),spaces(cb,w.get("reading")),spaces(cb,g.get("pattern")),spaces(cb,g.get("explanation")),spaces(cb,g.get("connection")),cb.exists(meaning(q,cb,root,m->spaces(cb,m.get("text")))),cb.exists(example(q,cb,root,e->cb.or(spaces(cb,e.get("japaneseText")),spaces(cb,e.get("translation"))))));};}
    private static Subquery<Long> meaning(CriteriaQuery<?>q,CriteriaBuilder cb,Root<ContentItem>root,Function<Root<Meaning>,Predicate> f){Subquery<Long>s=q.subquery(Long.class);Root<Meaning>m=s.from(Meaning.class);s.select(m.get("id")).where(cb.equal(m.join("word").get("contentItem"),root),f.apply(m));return s;}
    private static Subquery<Long> representativeMeaning(CriteriaQuery<?>q,CriteriaBuilder cb,Root<ContentItem>root,Function<Root<Meaning>,Predicate> f){Subquery<Long>s=q.subquery(Long.class);Root<Meaning>m=s.from(Meaning.class);Subquery<Integer>minimum=q.subquery(Integer.class);Root<Meaning>candidate=minimum.from(Meaning.class);minimum.select(cb.min(candidate.get("senseOrder"))).where(cb.equal(candidate.join("word").get("contentItem"),root));s.select(m.get("id")).where(cb.equal(m.join("word").get("contentItem"),root),cb.equal(m.get("senseOrder"),minimum),f.apply(m));return s;}
    private static Subquery<Long> example(CriteriaQuery<?>q,CriteriaBuilder cb,Root<ContentItem>root,Function<Root<Example>,Predicate> f){Subquery<Long>s=q.subquery(Long.class);Root<Example>e=s.from(Example.class);s.select(e.get("id")).where(cb.equal(e.get("contentItem"),root),f.apply(e));return s;}
    private static Subquery<Long> source(CriteriaQuery<?>q,CriteriaBuilder cb,Root<ContentItem>root){Subquery<Long>s=q.subquery(Long.class);Root<ContentSource>v=s.from(ContentSource.class);s.select(v.get("id")).where(cb.equal(v.get("sourceRef"),cb.trim(root.get("sourceRef"))));return s;}
    private static Subquery<Long> jlpt(CriteriaQuery<?>q,CriteriaBuilder cb,Root<ContentItem>root){Subquery<Long>s=q.subquery(Long.class);Root<ContentItem>i=s.from(ContentItem.class);Join<ContentItem,Level>l=i.join("levels");s.select(i.get("id")).where(cb.equal(i,root),cb.equal(l.get("system"),"JLPT"));return s;}
    private static Subquery<Long> dupWord(CriteriaQuery<?>q,CriteriaBuilder cb,Join<ContentItem,Word>w){Subquery<Long>s=q.subquery(Long.class);Root<Word>p=s.from(Word.class);s.select(p.get("id")).where(cb.notEqual(p.get("id"),w.get("id")),cb.equal(p.get("expressionSearch"),w.get("expressionSearch")),cb.equal(p.get("readingSearch"),w.get("readingSearch")),cb.isNotNull(w.get("expressionSearch")),cb.isNotNull(w.get("readingSearch")));return s;}
    private static Subquery<Long> dupGrammar(CriteriaQuery<?>q,CriteriaBuilder cb,Join<ContentItem,Grammar>g){Subquery<Long>s=q.subquery(Long.class);Root<Grammar>p=s.from(Grammar.class);s.select(p.get("id")).where(cb.notEqual(p.get("id"),g.get("id")),cb.equal(p.get("patternSearch"),g.get("patternSearch")),cb.isNotNull(g.get("patternSearch")));return s;}
    private static Predicate blank(CriteriaBuilder cb,Expression<String>v){return cb.or(cb.isNull(v),cb.equal(cb.trim(v),""));}private static Predicate markup(CriteriaBuilder cb,Expression<String>v){return cb.or(cb.like(v,"%<%"),cb.like(v,"%&lt;%"));}private static Predicate spaces(CriteriaBuilder cb,Expression<String>v){return cb.and(cb.isNotNull(v),cb.notEqual(v,cb.trim(v)));}
    private static QualitySeverity severityFor(QualityIssueType t){return switch(t){case WORD_TEXT_BLANK,WORD_READING_BLANK,MEANING_MISSING,MEANING_BLANK,GRAMMAR_PATTERN_BLANK,GRAMMAR_DESCRIPTION_BLANK->QualitySeverity.ERROR;case EXAMPLE_MISSING,EXAMPLE_TEXT_BLANK,EXAMPLE_TRANSLATION_BLANK,SOURCE_REF_MISSING,SOURCE_MISSING,JLPT_LEVEL_MISSING,DUPLICATE_WORD,DUPLICATE_GRAMMAR,READING_TOO_LONG->QualitySeverity.WARNING;default->QualitySeverity.INFO;};}
}
