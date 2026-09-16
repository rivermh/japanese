package com.japanese.content.service;

import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Example;
import com.japanese.content.entity.Grammar;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.QualityIssueType;
import com.japanese.content.entity.Word;
import com.japanese.content.search.ContentSearchNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collection;
import java.util.HashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Interprets quality hints and source rights as a deterministic publication decision. */
@Service
public class ContentReleaseGateService {

    private final ContentQualityAuditService qualityAudit;
    private final ContentSourceRightsService sourceRights;

    public ContentReleaseGateService(
            ContentQualityAuditService qualityAudit,
            ContentSourceRightsService sourceRights) {
        this.qualityAudit = qualityAudit;
        this.sourceRights = sourceRights;
    }

    @Transactional(readOnly = true)
    public Result evaluate(ContentItem item) {
        return evaluate(item, qualityAudit.audit(item));
    }

    public Result evaluate(ContentItem item, ContentQualityAuditService.Audit audit) {
        return evaluate(item, audit, sourceRights.releaseEligibility(item.getSourceRef()));
    }

    /** Evaluates a batch with one quality pass and cached source-rights lookups. */
    @Transactional(readOnly = true)
    public Map<Long, Result> evaluateAll(Collection<ContentItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<Long, ContentQualityAuditService.Audit> audits = qualityAudit.audits(items);
        Map<String, ContentSourceRightsService.ReleaseEligibility> rights = new HashMap<>();
        Map<Long, Result> results = new LinkedHashMap<>();
        for (ContentItem item : items) {
            ContentSourceRightsService.ReleaseEligibility eligibility = rights.computeIfAbsent(
                    item.getSourceRef(), sourceRights::releaseEligibility);
            results.put(item.getId(), evaluate(item, audits.get(item.getId()), eligibility));
        }
        return results;
    }

    private Result evaluate(ContentItem item,
                            ContentQualityAuditService.Audit audit,
                            ContentSourceRightsService.ReleaseEligibility rights) {
        Map<ContentReleaseIssueCode, Issue> issues = new LinkedHashMap<>();
        if (item.getType() == ContentType.WORD) {
            evaluateWord(item, issues);
        } else {
            evaluateGrammar(item, issues);
        }
        evaluateJlpt(item, issues);
        mapQualityIssues(audit, issues);

        mapSourceRights(rights, issues);
        evaluateCriticalParsingDamage(item, issues);

        List<Issue> blockers = classified(issues, ContentReleaseIssueClassification.PUBLICATION_BLOCKER);
        List<Issue> manual = classified(issues, ContentReleaseIssueClassification.MANUAL_REVIEW);
        List<Issue> informational = classified(issues, ContentReleaseIssueClassification.INFORMATIONAL);
        ContentReleaseDecision decision = !blockers.isEmpty()
                ? ContentReleaseDecision.BLOCKED
                : !manual.isEmpty()
                        ? ContentReleaseDecision.MANUAL_REVIEW_REQUIRED
                        : ContentReleaseDecision.RELEASABLE;
        return new Result(
                decision,
                item.getType(),
                blockers,
                manual,
                informational,
                rights,
                reason(decision, blockers, manual));
    }

