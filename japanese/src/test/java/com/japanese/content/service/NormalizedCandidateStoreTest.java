package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.content.entity.NormalizedCandidateQualityState;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.entity.NormalizedGrammarCandidateConfusablePattern;
import com.japanese.content.entity.NormalizedVocabularyCandidateExample;
import com.japanese.content.entity.NormalizedVocabularyCandidateMeaning;
import com.japanese.content.importer.GrammarNormalizationIssue;
import com.japanese.content.importer.GrammarNormalizationResult;
import com.japanese.content.importer.GrammarNormalizationWarning;
import com.japanese.content.importer.NormalizedConfusablePattern;
import com.japanese.content.importer.NormalizedExample;
import com.japanese.content.importer.NormalizedGrammarExample;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.NormalizedPitchAccent;
import com.japanese.content.importer.VocabularyNormalizationIssue;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.importer.VocabularyNormalizationWarning;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.GrammarComparisonRepository;
import com.japanese.content.repository.GrammarEnrichmentRepository;
import com.japanese.content.repository.GrammarRelationRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import com.japanese.content.repository.NormalizedContentCandidateRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ticket 4A: verifies {@link NormalizedCandidateStore} persists a lossless, independently-trackable
 * private snapshot of each normalization {@code Result} without ever touching production content
 * tables. Every scenario here builds {@code VocabularyNormalizationResult}/
 * {@code GrammarNormalizationResult} directly (the pure parsers are not re-tested here - see
 * {@code VocabularyNormalizationParserTest}/{@code GrammarNormalizationParserTest}).
 */
