package com.japanese.content.service;

import com.japanese.content.dto.AdminContentReleaseDryRunModels;
import com.japanese.content.dto.AdminContentReleaseDryRunModels.DecisionCounts;
import com.japanese.content.dto.AdminContentReleaseDryRunModels.Filter;
import com.japanese.content.dto.AdminContentReleaseDryRunModels.Result;
import com.japanese.content.dto.AdminContentReleaseDryRunModels.Sample;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.ReviewStatus;
import com.japanese.content.repository.ContentItemRepository;
import jakarta.persistence.criteria.JoinType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only, deterministic preview of a prospective release batch.  The target
 * query is scoped and ordered in the database before content is evaluated.
 */
@Service
public class ContentReleaseDryRunService {
    public static final String GATE_VERSION = "phase1a-v1";
    private static final int BATCH_SIZE = 100;
    private static final int SAMPLE_SIZE = 10;

    private final ContentItemRepository contents;
    private final ContentReleaseGateService releaseGate;

    public ContentReleaseDryRunService(ContentItemRepository contents, ContentReleaseGateService releaseGate) {
        this.contents = contents;
        this.releaseGate = releaseGate;
    }

    @Transactional(readOnly = true)
    public Result run(Filter requested) {
        Filter filter = normalize(requested);
        Specification<ContentItem> specification = specification(filter);
        long total = contents.count(specification);
        long releasable = 0;
        long manual = 0;
        long blocked = 0;
        long duplicate = 0;
        Map<String, Long> issueCounts = new LinkedHashMap<>();
        for (ContentReleaseIssueCode code : ContentReleaseIssueCode.values()) issueCounts.put(code.name(), 0L);
        Map<String, Long> classificationCounts = new LinkedHashMap<>();
        for (ContentReleaseIssueClassification classification : ContentReleaseIssueClassification.values()) classificationCounts.put(classification.name(), 0L);
        Map<String, Long> sourceRightsCounts = sourceRightsCounts();
        List<Sample> allSamples = new ArrayList<>();
        List<Sample> blockedSamples = new ArrayList<>();
        List<Sample> manualSamples = new ArrayList<>();
        List<Long> targetIds = new ArrayList<>();
        List<AdminContentReleaseDryRunModels.ManualTarget> manualTargets = new ArrayList<>();
        Map<String, ContentReleaseGateService.Issue> issueDetails = new LinkedHashMap<>();
        MessageDigest digest = sha256();
        digest.update(GATE_VERSION.getBytes(StandardCharsets.UTF_8));

        int pageNumber = 0;
        Page<ContentItem> page;
        do {
            Pageable pageable = PageRequest.of(pageNumber++, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id"));
            page = contents.findAll(specification, pageable);
            if (page.isEmpty()) {
                continue;
            }
            Map<Long, ContentReleaseGateService.Result> decisions = releaseGate.evaluateAll(page.getContent());
            for (ContentItem item : page.getContent()) {
                targetIds.add(item.getId());
                ContentReleaseGateService.Result decision = decisions.get(item.getId());
                decision.blockers().forEach(i -> issueDetails.putIfAbsent(i.code().name(), i));
                decision.manualReview().forEach(i -> issueDetails.putIfAbsent(i.code().name(), i));
                decision.informational().forEach(i -> issueDetails.putIfAbsent(i.code().name(), i));
                switch (decision.decision()) {
                    case RELEASABLE -> releasable++;
                    case MANUAL_REVIEW_REQUIRED -> manual++;
                    case BLOCKED -> blocked++;
                }
                if (decision.blockers().stream().anyMatch(i -> i.code() == ContentReleaseIssueCode.DUPLICATE_CANDIDATE)
                        || decision.manualReview().stream().anyMatch(i -> i.code() == ContentReleaseIssueCode.DUPLICATE_CANDIDATE)) {
                    duplicate++;
                }
                decision.blockers().forEach(i -> count(classificationCounts, i.classification()));
                decision.manualReview().forEach(i -> count(classificationCounts, i.classification()));
                decision.informational().forEach(i -> count(classificationCounts, i.classification()));
                decision.blockers().forEach(i -> count(issueCounts, i.code().name()));
                decision.manualReview().forEach(i -> count(issueCounts, i.code().name()));
                decision.informational().forEach(i -> count(issueCounts, i.code().name()));
                String rightsKey = decision.sourceRights().rightsStatus() == null
                        ? "MISSING_OR_UNREGISTERED" : decision.sourceRights().rightsStatus().name();
                count(sourceRightsCounts, rightsKey);
                Sample sample = sample(item, decision);
                if (decision.decision() == ContentReleaseDecision.MANUAL_REVIEW_REQUIRED)
                    manualTargets.add(new AdminContentReleaseDryRunModels.ManualTarget(sample, decision.manualReview()));
                if (allSamples.size() < SAMPLE_SIZE) allSamples.add(sample);
                if (decision.decision() == ContentReleaseDecision.BLOCKED && blockedSamples.size() < SAMPLE_SIZE) blockedSamples.add(sample);
                if (decision.decision() == ContentReleaseDecision.MANUAL_REVIEW_REQUIRED && manualSamples.size() < SAMPLE_SIZE) manualSamples.add(sample);
                updateDigest(digest, item, decision);
            }
        } while (page.hasNext());

        return new Result(filter, GATE_VERSION, Instant.now(), total, List.copyOf(targetIds),
                new DecisionCounts(releasable, manual, blocked), issueCounts,
                classificationCounts, sourceRightsCounts, duplicate, allSamples,
                blockedSamples, manualSamples, hex(digest.digest()), List.copyOf(manualTargets), Map.copyOf(issueDetails));
    }

    /** Re-evaluates an already locked, canonical target set for batch execution. */
    @Transactional(readOnly = true)
    public ExecutionSnapshot executionSnapshot(List<ContentItem> orderedItems) {
        MessageDigest digest = sha256();
        digest.update(GATE_VERSION.getBytes(StandardCharsets.UTF_8));
        Map<Long, ContentReleaseGateService.Result> results = new LinkedHashMap<>();
        for (int start = 0; start < orderedItems.size(); start += BATCH_SIZE) {
            List<ContentItem> page = orderedItems.subList(start, Math.min(start + BATCH_SIZE, orderedItems.size()));
            Map<Long, ContentReleaseGateService.Result> evaluated = releaseGate.evaluateAll(page);
            for (ContentItem item : page) {
                ContentReleaseGateService.Result result = evaluated.get(item.getId());
                results.put(item.getId(), result);
                updateDigest(digest, item, result);
            }
        }
        return new ExecutionSnapshot(
                orderedItems.stream().map(ContentItem::getId).toList(),
                hex(digest.digest()), Map.copyOf(results));
    }

    private Filter normalize(Filter filter) {
        if (filter == null) return new Filter(null, null, null, null, null, null);
        String level = clean(filter.level());
        if (level != null) level = level.toUpperCase(Locale.ROOT);
        return new Filter(filter.type(), level, filter.reviewStatus(), filter.published(), clean(filter.source()), filter.sourceRightsStatus());
    }

    private Specification<ContentItem> specification(Filter filter) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (filter.reviewStatus() != null) {
                predicate = filter.reviewStatus() == ReviewStatus.PENDING
                        ? cb.and(predicate, cb.or(cb.equal(root.get("reviewStatus"), ReviewStatus.PENDING), cb.isNull(root.get("reviewStatus"))))
                        : cb.and(predicate, cb.equal(root.get("reviewStatus"), filter.reviewStatus()));
            }
            if (filter.published() != null) predicate = cb.and(predicate, cb.equal(root.get("published"), filter.published()));
            if (filter.type() != null) predicate = cb.and(predicate, cb.equal(root.get("type"), filter.type()));
            if (filter.level() != null) {
                var level = root.join("levels", JoinType.LEFT);
                predicate = cb.and(predicate, cb.equal(level.get("code"), filter.level()), cb.equal(level.get("system"), "JLPT"));
                query.distinct(true);
            }
            if (filter.source() != null) predicate = cb.and(predicate,
                    cb.like(cb.lower(root.get("sourceRef")), "%" + filter.source().toLowerCase(Locale.ROOT) + "%"));
            if (filter.sourceRightsStatus() != null) {
                var source = query.subquery(Long.class);
                var sourceRoot = source.from(ContentSource.class);
                source.select(sourceRoot.get("id")).where(
                        cb.equal(sourceRoot.get("sourceRef"), root.get("sourceRef")),
                        cb.equal(sourceRoot.get("rightsStatus"), cb.literal(filter.sourceRightsStatus())));
                predicate = cb.and(predicate, cb.exists(source));
            }
            return predicate;
        };
    }

    private Sample sample(ContentItem item, ContentReleaseGateService.Result decision) {
        String expression = item.getType() == ContentType.WORD
                ? item.getWord() == null ? null : item.getWord().getExpression()
                : item.getGrammar() == null ? null : item.getGrammar().getPattern();
        String jlpt = item.getLevels().stream().filter(l -> "JLPT".equals(l.getSystem()))
                .map(Level::getCode).sorted().findFirst().orElse(null);
        List<String> codes = new ArrayList<>();
        decision.blockers().forEach(i -> codes.add(i.code().name()));
        decision.manualReview().forEach(i -> codes.add(i.code().name()));
        decision.informational().forEach(i -> codes.add(i.code().name()));
        return new Sample(item.getId(), item.getSlug(), item.getType(), expression, jlpt,
                decision.decision(), List.copyOf(codes), item.getSourceRef(), decision.sourceRights().rightsStatus());
    }

    private void updateDigest(MessageDigest digest, ContentItem item, ContentReleaseGateService.Result decision) {
        String core = item.getType() == ContentType.WORD
                ? item.getWord() == null ? "" : Objects.toString(item.getWord().getExpression(), "") + "|" + Objects.toString(item.getWord().getReading(), "")
                : item.getGrammar() == null ? "" : Objects.toString(item.getGrammar().getPattern(), "") + "|" + Objects.toString(item.getGrammar().getExplanation(), "");
        String issues = decision.blockers().stream().map(i -> i.code().name()).sorted().toList().toString()
                + decision.manualReview().stream().map(i -> i.code().name()).sorted().toList()
                + decision.informational().stream().map(i -> i.code().name()).sorted().toList();
        String value = item.getId() + "|" + item.getSlug() + "|" + item.getType() + "|"
                + item.getReviewStatus() + "|" + item.isPublished() + "|" + item.getSourceRef() + "|"
                + decision.sourceRights().rightsStatus() + "|" + decision.decision() + "|" + core + "|"
                + item.getExamples().size() + "|" + issues + "\n";
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Long> sourceRightsCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ContentSourceRightsStatus status : ContentSourceRightsStatus.values()) counts.put(status.name(), 0L);
        counts.put("MISSING_OR_UNREGISTERED", 0L);
        return counts;
    }

    private static void count(Map<String, Long> counts, Enum<?> key) { count(counts, key.name()); }
    private static void count(Map<String, Long> counts, String key) { counts.put(key, counts.getOrDefault(key, 0L) + 1); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private static String hex(byte[] bytes) { StringBuilder b = new StringBuilder(bytes.length * 2); for (byte value : bytes) b.append(String.format("%02x", value)); return b.toString(); }

    public record ExecutionSnapshot(List<Long> targetIds, String digest,
                                    Map<Long, ContentReleaseGateService.Result> decisions) {}
}
