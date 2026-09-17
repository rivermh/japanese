package com.japanese.content.service;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * JLPT-MAX Ticket 4E-1 hardening (MAJOR 1): a read-only lookup of the exact raw Anki provenance a
 * private-staging note still carries, keyed by the same {@code (sourceRef, sourceNoteId)} identity a
 * {@link com.japanese.content.entity.NormalizedContentCandidate} already carries as its own provenance
 * pointer. {@code private_apkg_notes} has no JPA entity anywhere in this codebase (it is treated as
 * raw/JDBC-only, matching {@code PrivateApkgExtractor}'s own javadoc), so this class reads it directly
 * via {@link JdbcClient} rather than introducing a new entity for a table this ticket never writes to.
 *
 * <p>{@code private_apkg_notes.field_names}/{@code field_values} are stored as JSON arrays (see
 * {@code PrivateApkgExtractor.encode(...)}), not the U+001F-joined form
 * {@code ImportedSourceRecord.fieldNames}/{@code fieldValues} already uses everywhere else in this
 * codebase ({@code ApkgVocabularyImporter}, {@code QuizQuestionService}'s reader) - this class decodes
 * the JSON and re-joins with that same established delimiter, so the value this class returns is
 * format-compatible with every other writer/reader of that column, while still being the genuine raw
 * Anki field name/value pair for this exact note (only {@code PrivateApkgExtractor}'s own audio-marker
 * stripping has already been applied to {@code field_values}, exactly as it is for every other row in
 * this table - that sanitization is this staging table's own definition of "raw", not something this
 * class adds).
 *
 * <p>{@code private_apkg_notes} rows are independent of any {@code NormalizedContentCandidate} row (no
 * FK - see that entity's own class javadoc) and may have been reprocessed or removed since a candidate
 * was normalized from one; a missing row here is therefore an expected, not exceptional, outcome that
 * callers must handle explicitly (see {@link NormalizedVocabularyCandidatePromotionService}).
 */
@Service
class PrivateApkgNoteProvenanceReader {

    private final JdbcClient jdbcClient;
    private final ObjectMapper json;

    PrivateApkgNoteProvenanceReader(JdbcClient jdbcClient, ObjectMapper json) {
        this.jdbcClient = jdbcClient;
        this.json = json;
    }

    /** Read-only; safe to call from inside an already-open write transaction (joins it, no new one). */
    @Transactional(readOnly = true)
    Optional<RawProvenance> find(String sourceRef, long sourceNoteId) {
        return jdbcClient.sql("select tags, field_names, field_values from private_apkg_notes "
                        + "where source_ref = ? and source_note_id = ?")
                .param(sourceRef)
                .param(sourceNoteId)
                .query((resultSet, rowNum) -> new RawProvenance(
                        resultSet.getString("tags"),
                        decode(resultSet.getString("field_names")),
                        decode(resultSet.getString("field_values"))))
                .optional();
    }

    private List<String> decode(String jsonArray) {
        return json.readValue(jsonArray, new TypeReference<>() { });
    }

    /** The genuine raw Anki payload for one private-staging note, decoded but not otherwise altered. */
    record RawProvenance(String tags, List<String> fieldNames, List<String> fieldValues) {
    }
}
