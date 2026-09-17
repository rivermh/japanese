package com.japanese.content.service;

import static com.japanese.content.entity.NormalizedCandidateMatchAssessment.CONFLICT;
import static com.japanese.content.entity.NormalizedCandidateMatchAssessment.EXACT_DUPLICATE;
import static com.japanese.content.entity.NormalizedCandidateMatchAssessment.POSSIBLE_DUPLICATE;
import static com.japanese.content.entity.NormalizedCandidateMatchAssessment.UNIQUE;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_CONNECTION;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_ENTRY_ID;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_EXPRESSION;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_LEVEL;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_MEANING;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_MEANING_GLOSS;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_NUANCE;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_PART_OF_SPEECH;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_PATTERN;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_READING;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.DIFFERENT_UNIT_ID;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_CONNECTION;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_ENTRY_ID;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_EXPRESSION;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_LEVEL;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_MEANING;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_MEANING_GLOSS;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_NUANCE;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_PART_OF_SPEECH;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_PATTERN;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_READING;
import static com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode.SAME_UNIT_ID;

import com.japanese.content.entity.NormalizedCandidateMatchAssessment;
import com.japanese.content.entity.NormalizedCandidateMatchEvidence;
import com.japanese.content.entity.NormalizedCandidateMatchEvidenceCode;
import com.japanese.content.entity.NormalizedCandidateMatchPair;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.NormalizedGrammarCandidateDetail;
import com.japanese.content.entity.NormalizedVocabularyCandidateDetail;
import com.japanese.content.entity.NormalizedVocabularyCandidateMeaning;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import jakarta.persistence.EntityManager;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4B: detects dedup/conflict relationships among the private
 * {@link NormalizedContentCandidate} snapshots persisted by Ticket 4A's {@code NormalizedCandidateStore},
 * within one {@code (candidateType, sourceRef)} scope, and persists the result as
 * {@link NormalizedCandidateMatchPair}/{@link NormalizedCandidateMatchEvidence} rows.
 *
 * <p><b>Not in scope</b> (see IMPLEMENTATION_LOG.md for the full list): this never merges, deletes,
 * or picks a canonical candidate; never creates/updates/reads any production entity
 * ({@code ContentItem}/{@code Word}/{@code Grammar}/{@code GrammarEnrichment}/{@code GrammarRelation}/
 * {@code GrammarComparison}); has no reviewer/approval concept. It is a read-of-candidates,
 * write-of-private-analysis-only operation.
 *
 * <p><b>Scope</b>: comparisons only ever happen between two candidates that share both
 * {@code candidateType} and {@code sourceRef} - {@link NormalizedCandidateMatchPair}'s constructor
 * additionally hard-rejects a cross-{@code candidateType} pair, and {@link #analyze} never even
 * builds a cross-{@code sourceRef} candidate pool to begin with (JLPT-MAX Ticket 4B step 16: source-native
 * identity - EntryID/UnitID - is only meaningful within the source that assigned it).
 *
 * <p><b>Blocking</b> (JLPT-MAX Ticket 4B step 28/29 profiling against the real v2.1.1 deck): candidates
 * are only ever compared when they share a non-blank {@code entryId}/{@code unitId}, or a non-blank
 * normalized {@code expression+reading} (Vocabulary) / {@code pattern} (Grammar) - never on
 * expression-only or reading-only matches (profiled at 14/455 groups respectively on the real deck;
 * both are common, unrelated-word homograph/homophone noise, not identity signals - JLPT-MAX Ticket 4B
 * step 10's explicit warning). On the real v2.1.1 deck this keeps the compared-pair count at 33
 * total (1 Vocabulary + 32 Grammar) against 10,238 candidates, so no cluster/union-find structure is
 * needed - see IMPLEMENTATION_LOG.md for the full profiling numbers.
 *
 * <p><b>Classification</b> is symmetric for both domains: same identity key (entryId/unitId) with a
 * differing core field (expression/reading, or pattern) is {@link NormalizedCandidateMatchAssessment#CONFLICT}
 * (the source data disagrees with its own identity claim); a matching core field (expression+reading,
 * or pattern) with every secondary field also matching is {@link NormalizedCandidateMatchAssessment#EXACT_DUPLICATE};
 * a matching core field with any secondary field differing is {@link NormalizedCandidateMatchAssessment#POSSIBLE_DUPLICATE}.
 * Real-deck profiling found {@code entryId}/{@code unitId} to be 100% unique (so real
 * {@code CONFLICT} never occurs today - only via the synthetic tests) and found that a shared
 * Grammar {@code pattern} is essentially never a true duplicate (0 of 30 same-pattern-and-level
 * pairs matched on every secondary field) - real particles like {@code で}/{@code に}/{@code の}
 * legitimately share a written pattern across several distinct grammar points, which is exactly why
 * this ticket flags rather than merges.
 *
 * <p><b>Idempotency/rerun</b>: re-running {@link #analyze} for the same scope deletes every existing
 * pair (and its evidence, via cascade on the Java side / explicit bulk delete on the SQL side) in
 * that scope first, then re-derives pairs from the current candidate snapshot - a plain
 * delete-then-regenerate, not versioned or diffed. No separate "analysis run" table is kept;
 * {@code generatedAt} on each pair row is the only staleness signal, by design (JLPT-MAX Ticket 4B
 * step 17).
 */
@Service
public class NormalizedCandidateConflictAnalyzer {

    private final NormalizedContentCandidateRepository candidateRepository;
    private final NormalizedCandidateMatchPairRepository pairRepository;
    private final EntityManager entityManager;

    public NormalizedCandidateConflictAnalyzer(NormalizedContentCandidateRepository candidateRepository,
            NormalizedCandidateMatchPairRepository pairRepository, EntityManager entityManager) {
        this.candidateRepository = candidateRepository;
        this.pairRepository = pairRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public NormalizedCandidateAnalysisSummary analyze(NormalizedCandidateType candidateType, String sourceRef) {
        List<NormalizedContentCandidate> candidates = candidateRepository
                .findByCandidateTypeAndSourceRef(candidateType, sourceRef).stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();

        deleteExistingPairs(candidateType, sourceRef);

        Set<PairKey> pairKeys = candidateType == NormalizedCandidateType.VOCABULARY
                ? blockVocabulary(candidates)
                : blockGrammar(candidates);

        Map<Long, NormalizedContentCandidate> byId = candidates.stream()
                .collect(Collectors.toMap(NormalizedContentCandidate::getId, c -> c));

        Instant generatedAt = Instant.now();
        List<NormalizedCandidateMatchPair> pairs = new ArrayList<>();
        Set<Long> involved = new HashSet<>();
        int exact = 0;
        int possible = 0;
        int conflict = 0;
        for (PairKey key : pairKeys) {
            NormalizedContentCandidate a = byId.get(key.lowerId());
            NormalizedContentCandidate b = byId.get(key.higherId());
            PairOutcome outcome = candidateType == NormalizedCandidateType.VOCABULARY
                    ? compareVocabulary(a, b)
                    : compareGrammar(a, b);
            if (outcome.assessment() == UNIQUE) {
                continue;
            }
            NormalizedCandidateMatchPair pair = new NormalizedCandidateMatchPair(a, b, outcome.assessment(), generatedAt);
            int position = 1;
            for (EvidenceItem item : outcome.evidence()) {
                pair.addEvidence(new NormalizedCandidateMatchEvidence(position++, item.code(), item.fieldName(), item.detail()));
            }
            pairs.add(pair);
            involved.add(a.getId());
            involved.add(b.getId());
            switch (outcome.assessment()) {
                case EXACT_DUPLICATE -> exact++;
                case POSSIBLE_DUPLICATE -> possible++;
                case CONFLICT -> conflict++;
                default -> throw new IllegalStateException("unreachable: " + outcome.assessment());
            }
        }
        pairRepository.saveAll(pairs);

        return new NormalizedCandidateAnalysisSummary(candidateType, sourceRef, candidates.size(),
                candidates.size() - involved.size(), exact, possible, conflict, pairs.size());
    }

    private void deleteExistingPairs(NormalizedCandidateType candidateType, String sourceRef) {
        entityManager.createQuery(
                        "delete from NormalizedCandidateMatchEvidence e where e.pair.id in ("
                                + "select p.id from NormalizedCandidateMatchPair p "
                                + "where p.leftCandidate.candidateType = :type and p.leftCandidate.sourceRef = :sourceRef)")
                .setParameter("type", candidateType)
                .setParameter("sourceRef", sourceRef)
                .executeUpdate();
        entityManager.createQuery(
                        "delete from NormalizedCandidateMatchPair p "
                                + "where p.leftCandidate.candidateType = :type and p.leftCandidate.sourceRef = :sourceRef")
                .setParameter("type", candidateType)
                .setParameter("sourceRef", sourceRef)
                .executeUpdate();
    }

    // ===================================================================================
    // Blocking
    // ===================================================================================

    private Set<PairKey> blockVocabulary(List<NormalizedContentCandidate> candidates) {
        Set<PairKey> keys = new LinkedHashSet<>();
        addBlockingPairs(candidates, c -> entryIdKey(c.getVocabularyDetail()), keys);
        addBlockingPairs(candidates, c -> {
            NormalizedVocabularyCandidateDetail d = c.getVocabularyDetail();
            String expr = vocabExpressionKey(d);
            String reading = vocabReadingKey(d);
            return expr == null || reading == null ? null : expr + " " + reading;
        }, keys);
        return keys;
    }

    private Set<PairKey> blockGrammar(List<NormalizedContentCandidate> candidates) {
        Set<PairKey> keys = new LinkedHashSet<>();
        addBlockingPairs(candidates, c -> grammarUnitIdKey(c.getGrammarDetail()), keys);
        addBlockingPairs(candidates, c -> grammarPatternKey(c.getGrammarDetail()), keys);
        return keys;
    }

    private void addBlockingPairs(List<NormalizedContentCandidate> candidates,
            Function<NormalizedContentCandidate, String> keyFunction, Set<PairKey> out) {
        Map<String, List<NormalizedContentCandidate>> groups = new LinkedHashMap<>();
        for (NormalizedContentCandidate candidate : candidates) {
            String key = keyFunction.apply(candidate);
            if (key != null) {
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(candidate);
            }
        }
        for (List<NormalizedContentCandidate> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    out.add(PairKey.of(group.get(i).getId(), group.get(j).getId()));
                }
            }
        }
    }

    // ===================================================================================
    // Vocabulary comparison
    // ===================================================================================

    /**
     * Secondary EXACT fields are {@code meanings}, {@code level}, and {@code partOfSpeech} only.
     * Deliberately excluded (independent-review follow-up, item 7): pitch accent
     * ({@code pitchAccentTerminalStates}/{@code pitchAccentMora}) is pronunciation/source metadata,
     * not a lexical-identity signal, so a pitch-accent difference alone never breaks EXACT; example
     * sentences are illustrative, not identity, so an example difference alone never breaks EXACT
     * either. See {@link #meaningsKey} for why meaning <em>order</em> specifically is ignored within
     * the {@code meanings} comparison.
     */
    private PairOutcome compareVocabulary(NormalizedContentCandidate a, NormalizedContentCandidate b) {
        NormalizedVocabularyCandidateDetail da = a.getVocabularyDetail();
        NormalizedVocabularyCandidateDetail db = b.getVocabularyDetail();

        String entryIdA = entryIdKey(da);
        String entryIdB = entryIdKey(db);
        boolean entryIdBothPresent = entryIdA != null && entryIdB != null;
        boolean entryIdBothAbsent = entryIdA == null && entryIdB == null;
        boolean sameEntryId = entryIdBothPresent && entryIdA.equals(entryIdB);
        boolean entryIdDisagrees = entryIdBothPresent && !entryIdA.equals(entryIdB);
        String exprA = vocabExpressionKey(da);
        String exprB = vocabExpressionKey(db);
        boolean sameExpr = exprA != null && exprA.equals(exprB);
        String readingA = vocabReadingKey(da);
        String readingB = vocabReadingKey(db);
        boolean sameReading = readingA != null && readingA.equals(readingB);

        List<EvidenceItem> evidence = new ArrayList<>();

        if (sameEntryId && !(sameExpr && sameReading)) {
            evidence.add(new EvidenceItem(SAME_ENTRY_ID, "entryId", entryIdA));
            if (!sameExpr) {
                evidence.add(new EvidenceItem(DIFFERENT_EXPRESSION, "expression", diff(exprA, exprB)));
            }
            if (!sameReading) {
                evidence.add(new EvidenceItem(DIFFERENT_READING, "reading", diff(readingA, readingB)));
            }
            return new PairOutcome(CONFLICT, evidence);
        }

        if (sameExpr && sameReading) {
            evidence.add(new EvidenceItem(SAME_EXPRESSION, "expression", exprA));
            evidence.add(new EvidenceItem(SAME_READING, "reading", readingA));
            if (sameEntryId) {
                evidence.add(new EvidenceItem(SAME_ENTRY_ID, "entryId", entryIdA));
            } else if (!entryIdBothAbsent) {
                // One EntryID present and the other absent still gets a DIFFERENT_ENTRY_ID row (the
                // source only supplied identity information on one side); both absent means neither
                // side made an identity claim at all, so no identity evidence row is emitted - a
                // fabricated "DIFFERENT_ENTRY_ID a=∅ b=∅" would misleadingly read as a real
                // disagreement to a future Ticket 4C human reviewer. Grammar's UnitID mirrors this.
                evidence.add(new EvidenceItem(DIFFERENT_ENTRY_ID, "entryId", diff(entryIdA, entryIdB)));
            }

            String meaningsA = meaningsKey(a);
            String meaningsB = meaningsKey(b);
            boolean sameMeanings = meaningsA.equals(meaningsB);
            String levelA = comparisonKey(da.getLevelCode());
            String levelB = comparisonKey(db.getLevelCode());
            boolean sameLevel = Objects.equals(levelA, levelB);
            // partOfSpeech is a secondary EXACT field, not a mere presentation detail: two entries
            // sharing expression+reading+meaning+level but differing noun/verb/adjective etc. are a
            // real lexical distinction, not a duplicate (independent-review follow-up, item 7A).
            String partOfSpeechA = comparisonKey(da.getPartOfSpeech());
            String partOfSpeechB = comparisonKey(db.getPartOfSpeech());
            boolean samePartOfSpeech = Objects.equals(partOfSpeechA, partOfSpeechB);

            evidence.add(sameMeanings
                    ? new EvidenceItem(SAME_MEANING, "meanings", null)
                    : new EvidenceItem(DIFFERENT_MEANING, "meanings", diff(meaningsA, meaningsB)));
            evidence.add(sameLevel
                    ? new EvidenceItem(SAME_LEVEL, "level", levelA)
                    : new EvidenceItem(DIFFERENT_LEVEL, "level", diff(levelA, levelB)));
            evidence.add(samePartOfSpeech
                    ? new EvidenceItem(SAME_PART_OF_SPEECH, "partOfSpeech", partOfSpeechA)
                    : new EvidenceItem(DIFFERENT_PART_OF_SPEECH, "partOfSpeech", diff(partOfSpeechA, partOfSpeechB)));

            boolean exact = !entryIdDisagrees && sameMeanings && sameLevel && samePartOfSpeech;
            return new PairOutcome(exact ? EXACT_DUPLICATE : POSSIBLE_DUPLICATE, evidence);
        }

        return new PairOutcome(UNIQUE, List.of());
    }

    private static String entryIdKey(NormalizedVocabularyCandidateDetail detail) {
        return detail == null ? null : comparisonKey(detail.getEntryId());
    }

    private static String vocabExpressionKey(NormalizedVocabularyCandidateDetail detail) {
        if (detail == null) {
            return null;
        }
        String preferred = detail.getNormalizedSearchExpression();
        return comparisonKey(preferred != null ? preferred : detail.getExpression());
    }

    private static String vocabReadingKey(NormalizedVocabularyCandidateDetail detail) {
        if (detail == null) {
            return null;
        }
        String preferred = detail.getNormalizedSearchReading();
        return comparisonKey(preferred != null ? preferred : detail.getReading());
    }

    /**
     * Sense <em>order</em> is deliberately ignored - two candidates with the same set of meaning
     * texts in a different sense order are still {@code sameMeanings} - because sense ordering is a
     * presentation/authoring detail, not part of this ticket's identity signal (independent-review
     * follow-up, item 7D). This is sorting a {@link List}'s elements, not deduplicating a
     * {@link Set}: a meaning text repeated twice still appears twice in the sorted, joined key, so
     * {@code ["word","word"]} and {@code ["word"]} still compare as different.
     */
    private static String meaningsKey(NormalizedContentCandidate candidate) {
        return candidate.getVocabularyMeanings().stream()
                .map(NormalizedVocabularyCandidateMeaning::getMeaningText)
                .map(NormalizedCandidateConflictAnalyzer::comparisonKey)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.joining("|"));
    }

    // ===================================================================================
    // Grammar comparison
    // ===================================================================================

    /**
     * Secondary EXACT fields are {@code level}, {@code meaningGloss}, {@code connection}, and
     * {@code nuance} only. Deliberately excluded (independent-review follow-up, item 8):
     * {@code frontExample}, {@code rawKind}, and {@code confusablePatterns} - excluding them is
     * existing, unchanged behavior, kept as-is here. EXACT_DUPLICATE therefore means "semantic
     * identity over this specific field set", not "byte-identical normalized snapshot" - two
     * candidates can legitimately differ in front example, raw kind, or confusable-pattern list and
     * still be EXACT_DUPLICATE.
     */
    private PairOutcome compareGrammar(NormalizedContentCandidate a, NormalizedContentCandidate b) {
        NormalizedGrammarCandidateDetail da = a.getGrammarDetail();
        NormalizedGrammarCandidateDetail db = b.getGrammarDetail();

        String unitIdA = comparisonKey(da == null ? null : da.getUnitId());
        String unitIdB = comparisonKey(db == null ? null : db.getUnitId());
        boolean unitIdBothPresent = unitIdA != null && unitIdB != null;
        boolean unitIdBothAbsent = unitIdA == null && unitIdB == null;
        boolean sameUnitId = unitIdBothPresent && unitIdA.equals(unitIdB);
        boolean unitIdDisagrees = unitIdBothPresent && !unitIdA.equals(unitIdB);
        String patternA = comparisonKey(da == null ? null : da.getPattern());
        String patternB = comparisonKey(db == null ? null : db.getPattern());
        boolean samePattern = patternA != null && patternA.equals(patternB);

        List<EvidenceItem> evidence = new ArrayList<>();

        if (sameUnitId && !samePattern) {
            evidence.add(new EvidenceItem(SAME_UNIT_ID, "unitId", unitIdA));
            evidence.add(new EvidenceItem(DIFFERENT_PATTERN, "pattern", diff(patternA, patternB)));
            return new PairOutcome(CONFLICT, evidence);
        }

        if (samePattern) {
            evidence.add(new EvidenceItem(SAME_PATTERN, "pattern", patternA));
            if (sameUnitId) {
                evidence.add(new EvidenceItem(SAME_UNIT_ID, "unitId", unitIdA));
            } else if (!unitIdBothAbsent) {
                // Mirrors Vocabulary's EntryID handling above: both UnitIDs absent means neither side
                // made an identity claim, so no identity evidence row is emitted rather than a
                // misleading "DIFFERENT_UNIT_ID a=∅ b=∅".
                evidence.add(new EvidenceItem(DIFFERENT_UNIT_ID, "unitId", diff(unitIdA, unitIdB)));
            }

            String levelA = comparisonKey(da.getLevelCode());
            String levelB = comparisonKey(db.getLevelCode());
            boolean sameLevel = Objects.equals(levelA, levelB);
            String glossA = comparisonKey(da.getMeaningGloss());
            String glossB = comparisonKey(db.getMeaningGloss());
            boolean sameGloss = Objects.equals(glossA, glossB);
            String connectionA = comparisonKey(da.getConnectionForm());
            String connectionB = comparisonKey(db.getConnectionForm());
            boolean sameConnection = Objects.equals(connectionA, connectionB);
            String nuanceA = comparisonKey(da.getNuance());
            String nuanceB = comparisonKey(db.getNuance());
            boolean sameNuance = Objects.equals(nuanceA, nuanceB);

            evidence.add(sameLevel
                    ? new EvidenceItem(SAME_LEVEL, "level", levelA)
                    : new EvidenceItem(DIFFERENT_LEVEL, "level", diff(levelA, levelB)));
            evidence.add(sameGloss
                    ? new EvidenceItem(SAME_MEANING_GLOSS, "meaningGloss", null)
                    : new EvidenceItem(DIFFERENT_MEANING_GLOSS, "meaningGloss", diff(glossA, glossB)));
            evidence.add(sameConnection
                    ? new EvidenceItem(SAME_CONNECTION, "connection", null)
                    : new EvidenceItem(DIFFERENT_CONNECTION, "connection", diff(connectionA, connectionB)));
            evidence.add(sameNuance
                    ? new EvidenceItem(SAME_NUANCE, "nuance", null)
                    : new EvidenceItem(DIFFERENT_NUANCE, "nuance", diff(nuanceA, nuanceB)));

            boolean exact = !unitIdDisagrees && sameLevel && sameGloss && sameConnection && sameNuance;
            return new PairOutcome(exact ? EXACT_DUPLICATE : POSSIBLE_DUPLICATE, evidence);
        }

        return new PairOutcome(UNIQUE, List.of());
    }

    /**
     * Null-safe like {@link #entryIdKey}/{@link #vocabExpressionKey} above: a well-formed candidate
     * always has a non-null {@code grammarDetail} (persisted by {@code NormalizedCandidateStore}),
     * but the DB has no reverse FK forcing that, so a malformed/legacy candidate row could exist
     * without one. Returning {@code null} here simply keeps such a candidate out of every blocking
     * group (it ends up {@code UNIQUE}) instead of throwing a {@link NullPointerException} that
     * would abort the whole scope's analysis over one bad row.
     */
    private static String grammarUnitIdKey(NormalizedGrammarCandidateDetail detail) {
        return detail == null ? null : comparisonKey(detail.getUnitId());
    }

    private static String grammarPatternKey(NormalizedGrammarCandidateDetail detail) {
        return detail == null ? null : comparisonKey(detail.getPattern());
    }

    // ===================================================================================
    // Shared helpers
    // ===================================================================================

    /**
     * Comparison-only normalization (NFKC unicode form + trim), deliberately conservative -
     * whitespace/case/punctuation inside the value are left untouched so a Japanese grammar
     * pattern's particles/symbols/brackets/{@code ～} are never altered (JLPT-MAX Ticket 4B step 9).
     * Never mutates the source entity field it is called on; only used to build in-memory
     * comparison keys.
     */
    private static String comparisonKey(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * The max length of one side's rendered value inside {@link #diff}, chosen so the combined
     * {@code "a=<side> b=<side>"} string can never reach the {@code normalized_candidate_match_evidence.detail}
     * column's {@code varchar(2000)} limit (independent-review follow-up, item 1/2 - MAJOR): worst
     * case is {@code 2 + 900 + 3 + 900 = 1805} chars, comfortably under 2000 even before accounting
     * for the fact both sides being simultaneously at the cap is itself a rare edge case.
     * {@code meaningGloss}/{@code connection} are {@code varchar(2000)} each and {@code meanings}
     * (an aggregate join of {@code longtext} meaning rows) and {@code nuance} ({@code longtext}) are
     * both unbounded at the source-column level, so without this cap a normal (non-adversarial)
     * candidate pair can legitimately overflow the evidence column and fail to persist.
     */
    private static final int EVIDENCE_EXCERPT_MAX_LENGTH = 900;
    private static final String EVIDENCE_TRUNCATION_MARKER = "…[truncated]";

    /**
     * Bounds one comparison value for human-readable evidence <em>display</em> only - never call
     * this on a value used to decide {@code sameXxx}/{@code exact}, which must always compare the
     * full, untruncated semantic value (see {@link #diff}'s callers). Truncation is safe against
     * cutting a UTF-16 surrogate pair in half, and a truncated result always carries an explicit,
     * human-visible marker so a Ticket 4C reviewer never mistakes a truncated excerpt for the whole
     * value.
     */
    private static String excerpt(String value) {
        if (value == null) {
            return "∅";
        }
        if (value.length() <= EVIDENCE_EXCERPT_MAX_LENGTH) {
            return value;
        }
        int cut = EVIDENCE_EXCERPT_MAX_LENGTH - EVIDENCE_TRUNCATION_MARKER.length();
        if (cut > 0 && Character.isHighSurrogate(value.charAt(cut - 1))) {
            cut--;
        }
        return value.substring(0, cut) + EVIDENCE_TRUNCATION_MARKER;
    }

    private static String diff(String a, String b) {
        return "a=" + excerpt(a) + " b=" + excerpt(b);
    }

    private record PairKey(long lowerId, long higherId) {
        static PairKey of(long x, long y) {
            return x < y ? new PairKey(x, y) : new PairKey(y, x);
        }
    }

    private record EvidenceItem(NormalizedCandidateMatchEvidenceCode code, String fieldName, String detail) {
    }

    private record PairOutcome(NormalizedCandidateMatchAssessment assessment, List<EvidenceItem> evidence) {
    }
}
