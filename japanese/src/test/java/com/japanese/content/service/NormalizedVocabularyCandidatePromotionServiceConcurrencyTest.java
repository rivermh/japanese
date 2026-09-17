package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.importer.NormalizedExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.repository.ContentSourceRepository;
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
 * JLPT-MAX Ticket 4E-1, test requirement 13: the strongest practical integration coverage this
 * environment can give that two concurrent {@code promote(...)} attempts on the <em>same</em>
 * candidate never both succeed - real, independent {@link Thread}s each calling the real
 * {@code @Transactional} {@link NormalizedVocabularyCandidatePromotionService} bean directly (each
 * gets its own transaction, bound per-thread by Spring's {@code TransactionSynchronizationManager} -
 * no special wiring needed for that), racing against the same
 * {@code NormalizedContentCandidateRepository.findByIdAndCandidateTypeForPromotion} {@code PESSIMISTIC_WRITE}
 * lock added by Ticket 4E-0.
 *
 * <p><b>H2-vs-MySQL fidelity limitation (stated explicitly, per the ticket's own instruction not to
 * overclaim what this proves)</b>: this suite runs against H2 (MVStore engine, {@code MODE=MySQL}),
 * not the real MySQL production database. H2's MVStore engine does implement real row-level
 * {@code SELECT ... FOR UPDATE} blocking for a {@code PESSIMISTIC_WRITE} lock - which is what this
 * test actually exercises and can therefore genuinely prove - but its lock-wait/deadlock-detection
 * timing, isolation-level nuances, and exact exception types on contention are not guaranteed
 * identical to MySQL/InnoDB's. What this test proves: given this environment's H2 engine, two
 * concurrently racing transactions against the candidate lock never both create a production
 * {@code ContentItem} for the same candidate. What it does not prove: that MySQL's own InnoDB
 * locking behaves identically under the exact same race (a MySQL-specific concern out of reach of
 * any H2-backed test in this repository).
 */
@SpringBootTest
@ActiveProfiles("sample")
class NormalizedVocabularyCandidatePromotionServiceConcurrencyTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedVocabularyCandidatePromotionService promotion;
    @Autowired NormalizedContentCandidateRepository candidateRepository;
    @Autowired ContentSourceRepository contentSources;
    @Autowired com.japanese.content.repository.LevelRepository levels;
    @Autowired JdbcClient jdbcClient;
    @Autowired ObjectMapper objectMapper;

    private static final String CANONICAL_JLPT_MAX_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";

    /** See {@code NormalizedVocabularyCandidatePromotionServiceTest.seedPrivateApkgNote} for the full rationale. */
    private void seedPrivateApkgNote(String sourceRef, long sourceNoteId) {
        jdbcClient.sql("insert into private_apkg_notes (source_ref, source_file, source_version, source_note_id, "
                        + "model_id, note_type, category, anki_guid, deck_paths, card_metadata, tags, field_names, "
                        + "field_values, normalized_values, audio_reference_count, extracted_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .param(sourceRef).param("fixture.apkg").param("1").param(sourceNoteId).param(1L)
                .param("JLPT MAX덱 어휘").param("VOCABULARY").param("guid-" + sourceNoteId)
                .param("[]").param("{}").param("")
                .param(objectMapper.writeValueAsString(List.of("Word")))
                .param(objectMapper.writeValueAsString(List.of("並")))
                .param("{}").param(0).param(Instant.now())
                .update();
    }

    @Test
    void twoConcurrentPromotionAttemptsOnTheSameCandidateNeverBothSucceed() throws Exception {
        long noteId = 900_200L + (System.nanoTime() % 100_000L);
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

        store.saveVocabulary(new VocabularyNormalizationResult(CANONICAL_JLPT_MAX_SOURCE_REF, noteId, "E-concurrency",
                "並", "なみ", "noun", null, List.of(new NormalizedMeaning(1, "meaning")),
                List.of(new NormalizedExample(1, "meaning", "並の例文です", "なみのよみ", "translation")),
                new NormalizedJlptLevel("N5", "N5", "WordJLPT"), "並", "なみ", Map.of(), List.of(), true));
        NormalizedContentCandidate candidate = candidateRepository
                .findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
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
                    NormalizedVocabularyCandidatePromotionResult result =
                            promotion.promote(NormalizedCandidateType.VOCABULARY, candidateId, expectedNormalizedAt);
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
                    .param(CANONICAL_JLPT_MAX_SOURCE_REF).param("JLPT MAX덱 어휘").param(noteId)
                    .query(Long.class).single();
            assertThat(contentItemCount)
                    .as("exactly one production ContentItem/provenance link must survive for this candidate identity")
                    .isEqualTo(1);

            long recordCount = jdbcClient.sql(
                            "select count(*) from imported_source_records where source_ref = ? and note_type = ? and source_note_id = ?")
                    .param(CANONICAL_JLPT_MAX_SOURCE_REF).param("JLPT MAX덱 어휘").param(noteId)
                    .query(Long.class).single();
            assertThat(recordCount)
                    .as("no duplicate ImportedSourceRecord row for the same identity")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(NormalizedVocabularyCandidatePromotionResult result, RuntimeException failure) {
        static Outcome success(NormalizedVocabularyCandidatePromotionResult result) {
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
