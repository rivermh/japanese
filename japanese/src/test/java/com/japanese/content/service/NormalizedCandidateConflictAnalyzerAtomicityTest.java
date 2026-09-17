package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.repository.NormalizedCandidateMatchPairRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * JLPT-MAX Ticket 4B independent-review follow-up, item 11: {@link NormalizedCandidateConflictAnalyzer#analyze}
 * deletes a scope's existing pairs/evidence and then regenerates and saves new ones inside one
 * {@code @Transactional} method. This proves that a failure <em>after</em> the deletes have already
 * executed against the database (but before the new pairs commit) rolls back the deletes too,
 * leaving the previous run's pairs/evidence exactly as they were - not a partially-emptied scope.
 *
 * <p>Deliberately has no class-level {@code @Transactional}, matching {@code NormalizedCandidateStoreAtomicityTest}
 * (Ticket 4A): {@code analyze}'s own {@code @Transactional} must be a real, independently-committed-or-rolled-back
 * transaction here, not a participant in a surrounding test transaction that would roll back
 * regardless of whether {@code analyze} itself is atomic.
 *
 * <p>There is no natural DB constraint to trigger a persistence failure specifically at the
 * "regenerate/save" step of a rerun: the analyzer's own delete always removes every existing pair
 * row belonging to the scope being re-analyzed before it inserts anything, so a pre-existing row
 * that could collide with a freshly-generated one can never survive to cause a unique-constraint
 * violation. Instead, this test uses Spring's own {@link MockitoSpyBean} (test-only, this file
 * only - production code is unmodified) to wrap the real {@link NormalizedCandidateMatchPairRepository}
 * bean and make its final {@code saveAll} throw, simulating exactly the kind of failure a real
 * constraint violation or DB outage would cause at that point.
 */
@SpringBootTest
@ActiveProfiles("sample")
class NormalizedCandidateConflictAnalyzerAtomicityTest {

    @Autowired
    NormalizedCandidateStore store;
    @Autowired
    NormalizedCandidateConflictAnalyzer analyzer;
    @MockitoSpyBean
    NormalizedCandidateMatchPairRepository pairRepository;
    @Autowired
    JdbcClient jdbcClient;

    @Test
    void aFailedRerunSaveRollsBackAndPreservesThePreviousPairsAndEvidence() {
        String ref = "conflict-atomicity-" + UUID.randomUUID();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご", "N5", "word"));
        store.saveVocabulary(vocab(ref, 2L, "E1", "語", "ご", "N5", "word"));

        var firstRun = analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(firstRun.pairCount()).isEqualTo(1);

        List<PairRow> pairsBeforeRerun = loadPairs(ref);
        List<EvidenceRow> evidenceBeforeRerun = loadEvidence(ref);
        assertThat(pairsBeforeRerun).hasSize(1);
        assertThat(evidenceBeforeRerun).isNotEmpty();

        Mockito.doThrow(new RuntimeException("simulated persistence failure during rerun"))
                .when(pairRepository).saveAll(Mockito.any());

        assertThatThrownBy(() -> analyzer.analyze(NormalizedCandidateType.VOCABULARY, ref))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated persistence failure during rerun");

        // Read back with a fresh query (the failed call's transaction is gone): if the earlier bulk
        // JPQL deletes had actually committed while the later saveAll failed, this would come back
        // empty/different instead of exactly matching the pre-rerun snapshot.
        assertThat(loadPairs(ref)).isEqualTo(pairsBeforeRerun);
        assertThat(loadEvidence(ref)).isEqualTo(evidenceBeforeRerun);
    }

    private List<PairRow> loadPairs(String ref) {
        return jdbcClient.sql(
                        "select p.id, p.left_candidate_id, p.right_candidate_id, p.assessment "
                                + "from normalized_candidate_match_pairs p "
                                + "join normalized_content_candidates c on c.id = p.left_candidate_id "
                                + "where c.source_ref = ? order by p.id")
                .param(ref)
                .query((rs, rowNum) -> new PairRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4)))
                .list();
    }

    private List<EvidenceRow> loadEvidence(String ref) {
        return jdbcClient.sql(
                        "select e.pair_id, e.position, e.evidence_code, e.field_name, e.detail "
                                + "from normalized_candidate_match_evidence e "
                                + "join normalized_candidate_match_pairs p on p.id = e.pair_id "
                                + "join normalized_content_candidates c on c.id = p.left_candidate_id "
                                + "where c.source_ref = ? order by e.pair_id, e.position")
                .param(ref)
                .query((rs, rowNum) -> new EvidenceRow(rs.getLong(1), rs.getInt(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)))
                .list();
    }

    private record PairRow(long id, long leftId, long rightId, String assessment) {
    }

    private record EvidenceRow(long pairId, int position, String evidenceCode, String fieldName, String detail) {
    }

    private VocabularyNormalizationResult vocab(String ref, long noteId, String entryId, String expression,
            String reading, String level, String meaning) {
        return new VocabularyNormalizationResult(
                ref, noteId, entryId, expression, reading, "noun", null,
                List.of(new NormalizedMeaning(1, meaning)),
                List.of(), new NormalizedJlptLevel(level, level, "WordJLPT"), expression, reading, Map.of(),
                List.of(), true);
    }
}
