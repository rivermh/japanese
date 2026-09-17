package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.importer.GrammarNormalizationIssue;
import com.japanese.content.importer.GrammarNormalizationResult;
import com.japanese.content.importer.GrammarNormalizationWarning;
import com.japanese.content.importer.NormalizedConfusablePattern;
import com.japanese.content.importer.NormalizedGrammarExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Deliberately has no class-level {@code @Transactional}: {@link NormalizedCandidateStore}'s own
 * {@code @Transactional} methods must be exercised as real, independently-committed transactions
 * here, so a failure partway through actually rolls back at the database rather than merely
 * riding along a surrounding test transaction that would roll back anyway.
 */
@SpringBootTest
@ActiveProfiles("sample")
class NormalizedCandidateStoreAtomicityTest {

    @Autowired
    NormalizedCandidateStore store;
    @Autowired
    NormalizedContentCandidateRepository repository;
    @Autowired
    JdbcClient jdbcClient;

    @Test
    void aFailedChildInsertRollsBackTheWholeEnvelope() {
        String ref = "candidate-atomicity-" + UUID.randomUUID();
        long noteId = 1L;
        GrammarNormalizationResult result = new GrammarNormalizationResult(
                ref, noteId, "U-ATOMIC", "〜すぎる",
                new NormalizedGrammarExample(1, "食べすぎる", null, "eat too much"),
                "too much", "casual", "verb-stem + すぎる",
                List.of(new NormalizedConfusablePattern(1, "pattern-a", "explanation-a"),
                        new NormalizedConfusablePattern(1, "pattern-b", "explanation-b")),
                new NormalizedJlptLevel(null, null, null), "⁣", Map.of(), List.of(), true);

        assertThatThrownBy(() -> store.saveGrammar(result)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(repository.findBySourceRefAndSourceNoteIdAndCandidateType(ref, noteId, NormalizedCandidateType.GRAMMAR))
                .isEmpty();
        long envelopeCount = jdbcClient.sql("select count(*) from normalized_content_candidates where source_ref = ?")
                .param(ref).query(Long.class).single();
        assertThat(envelopeCount).isZero();
    }

    @Test
    void aFailedRefreshRollsBackAndPreservesThePreviousSnapshot() {
        String ref = "candidate-atomicity-refresh-" + UUID.randomUUID();
        long noteId = 2L;

        GrammarNormalizationResult original = new GrammarNormalizationResult(
                ref, noteId, "U-ORIGINAL", "〜からには",
                new NormalizedGrammarExample(1, "約束したからには守る", null, "since I promised, I'll keep it"),
                "since/now that", "formal", "verb-plain + からには",
                List.of(new NormalizedConfusablePattern(1, "〜以上は", "more formal register")),
                new NormalizedJlptLevel("N2", "N2", "Level"), "⁣", Map.of("Note", "original extra"),
                List.of(new GrammarNormalizationWarning(GrammarNormalizationIssue.UNKNOWN_EXTRA_FIELD, "original warning")),
                true);
        NormalizedContentCandidate originalSaved = store.saveGrammar(original);
        Long candidateId = originalSaved.getId();

        GrammarNormalizationResult brokenRefresh = new GrammarNormalizationResult(
                ref, noteId, "U-BROKEN", "〜たとたん",
                new NormalizedGrammarExample(1, "帰ったとたん雨が降った", null, "as soon as I got home, it rained"),
                "as soon as", "neutral", "verb-た + とたん",
                // duplicate displayOrder=1 violates uk_normalized_grammar_candidate_confusable_order
                List.of(new NormalizedConfusablePattern(1, "pattern-x", "x"),
                        new NormalizedConfusablePattern(1, "pattern-y", "y")),
                new NormalizedJlptLevel("N2", "N2", "Level"), "⁣", Map.of("Note", "broken extra"),
                List.of(new GrammarNormalizationWarning(GrammarNormalizationIssue.UNKNOWN_EXTRA_FIELD, "broken warning")),
                true);

        assertThatThrownBy(() -> store.saveGrammar(brokenRefresh)).isInstanceOf(DataIntegrityViolationException.class);

        // repository.findById() returns a persistence-context-bound entity whose lazy associations
        // cannot be navigated after the (non-@Transactional) test method's implicit read closes -
        // so the post-rollback snapshot is verified with direct SQL reads instead, matching
        // aFailedChildInsertRollsBackTheWholeEnvelope()'s style above.
        NormalizedContentCandidate afterFailedRefresh = repository.findById(candidateId).orElseThrow();
        assertThat(afterFailedRefresh.getSourceIdentityKey()).isEqualTo("U-ORIGINAL");
        assertThat(afterFailedRefresh.getQualityState()).isEqualTo(NormalizedCandidateQualityState.INFORMATIONAL);

        assertThat(jdbcClient.sql("select unit_id, pattern, meaning_gloss from normalized_grammar_candidates "
                        + "where candidate_id = ?")
                .param(candidateId)
                .query((rs, rowNum) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3)))
                .single()).containsExactly("U-ORIGINAL", "〜からには", "since/now that");

        assertThat(jdbcClient.sql("select pattern from normalized_grammar_candidate_confusable_patterns "
                        + "where candidate_id = ? order by display_order")
                .param(candidateId).query(String.class).list())
                .containsExactly("〜以上は");

        assertThat(jdbcClient.sql("select message from normalized_candidate_warnings where candidate_id = ?")
                .param(candidateId).query(String.class).list())
                .containsExactly("original warning");

        assertThat(jdbcClient.sql("select field_value from normalized_candidate_extra_fields where candidate_id = ?")
                .param(candidateId).query(String.class).list())
                .containsExactly("original extra");
    }
}
