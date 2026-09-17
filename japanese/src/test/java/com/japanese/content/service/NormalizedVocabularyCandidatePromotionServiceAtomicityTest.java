package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

/**
 * JLPT-MAX Ticket 4E-1, test requirement 12: proves a failure that happens <em>after</em> the
 * production {@code ContentItem}/{@code Word}/{@code Meaning}/{@code Example} rows have already been
 * flushed to the database - but before the whole {@code promote(...)} transaction commits - rolls
 * back everything, leaving no partial production or provenance row behind.
 *
 * <p>Mirrors {@code NormalizedCandidateConflictAnalyzerAtomicityTest}'s own approach exactly: there is
 * no natural DB constraint that reliably fails specifically at the provenance-linking step without
 * also being reachable some other, less interesting way (e.g. simply re-promoting an already-promoted
 * candidate, which is rejected before any write - see requirement 9's coverage in
 * {@link NormalizedVocabularyCandidatePromotionServiceTest}). Instead this test uses Spring's own
 * {@link MockitoSpyBean} (test-only, this file only - production code is unmodified) to wrap the real
 * {@link ImportedSourceRecordRepository} bean and make its {@code save} throw, simulating exactly the
 * kind of failure a real constraint violation or DB outage would cause at that point - after
 * {@code ContentItemRepository.save(...)} (and its cascaded {@code Word}/{@code Meaning}/
 * {@code Example} inserts) has already executed against the database inside the same still-open
 * transaction.
 *
 * <p>Deliberately has no class-level {@code @Transactional}, matching
 * {@code NormalizedCandidateConflictAnalyzerAtomicityTest}: {@code promote(...)}'s own
 * {@code @Transactional} must be a real, independently-committed-or-rolled-back transaction here, not
 * a participant in a surrounding test transaction that would roll back regardless of whether
 * {@code promote(...)} itself is atomic.
 */
@SpringBootTest
@ActiveProfiles("sample")
class NormalizedVocabularyCandidatePromotionServiceAtomicityTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedVocabularyCandidatePromotionService promotion;
    @Autowired NormalizedContentCandidateRepository candidateRepository;
    @Autowired ContentSourceRepository contentSources;
    @Autowired LevelRepository levels;
    @MockitoSpyBean ImportedSourceRecordRepository importedSourceRecordRepository;
    @Autowired JdbcClient jdbcClient;
    @Autowired ObjectMapper objectMapper;

    /** Matches {@code ApkgVocabularyImporter.SOURCE_REF} / {@code NormalizedVocabularyMeaningLanguagePolicy}. */
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
                .param(objectMapper.writeValueAsString(List.of("語")))
                .param("{}").param(0).param(Instant.now())
                .update();
    }

    @Test
    void aFailedProvenanceLinkSaveRollsBackTheAlreadyFlushedContentItemToo() {
        // Must be the canonical sourceRef: NormalizedVocabularyMeaningLanguagePolicy only resolves a
        // Meaning.languageTag for this exact sourceRef, and this test needs the candidate to reach
        // this class's write phase (past readiness) before the spy below throws - a rejection at the
        // readiness check, before any write, would prove nothing about atomicity.
        long noteId = 900_100L + (System.nanoTime() % 100_000L);
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

        store.saveVocabulary(new VocabularyNormalizationResult(CANONICAL_JLPT_MAX_SOURCE_REF, noteId, "E-atomicity",
                "語", "ご", "noun", null, List.of(new NormalizedMeaning(1, "meaning")),
                List.of(new NormalizedExample(1, "meaning", "語の例文です", "ごのよみ", "translation")),
                new NormalizedJlptLevel("N5", "N5", "WordJLPT"), "語", "ご", Map.of(), List.of(), true));
        NormalizedContentCandidate candidate = candidateRepository
                .findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(c -> c.getSourceRef().equals(CANONICAL_JLPT_MAX_SOURCE_REF) && c.getSourceNoteId() == noteId)
                .findFirst().orElseThrow();
        seedPrivateApkgNote(CANONICAL_JLPT_MAX_SOURCE_REF, noteId);

        // Snapshot counts only now (test setup above is done) - this single test method runs on one
        // thread with no concurrent writer, so a before/after delta across the one promote() call
        // below faithfully isolates exactly what that call did or did not commit.
        long contentItemsBefore = jdbcClient.sql("select count(*) from content_items").query(Long.class).single();
        long wordsBefore = jdbcClient.sql("select count(*) from words").query(Long.class).single();
        long meaningsBefore = jdbcClient.sql("select count(*) from meanings").query(Long.class).single();
        long examplesBefore = jdbcClient.sql("select count(*) from examples").query(Long.class).single();
        long recordsBefore = jdbcClient.sql("select count(*) from imported_source_records").query(Long.class).single();

        Mockito.doThrow(new RuntimeException("simulated persistence failure while linking provenance"))
                .when(importedSourceRecordRepository).save(Mockito.any());

        assertThatThrownBy(() -> promotion.promote(NormalizedCandidateType.VOCABULARY, candidate.getId(),
                candidate.getNormalizedAt()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated persistence failure while linking provenance");

        // Read back with fresh queries (the failed call's transaction is gone): if the ContentItem/
        // Word/Meaning/Example inserts that happened earlier in that same transaction had actually
        // survived while only the later ImportedSourceRecord save failed, these counts would be higher
        // than the snapshot above instead of exactly matching it.
        assertThat(jdbcClient.sql("select count(*) from content_items").query(Long.class).single())
                .isEqualTo(contentItemsBefore);
        assertThat(jdbcClient.sql("select count(*) from words").query(Long.class).single()).isEqualTo(wordsBefore);
        assertThat(jdbcClient.sql("select count(*) from meanings").query(Long.class).single()).isEqualTo(meaningsBefore);
        assertThat(jdbcClient.sql("select count(*) from examples").query(Long.class).single()).isEqualTo(examplesBefore);
        assertThat(jdbcClient.sql("select count(*) from imported_source_records").query(Long.class).single())
                .isEqualTo(recordsBefore);
    }
}