    private void evaluateWord(ContentItem item, Map<ContentReleaseIssueCode, Issue> issues) {
        Word word = item.getWord();
        if (word == null) {
            add(issues, ContentReleaseIssueCode.WORD_ENTITY_MISSING,
                    ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                    "Word 데이터가 없습니다.", null);
            return;
        }
        if (blank(word.getExpression())) {
            add(issues, ContentReleaseIssueCode.WORD_EXPRESSION_MISSING,
                    ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                    "단어 표기가 없습니다.", QualityIssueType.WORD_TEXT_BLANK);
        }
        if (blank(word.getReading())) {
            add(issues, ContentReleaseIssueCode.WORD_READING_MISSING,
                    ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                    "단어 읽기가 없습니다.", QualityIssueType.WORD_READING_BLANK);
        }
        if (blank(ContentSearchNormalizer.normalize(word.getExpression()))) {
            add(issues, ContentReleaseIssueCode.WORD_SEARCH_KEY_MISSING,
                    ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                    "단어 검색 key를 만들 수 없습니다.", null);
        }
        boolean koreanMeaning = word.getMeanings().stream().anyMatch(this::isNonBlankKoreanMeaning);
        if (!koreanMeaning) {
            add(issues, ContentReleaseIssueCode.WORD_KOREAN_MEANING_MISSING,
                    ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                    "비어 있지 않은 한국어 의미가 없습니다.", QualityIssueType.MEANING_MISSING);
        }
        if (blank(word.getPartOfSpeech())) {
            add(issues, ContentReleaseIssueCode.WORD_PART_OF_SPEECH_MISSING,
                    ContentReleaseIssueClassification.MANUAL_REVIEW,
                    "품사 확인이 필요합니다.", null);
        }
    }

    private void evaluateGrammar(ContentItem item, Map<ContentReleaseIssueCode, Issue> issues) {
        Grammar grammar = item.getGrammar();
        if (grammar == null) {
            add(issues, ContentReleaseIssueCode.GRAMMAR_ENTITY_MISSING,
                    ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                    "Grammar 데이터가 없습니다.", null);
            return;
        }
        if (blank(grammar.getPattern())) {
            add(issues, ContentReleaseIssueCode.GRAMMAR_PATTERN_MISSING,
                    ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                    "문법 패턴이 없습니다.", QualityIssueType.GRAMMAR_PATTERN_BLANK);
        }
        if (blank(grammar.getExplanation())) {
            add(issues, ContentReleaseIssueCode.GRAMMAR_EXPLANATION_MISSING,
                    ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                    "문법 설명이 없습니다.", QualityIssueType.GRAMMAR_DESCRIPTION_BLANK);
        }
    }

    private void evaluateJlpt(ContentItem item, Map<ContentReleaseIssueCode, Issue> issues) {
        boolean hasJlpt = item.getLevels().stream().anyMatch(level -> "JLPT".equals(level.getSystem()));
        if (!hasJlpt) {
            add(issues, ContentReleaseIssueCode.JLPT_LEVEL_MISSING,
                    ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                    "JLPT 레벨이 없습니다.", QualityIssueType.JLPT_LEVEL_MISSING);
        }
    }

