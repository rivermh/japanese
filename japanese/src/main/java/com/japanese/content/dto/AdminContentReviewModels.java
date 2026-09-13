package com.japanese.content.dto;

import com.japanese.content.entity.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class AdminContentReviewModels {
    private AdminContentReviewModels() {}
    public record QualitySummary(long total, long clean, long info, long warning, long error) {}
    public record QualityIssue(QualityIssueType type, QualitySeverity severity, String message) {}
    public record QualityAudit(int issueCount, QualitySeverity highestSeverity, List<QualityIssue> issues) {}
    public record Dashboard(long pending, long pendingWords, long pendingGrammar, long recentApproved,
                            long recentRejected, long curatedPending, QualitySummary n5Words,
                            QualitySummary n5Grammars) {}
    public record ContentRow(Long id, String slug, ContentType type, String title, String reading, String summary,
                             String jlpt, String sourceRef, ReviewStatus reviewStatus, boolean published,
                             int qualityIssueCount, QualitySeverity qualitySeverity) {}
    public record RawSource(String sourceRef, String noteType, long sourceNoteId, String levelCode,
                            String tags, String fieldNames, String fieldValues) {}
    public record Audit(ReviewStatus previousStatus, ReviewStatus status, String reviewer, String note, Instant reviewedAt) {}
    public record ContentDetail(ContentReviewDetails content, boolean published, List<RawSource> rawSources,
                                List<Audit> audits, QualityAudit qualityAudit) {}
    public record CurationRow(CurationRecordType type, Long id, String grammar, String secondaryGrammar,
                              GrammarRelationType relationType, String summary, String jlpt, String sourceRef,
                              ReviewStatus reviewStatus, boolean published) {}
    public record Choice(Long id, String text, boolean correct, int order) {}
    public record CurationDetail(CurationRow summary, String nuance, String usage, String formation,
                                 String commonMistake, String learnerNote, String keyDifference,
                                 String usageDifference, String commonConfusion, String questionType,
                                 String prompt, String context, String explanation, List<Choice> choices,
                                 List<Audit> audits) {}
    public record ContentPage(Page<ContentRow> page) {}
    public record CurationPage(Page<CurationRow> page) {}
    public record ActionResult(boolean changed, ReviewStatus status, boolean published, String message) {}
}