@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class NormalizedCandidateStoreTest {

    @Autowired
    NormalizedCandidateStore store;
    @Autowired
    NormalizedContentCandidateRepository repository;
    @Autowired
    ContentItemRepository contentItems;
    @Autowired
    GrammarEnrichmentRepository grammarEnrichments;
    @Autowired
    GrammarRelationRepository grammarRelations;
    @Autowired
    GrammarComparisonRepository grammarComparisons;
    @Autowired
    ImportedSourceRecordRepository importedSourceRecords;
    @Autowired
    JdbcClient jdbcClient;

    private String sourceRef() {
        return "candidate-test-" + UUID.randomUUID();
    }

    @Test
    void cleanVocabularySaveAndRetrieveIsLossless() {
        String ref = sourceRef();
        VocabularyNormalizationResult result = new VocabularyNormalizationResult(
                ref, 101L, "E-101", "食べる", "たべる", "verb",
                new NormalizedPitchAccent("0", "た/べる"),
                List.of(new NormalizedMeaning(1, "to eat"), new NormalizedMeaning(2, "to consume")),
                List.of(new NormalizedExample(1, "casual", "彼は食べる", "かれはたべる", "He eats"),
                        new NormalizedExample(2, null, "食べた", "たべた", "Ate")),
                new NormalizedJlptLevel("N5", "N5", "WordJLPT"),
                "たべる", "たべる",
                Map.of("VocabularyContext", "food context"),
                List.of(new VocabularyNormalizationWarning(VocabularyNormalizationIssue.UNKNOWN_EXTRA_FIELD, "info only")),
                true);

        NormalizedContentCandidate saved = store.saveVocabulary(result);

        NormalizedContentCandidate loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getCandidateType()).isEqualTo(NormalizedCandidateType.VOCABULARY);
        assertThat(loaded.getSourceRef()).isEqualTo(ref);
        assertThat(loaded.getSourceNoteId()).isEqualTo(101L);
        assertThat(loaded.getSourceIdentityKey()).isEqualTo("E-101");
        assertThat(loaded.getQualityState()).isEqualTo(NormalizedCandidateQualityState.INFORMATIONAL);
        assertThat(loaded.getVocabularyDetail().getEntryId()).isEqualTo("E-101");
        assertThat(loaded.getVocabularyDetail().getExpression()).isEqualTo("食べる");
        assertThat(loaded.getVocabularyDetail().getReading()).isEqualTo("たべる");
        assertThat(loaded.getVocabularyDetail().getPartOfSpeech()).isEqualTo("verb");
        assertThat(loaded.getVocabularyDetail().getPitchAccentTerminalStates()).isEqualTo("0");
        assertThat(loaded.getVocabularyDetail().getPitchAccentMora()).isEqualTo("た/べる");
        assertThat(loaded.getVocabularyDetail().getLevelCode()).isEqualTo("N5");
        assertThat(loaded.getVocabularyDetail().getLevelRawValue()).isEqualTo("N5");
        assertThat(loaded.getVocabularyDetail().getLevelSourceField()).isEqualTo("WordJLPT");
        assertThat(loaded.getVocabularyDetail().getNormalizedSearchExpression()).isEqualTo("たべる");
        assertThat(loaded.getVocabularyDetail().getNormalizedSearchReading()).isEqualTo("たべる");
        assertThat(loaded.getVocabularyMeanings()).extracting(NormalizedVocabularyCandidateMeaning::getMeaningText)
                .containsExactly("to eat", "to consume");
        assertThat(loaded.getVocabularyExamples()).extracting(NormalizedVocabularyCandidateExample::getJapaneseText)
                .containsExactly("彼は食べる", "食べた");
        assertThat(loaded.getVocabularyExamples()).extracting(NormalizedVocabularyCandidateExample::getMeaningLabel)
                .containsExactly("casual", null);
        assertThat(loaded.getExtraFields()).hasSize(1);
        assertThat(loaded.getExtraFields().get(0).getFieldName()).isEqualTo("VocabularyContext");
        assertThat(loaded.getExtraFields().get(0).getFieldValue()).isEqualTo("food context");
        assertThat(loaded.getWarnings()).hasSize(1);
        assertThat(loaded.getWarnings().get(0).getIssueCode()).isEqualTo("UNKNOWN_EXTRA_FIELD");
        assertThat(loaded.getWarnings().get(0).getSeverity()).isEqualTo("INFORMATIONAL");
    }

    @Test
    void cleanGrammarSaveAndRetrieveIsLossless() {
        String ref = sourceRef();
        GrammarNormalizationResult result = new GrammarNormalizationResult(
                ref, 202L, "U-202", "〜ばかり",
                new NormalizedGrammarExample(1, "彼は寝てばかりいる", null, "He is only sleeping"),
                "just did / only", "casual, slightly negative nuance", "verb-た + ばかり",
                List.of(new NormalizedConfusablePattern(1, "〜たところ", "different timing nuance"),
                        new NormalizedConfusablePattern(2, "〜たばかりだ", "closely related form")),
                new NormalizedJlptLevel("N3", "N3", "Level"),
                "⁣",
                Map.of("ExtraField", "extra value"),
                List.of(),
                true);

        NormalizedContentCandidate saved = store.saveGrammar(result);

        NormalizedContentCandidate loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getCandidateType()).isEqualTo(NormalizedCandidateType.GRAMMAR);
        assertThat(loaded.getSourceIdentityKey()).isEqualTo("U-202");
        assertThat(loaded.getQualityState()).isEqualTo(NormalizedCandidateQualityState.CLEAN);
        var detail = loaded.getGrammarDetail();
        assertThat(detail.getUnitId()).isEqualTo("U-202");
        assertThat(detail.getPattern()).isEqualTo("〜ばかり");
        assertThat(detail.getFrontExampleDisplayOrder()).isEqualTo(1);
        assertThat(detail.getFrontExampleJapaneseText()).isEqualTo("彼は寝てばかりいる");
        assertThat(detail.getFrontExampleReading()).isNull();
        assertThat(detail.getFrontExampleTranslation()).isEqualTo("He is only sleeping");
        assertThat(detail.getMeaningGloss()).isEqualTo("just did / only");
        assertThat(detail.getNuance()).isEqualTo("casual, slightly negative nuance");
        assertThat(detail.getConnectionForm()).isEqualTo("verb-た + ばかり");
        assertThat(detail.getLevelCode()).isEqualTo("N3");
        assertThat(detail.getRawKind()).isEqualTo("⁣");
        assertThat(loaded.getGrammarConfusablePatterns())
                .extracting(NormalizedGrammarCandidateConfusablePattern::getPattern)
                .containsExactly("〜たところ", "〜たばかりだ");
        assertThat(loaded.getGrammarConfusablePatterns())
                .extracting(NormalizedGrammarCandidateConfusablePattern::getExplanation)
                .containsExactly("different timing nuance", "closely related form");
        assertThat(loaded.getExtraFields()).hasSize(1);
        assertThat(loaded.getExtraFields().get(0).getFieldName()).isEqualTo("ExtraField");
        assertThat(loaded.getWarnings()).isEmpty();
    }

    @Test
    void fatalVocabularyCandidateIsPersistedWithNullSemanticFields() {
        String ref = sourceRef();
        VocabularyNormalizationResult result = new VocabularyNormalizationResult(
                ref, 303L, null, null, null, null, null,
                List.of(), List.of(), new NormalizedJlptLevel(null, null, null), null, null,
                Map.of(),
                List.of(new VocabularyNormalizationWarning(VocabularyNormalizationIssue.MISSING_ENTRY_ID, "no id"),
                        new VocabularyNormalizationWarning(VocabularyNormalizationIssue.MISSING_EXPRESSION, "no word"),
                        new VocabularyNormalizationWarning(VocabularyNormalizationIssue.MISSING_READING, "no reading"),
                        new VocabularyNormalizationWarning(VocabularyNormalizationIssue.MISSING_MEANING, "no meaning")),
                false);

        NormalizedContentCandidate saved = store.saveVocabulary(result);

        NormalizedContentCandidate loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getQualityState()).isEqualTo(NormalizedCandidateQualityState.FATAL);
        assertThat(loaded.getSourceIdentityKey()).isNull();
        assertThat(loaded.getVocabularyDetail()).isNotNull();
        assertThat(loaded.getVocabularyDetail().getEntryId()).isNull();
        assertThat(loaded.getVocabularyDetail().getExpression()).isNull();
        assertThat(loaded.getVocabularyDetail().getReading()).isNull();
        assertThat(loaded.getVocabularyMeanings()).isEmpty();
        assertThat(loaded.getVocabularyExamples()).isEmpty();
        assertThat(loaded.getWarnings()).hasSize(4);
    }

    @Test
    void fatalGrammarCandidateIsPersistedWithNullSemanticFields() {
        String ref = sourceRef();
        GrammarNormalizationResult result = new GrammarNormalizationResult(
                ref, 404L, null, null, null, null, null, null,
                List.of(), new NormalizedJlptLevel(null, null, null), null, Map.of(),
                List.of(new GrammarNormalizationWarning(GrammarNormalizationIssue.MISSING_UNIT_ID, "no unit"),
                        new GrammarNormalizationWarning(GrammarNormalizationIssue.MISSING_PATTERN, "no pattern"),
                        new GrammarNormalizationWarning(GrammarNormalizationIssue.MISSING_FRONT_EXAMPLE, "no example")),
                false);

        NormalizedContentCandidate saved = store.saveGrammar(result);

        NormalizedContentCandidate loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getQualityState()).isEqualTo(NormalizedCandidateQualityState.FATAL);
        assertThat(loaded.getSourceIdentityKey()).isNull();
        assertThat(loaded.getGrammarDetail()).isNotNull();
        assertThat(loaded.getGrammarDetail().getUnitId()).isNull();
        assertThat(loaded.getGrammarDetail().getPattern()).isNull();
        assertThat(loaded.getGrammarDetail().getFrontExampleJapaneseText()).isNull();
        assertThat(loaded.getGrammarConfusablePatterns()).isEmpty();
        assertThat(loaded.getWarnings()).hasSize(3);
    }

    @Test
    void entryIdAndUnitIdAreNotUniqueAcrossCandidates() {
        String ref = sourceRef();
        VocabularyNormalizationResult first = vocabulary(ref, 1L, "SHARED-KEY");
        VocabularyNormalizationResult second = vocabulary(ref, 2L, "SHARED-KEY");

        store.saveVocabulary(first);
        store.saveVocabulary(second);

        assertThat(repository.findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(c -> c.getSourceRef().equals(ref))
                .map(NormalizedContentCandidate::getSourceIdentityKey))
                .containsExactlyInAnyOrder("SHARED-KEY", "SHARED-KEY");
    }

    @Test
    void doubleSaveOfIdenticalResultDoesNotDuplicate() {
        String ref = sourceRef();
        VocabularyNormalizationResult result = vocabulary(ref, 5L, "E-5");

        store.saveVocabulary(result);
        store.saveVocabulary(result);

        NormalizedContentCandidate candidate = repository
                .findBySourceRefAndSourceNoteIdAndCandidateType(ref, 5L, NormalizedCandidateType.VOCABULARY)
                .orElseThrow();
        assertThat(repository.findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(c -> c.getSourceRef().equals(ref)).count()).isEqualTo(1);
        assertThat(loadedMeaningCount(candidate.getId())).isEqualTo(1);
        assertThat(loadedWarningCount(candidate.getId())).isEqualTo(0);
    }

    @Test
    void refreshReplacesSnapshotWithoutStaleChildRows() {
        String ref = sourceRef();
        VocabularyNormalizationResult firstVersion = new VocabularyNormalizationResult(
                ref, 7L, "E-7", "旧", "きゅう", "adj", null,
                List.of(new NormalizedMeaning(1, "old-sense-1"), new NormalizedMeaning(2, "old-sense-2"),
                        new NormalizedMeaning(3, "old-sense-3")),
                List.of(), new NormalizedJlptLevel(null, null, null), "きゅう", "きゅう", Map.of(),
                List.of(new VocabularyNormalizationWarning(VocabularyNormalizationIssue.EMPTY_PART_OF_SPEECH, "old warning")),
                true);
        NormalizedContentCandidate firstSaved = store.saveVocabulary(firstVersion);
        Long candidateId = firstSaved.getId();
        assertThat(loadedMeaningCount(candidateId)).isEqualTo(3);
        assertThat(loadedWarningCount(candidateId)).isEqualTo(1);

        VocabularyNormalizationResult secondVersion = new VocabularyNormalizationResult(
                ref, 7L, "E-7", "新", "しん", "adj", null,
                List.of(new NormalizedMeaning(1, "new-sense-1")),
                List.of(), new NormalizedJlptLevel(null, null, null), "しん", "しん", Map.of(),
                List.of(),
                true);
        NormalizedContentCandidate secondSaved = store.saveVocabulary(secondVersion);

        assertThat(secondSaved.getId()).isEqualTo(candidateId);
        assertThat(loadedMeaningCount(candidateId)).isEqualTo(1);
        assertThat(loadedWarningCount(candidateId)).isEqualTo(0);
        NormalizedContentCandidate reloaded = repository.findById(candidateId).orElseThrow();
        assertThat(reloaded.getVocabularyDetail().getExpression()).isEqualTo("新");
        assertThat(reloaded.getVocabularyMeanings()).extracting(NormalizedVocabularyCandidateMeaning::getMeaningText)
                .containsExactly("new-sense-1");
        assertThat(repository.findByCandidateType(NormalizedCandidateType.VOCABULARY).stream()
                .filter(c -> c.getSourceRef().equals(ref)).count()).isEqualTo(1);
    }

    @Test
    void savingCandidatesNeverChangesProductionOrImportedSourceRowCounts() {
        long contentItemsBefore = contentItems.count();
        long wordsBefore = tableCount("words");
        long grammarsBefore = tableCount("grammars");
        long enrichmentsBefore = grammarEnrichments.count();
        long relationsBefore = grammarRelations.count();
        long comparisonsBefore = grammarComparisons.count();
        long importedBefore = importedSourceRecords.count();

        String ref = sourceRef();
        store.saveVocabulary(vocabulary(ref, 9L, "E-9"));
        store.saveGrammar(grammar(ref, 10L, "U-10"));

        assertThat(contentItems.count()).isEqualTo(contentItemsBefore);
        assertThat(tableCount("words")).isEqualTo(wordsBefore);
        assertThat(tableCount("grammars")).isEqualTo(grammarsBefore);
        assertThat(grammarEnrichments.count()).isEqualTo(enrichmentsBefore);
        assertThat(grammarRelations.count()).isEqualTo(relationsBefore);
        assertThat(grammarComparisons.count()).isEqualTo(comparisonsBefore);
        assertThat(importedSourceRecords.count()).isEqualTo(importedBefore);
    }

    private long tableCount(String tableName) {
        return jdbcClient.sql("select count(*) from " + tableName).query(Long.class).single();
    }

    private long loadedMeaningCount(Long candidateId) {
        return jdbcClient.sql("select count(*) from normalized_vocabulary_candidate_meanings where candidate_id = ?")
                .param(candidateId).query(Long.class).single();
    }

    private long loadedWarningCount(Long candidateId) {
        return jdbcClient.sql("select count(*) from normalized_candidate_warnings where candidate_id = ?")
                .param(candidateId).query(Long.class).single();
    }

    private VocabularyNormalizationResult vocabulary(String ref, long noteId, String entryId) {
        return new VocabularyNormalizationResult(
                ref, noteId, entryId, "語", "ご", "noun", null,
                List.of(new NormalizedMeaning(1, "word")),
                List.of(), new NormalizedJlptLevel(null, null, null), "ご", "ご", Map.of(), List.of(), true);
    }

    private GrammarNormalizationResult grammar(String ref, long noteId, String unitId) {
        return new GrammarNormalizationResult(
                ref, noteId, unitId, "〜てみる",
                new NormalizedGrammarExample(1, "食べてみる", null, "try eating"),
                "try", "casual", "verb-て + みる", List.of(),
                new NormalizedJlptLevel(null, null, null), "⁣", Map.of(), List.of(), true);
    }
}