    private void mapQualityIssues(
            ContentQualityAuditService.Audit audit,
            Map<ContentReleaseIssueCode, Issue> issues) {
        for (ContentQualityAuditService.Issue qualityIssue : audit.issues()) {
            switch (qualityIssue.type()) {
                case WORD_TEXT_BLANK -> add(issues, ContentReleaseIssueCode.WORD_EXPRESSION_MISSING,
                        ContentReleaseIssueClassification.PUBLICATION_BLOCKER, qualityIssue.message(), qualityIssue.type());
                case WORD_READING_BLANK -> add(issues, ContentReleaseIssueCode.WORD_READING_MISSING,
                        ContentReleaseIssueClassification.PUBLICATION_BLOCKER, qualityIssue.message(), qualityIssue.type());
                case MEANING_MISSING -> add(issues, ContentReleaseIssueCode.WORD_KOREAN_MEANING_MISSING,
                        ContentReleaseIssueClassification.PUBLICATION_BLOCKER, qualityIssue.message(), qualityIssue.type());
                case MEANING_BLANK -> add(issues, ContentReleaseIssueCode.REPRESENTATIVE_MEANING_BLANK,
                        ContentReleaseIssueClassification.MANUAL_REVIEW, qualityIssue.message(), qualityIssue.type());
                case GRAMMAR_PATTERN_BLANK -> add(issues, ContentReleaseIssueCode.GRAMMAR_PATTERN_MISSING,
                        ContentReleaseIssueClassification.PUBLICATION_BLOCKER, qualityIssue.message(), qualityIssue.type());
                case GRAMMAR_DESCRIPTION_BLANK -> add(issues, ContentReleaseIssueCode.GRAMMAR_EXPLANATION_MISSING,
                        ContentReleaseIssueClassification.PUBLICATION_BLOCKER, qualityIssue.message(), qualityIssue.type());
                case SOURCE_REF_MISSING -> add(issues, ContentReleaseIssueCode.SOURCE_REF_MISSING,
                        ContentReleaseIssueClassification.PUBLICATION_BLOCKER, qualityIssue.message(), qualityIssue.type());
                case SOURCE_MISSING -> add(issues, ContentReleaseIssueCode.SOURCE_NOT_REGISTERED,
                        ContentReleaseIssueClassification.PUBLICATION_BLOCKER, qualityIssue.message(), qualityIssue.type());
                case JLPT_LEVEL_MISSING -> add(issues, ContentReleaseIssueCode.JLPT_LEVEL_MISSING,
                        ContentReleaseIssueClassification.PUBLICATION_BLOCKER, qualityIssue.message(), qualityIssue.type());
                case DUPLICATE_WORD, DUPLICATE_GRAMMAR -> add(issues, ContentReleaseIssueCode.DUPLICATE_CANDIDATE,
                        ContentReleaseIssueClassification.MANUAL_REVIEW, qualityIssue.message(), qualityIssue.type());
                case EXAMPLE_MISSING -> add(issues, ContentReleaseIssueCode.EXAMPLE_MISSING,
                        ContentReleaseIssueClassification.MANUAL_REVIEW, qualityIssue.message(), qualityIssue.type());
                case EXAMPLE_TEXT_BLANK -> add(issues, ContentReleaseIssueCode.EXAMPLE_TEXT_MISSING,
                        ContentReleaseIssueClassification.MANUAL_REVIEW, qualityIssue.message(), qualityIssue.type());
                case EXAMPLE_TRANSLATION_BLANK -> add(issues, ContentReleaseIssueCode.EXAMPLE_TRANSLATION_MISSING,
                        ContentReleaseIssueClassification.MANUAL_REVIEW, qualityIssue.message(), qualityIssue.type());
                case READING_TOO_LONG -> add(issues, ContentReleaseIssueCode.WORD_READING_TOO_LONG,
                        ContentReleaseIssueClassification.MANUAL_REVIEW, qualityIssue.message(), qualityIssue.type());
                case GRAMMAR_DESCRIPTION_TOO_SHORT -> add(issues, ContentReleaseIssueCode.GRAMMAR_DESCRIPTION_TOO_SHORT,
                        ContentReleaseIssueClassification.MANUAL_REVIEW, qualityIssue.message(), qualityIssue.type());
                case GRAMMAR_DESCRIPTION_TOO_LONG -> add(issues, ContentReleaseIssueCode.GRAMMAR_DESCRIPTION_TOO_LONG,
                        ContentReleaseIssueClassification.MANUAL_REVIEW, qualityIssue.message(), qualityIssue.type());
                case MARKUP_SUSPECTED -> add(issues, ContentReleaseIssueCode.MARKUP_SUSPECTED,
                        ContentReleaseIssueClassification.MANUAL_REVIEW, qualityIssue.message(), qualityIssue.type());
                case MEANING_TOO_LONG -> add(issues, ContentReleaseIssueCode.MEANING_TOO_LONG,
                        ContentReleaseIssueClassification.INFORMATIONAL, qualityIssue.message(), qualityIssue.type());
                case SURROUNDING_WHITESPACE -> add(issues, ContentReleaseIssueCode.SURROUNDING_WHITESPACE,
                        ContentReleaseIssueClassification.INFORMATIONAL, qualityIssue.message(), qualityIssue.type());
            }
        }
    }

