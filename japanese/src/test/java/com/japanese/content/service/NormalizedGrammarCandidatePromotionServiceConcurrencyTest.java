package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.importer.GrammarNormalizationResult;
import com.japanese.content.importer.NormalizedGrammarExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.repository.ContentSourceRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/**
 * JLPT-MAX Ticket 4E-8 hardening (MAJOR 2): the Grammar-side equivalent of
 * {@link NormalizedVocabularyCandidatePromotionServiceConcurrencyTest} - see that class's javadoc for
 * the full H2-vs-MySQL fidelity limitation this test shares. Proves two concurrent
 * {@code promote(...)} attempts on the <em>same</em> Grammar candidate, each a real independent
 * {@link Thread} calling the real {@code @Transactional} {@link NormalizedGrammarCandidatePromotionService}
 * bean directly, never both create a production {@code ContentItem}/{@code Grammar}/
 * {@code ImportedSourceRecord} row for that candidate - the same
 * {@code NormalizedContentCandidateRepository.findByIdAndCandidateTypeForPromotion} {@code PESSIMISTIC_WRITE}
 * lock ordering (candidate lock -> freshness -> readiness -> ImportedSourceRecord lock/provenance ->
 * production writes) that class exercises is what this test races against too.
 */
@SpringBootTest
@ActiveProfiles("sample")
class NormalizedGrammarCandidatePromotionServiceConcurrencyTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedGrammarCandidatePromotionService promotion;
    @Autowired NormalizedContentCandidateRepository candidateRepository;
    @Autowired ContentSourceRepository contentSources;
    @Autowired LevelRepository levels;
    @Autowired JdbcClient jdbcClient;
    @Autowired ObjectMapper objectMapper;

    private static final String CANONICAL_JLPT_MAX_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";
    private static final String GRAMMAR_NOTE_TYPE = "JLPT MAX덱 문법";

    /** See {@code NormalizedGrammarCandidatePromotionServiceTest.seedPrivateApkgNote} for the full rationale. */
    private void seedPrivateApkgNote(String sourceRef, long sourceNoteId) {
        jdbcClient.sql("insert into private_apkg_notes (source_ref, source_file, source_version, source_note_id, "
                        + "model_id, note_type, category, anki_guid, deck_paths, card_metadata, tags, field_names, "
                        + "field_values, normalized_values, audio_reference_count, extracted_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .param(sourceRef).param("fixture.apkg").param("1").param(sourceNoteId).param(1L)
                .param(GRAMMAR_NOTE_TYPE).param("GRAMMAR").param("guid-" + sourceNoteId)
                .param("[]").param("{}").param("")
                .param(objectMapper.writeValueAsString(List.of("UnitID")))
                .param(objectMapper.writeValueAsString(List.of("U-concurrency")))
                .param("{}").param(0).param(Instant.now())
                .update();
    }

    @Test
    void twoConcurrentPromotionAttemptsOnTheSameCandidateNeverBothSucceed() throws Exception {
        long noteId = 900_300L + (System.nanoTime() % 100_000L);
        levels.findBySystemAndCode("JLPT", "N5").orElseGet(() -> levels.save(new Level("JLPT", "N5", "JLPT N5")));
        ContentSource source = contentSources.findBySourceRef(CANONICAL_JLPT_MAX_SOURCE_REF).orElseThrow();
        if (source.getRightsStatus() == ContentSourceRightsStatus.UNKNOWN) {
            source.reviewRights(ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED, "검토 시작", false, null);
        }
        if (source.getRightsStatus() == ContentSourceRightsStatus.MANUAL_REVIEW_REQUIRED) {
            source.reviewRights(ContentSourceRightsStatus.ALLOWED, "허용", false, null);
        }
        assertThat(source.getRightsStatus()).isEqualTo(ContentSourceRightsStatus.ALLOWED);
        contentSources.save(source);

        store.saveGrammar(new GrammarNormalizationResult(CANONICAL_JLPT_MAX_SOURCE_REF, noteId, "U-concurrency",
                "〜べきだ", new NormalizedGrammarExample(1, "前文です", null, "번역"),
                "gloss", "nuance", "접속", List.of(), new NormalizedJlptLevel("N5", "N5", "Level"), null,
                Map.of(), List.of(), true));
        NormalizedContentCandidate candidate = candidateRepository
                .findByCandidateType(NormalizedCandidateType.GRAMMAR).stream()
                .filter(c -> c.getSourceRef().equals(CANONICAL_JLPT_MAX_SOURCE_REF) && c.getSourceNoteId() == noteId)
                .findFirst().orElseThrow();
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, noteId);
        Long candidateId = candidate.getId();
        Instant expectedNormalizedAt = candidate.getNormalizedAt();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Outcome> attempt = () -> {
                bothReady.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    NormalizedGrammarCandidatePromotionResult result =
                            promotion.promote(NormalizedCandidateType.GRAMMAR, candidateId, expectedNormalizedAt);
                    return Outcome.success(result);
                } catch (RuntimeException e) {
                    return Outcome.failure(e);
                }
            };

            Future<Outcome> first = pool.submit(attempt);
            Future<Outcome> second = pool.submit(attempt);
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();

            Outcome a = first.get(30, TimeUnit.SECONDS);
            Outcome b = second.get(30, TimeUnit.SECONDS);

            long successCount = List.of(a, b).stream().filter(Outcome::succeeded).count();
            assertThat(successCount)
                    .as("exactly one of the two concurrent promotion attempts must succeed - outcomes: %s, %s", a, b)
                    .isEqualTo(1);

            long contentItemCount = jdbcClient.sql(
                            "select count(*) from imported_source_records r "
                                    + "join content_items c on c.id = r.content_item_id "
                                    + "where r.source_ref = ? and r.note_type = ? and r.source_note_id = ?")
                    .param(CANONICAL_JLPT_MAX_SOURCE_REF).param(GRAMMAR_NOTE_TYPE).param(noteId)
                    .query(Long.class).single();
            assertThat(contentItemCount)
                    .as("exactly one production ContentItem/provenance link must survive for this candidate identity")
                    .isEqualTo(1);

            long recordCount = jdbcClient.sql(
                            "select count(*) from imported_source_records where source_ref = ? and note_type = ? and source_note_id = ?")
                    .param(CANONICAL_JLPT_MAX_SOURCE_REF).param(GRAMMAR_NOTE_TYPE).param(noteId)
                    .query(Long.class).single();
            assertThat(recordCount)
                    .as("no duplicate ImportedSourceRecord row for the same identity")
                    .isEqualTo(1);

            long grammarCount = jdbcClient.sql(
                            "select count(*) from grammars g join content_items c on c.id = g.content_item_id "
                                    + "join imported_source_records r on r.content_item_id = c.id "
                                    + "where r.source_ref = ? and r.note_type = ? and r.source_note_id = ?")
                    .param(CANONICAL_JLPT_MAX_SOURCE_REF).param(GRAMMAR_NOTE_TYPE).param(noteId)
                    .query(Long.class).single();
            assertThat(grammarCount)
                    .as("no duplicate production Grammar row for the same candidate")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(NormalizedGrammarCandidatePromotionResult result, RuntimeException failure) {
        static Outcome success(NormalizedGrammarCandidatePromotionResult result) {
            return new Outcome(result, null);
        }

        static Outcome failure(RuntimeException failure) {
            return new Outcome(null, failure);
        }

        boolean succeeded() {
            return result != null;
        }

        @Override
        public String toString() {
            return succeeded() ? "SUCCESS(" + result + ")" : "FAILURE(" + failure + ")";
        }
    }
}