    private void mapSourceRights(
            ContentSourceRightsService.ReleaseEligibility rights,
            Map<ContentReleaseIssueCode, Issue> issues) {
        if (rights.allowed()) {
            return;
        }
        ContentReleaseIssueCode code = switch (rights.blockingCode()) {
            case "SOURCE_REF_MISSING" -> ContentReleaseIssueCode.SOURCE_REF_MISSING;
            case "SOURCE_NOT_REGISTERED" -> ContentReleaseIssueCode.SOURCE_NOT_REGISTERED;
            case "SOURCE_REF_NOT_CANONICAL" -> ContentReleaseIssueCode.SOURCE_REF_NOT_CANONICAL;
            case "ATTRIBUTION_MISSING" -> ContentReleaseIssueCode.SOURCE_ATTRIBUTION_MISSING;
            default -> ContentReleaseIssueCode.SOURCE_RIGHTS_NOT_ALLOWED;
        };
        add(issues, code, ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                rights.blockingReason(), null);
    }

    private void evaluateCriticalParsingDamage(
            ContentItem item,
            Map<ContentReleaseIssueCode, Issue> issues) {
        List<String> values = new ArrayList<>();
        values.add(item.getSourceRef());
        if (item.getWord() != null) {
            values.add(item.getWord().getExpression());
            values.add(item.getWord().getReading());
            item.getWord().getMeanings().stream().map(Meaning::getText).forEach(values::add);
        }
        if (item.getGrammar() != null) {
            values.add(item.getGrammar().getPattern());
            values.add(item.getGrammar().getExplanation());
            values.add(item.getGrammar().getConnection());
        }
        item.getExamples().stream().map(Example::getJapaneseText).forEach(values::add);
        item.getExamples().stream().map(Example::getTranslation).forEach(values::add);
        if (values.stream().anyMatch(this::hasCriticalParsingDamage)) {
            add(issues, ContentReleaseIssueCode.CONTENT_PARSING_DAMAGE,
                    ContentReleaseIssueClassification.PUBLICATION_BLOCKER,
                    "깨진 문자 또는 제어 문자가 있어 파싱 결과를 신뢰할 수 없습니다.", null);
        }
    }

    private boolean isNonBlankKoreanMeaning(Meaning meaning) {
        String language = meaning.getLanguageTag();
        return language != null
                && language.toLowerCase(Locale.ROOT).startsWith("ko")
                && !blank(meaning.getText());
    }

    private boolean hasCriticalParsingDamage(String value) {
        return value != null && (value.indexOf('\u0000') >= 0 || value.indexOf('\ufffd') >= 0);
    }

    private static void add(
            Map<ContentReleaseIssueCode, Issue> issues,
            ContentReleaseIssueCode code,
            ContentReleaseIssueClassification classification,
            String message,
            QualityIssueType qualityIssueType) {
        issues.putIfAbsent(code, new Issue(code, classification, message, qualityIssueType));
    }

    private static List<Issue> classified(
            Map<ContentReleaseIssueCode, Issue> issues,
            ContentReleaseIssueClassification classification) {
        return issues.values().stream().filter(issue -> issue.classification() == classification).toList();
    }

    private static String reason(
            ContentReleaseDecision decision,
            List<Issue> blockers,
            List<Issue> manual) {
        return switch (decision) {
            case RELEASABLE -> "모든 core release 조건을 충족했습니다.";
            case BLOCKED -> "공개 차단: " + joinCodes(blockers);
            case MANUAL_REVIEW_REQUIRED -> "수동 확인 필요: " + joinCodes(manual);
        };
    }

    private static String joinCodes(List<Issue> issues) {
        return String.join(", ", issues.stream().map(issue -> issue.code().name()).toList());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Issue(
            ContentReleaseIssueCode code,
            ContentReleaseIssueClassification classification,
            String message,
            QualityIssueType qualityIssueType) {
    }

    public record Result(
            ContentReleaseDecision decision,
            ContentType contentType,
            List<Issue> blockers,
            List<Issue> manualReview,
            List<Issue> informational,
            ContentSourceRightsService.ReleaseEligibility sourceRights,
            String reason) {
        public boolean releasable() {
            return decision == ContentReleaseDecision.RELEASABLE;
        }
    }
}
