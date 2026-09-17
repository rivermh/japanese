package com.japanese.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.NormalizedCandidateType;
import com.japanese.content.entity.NormalizedContentCandidate;
import com.japanese.content.importer.NormalizedJlptLevel;
import com.japanese.content.importer.NormalizedMeaning;
import com.japanese.content.importer.VocabularyNormalizationResult;
import com.japanese.content.service.NormalizedCandidateStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * JLPT-MAX Ticket 4E-0: verifies the two promotion-time {@code PESSIMISTIC_WRITE} lock query
 * primitives this ticket adds - {@code NormalizedContentCandidateRepository.findByIdAndCandidateTypeForPromotion}
 * and {@code ImportedSourceRecordRepository.findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion} -
 * return the exact row (or empty) they promise. This does not exercise concurrent locking under a
 * real race - no promotion write service exists yet to do that with (see Ticket 4E-1).
 */
@SpringBootTest
@ActiveProfiles("sample")
@Transactional
class NormalizedCandidatePromotionLockRepositoryTest {

    @Autowired NormalizedCandidateStore store;
    @Autowired NormalizedContentCandidateRepository candidates;
    @Autowired ImportedSourceRecordRepository importedSourceRecords;
    @Autowired ContentItemRepository contentItems;

    @Test
    void candidateLockQueryReturnsTheExactCandidate() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご"));
        NormalizedContentCandidate candidate = onlyVocabularyCandidate(ref);

        Optional<NormalizedContentCandidate> locked =
                candidates.findByIdAndCandidateTypeForPromotion(candidate.getId(), NormalizedCandidateType.VOCABULARY);

        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(candidate.getId());
    }

    @Test
    void candidateLockQueryReturnsEmptyForAMissingId() {
        assertThat(candidates.findByIdAndCandidateTypeForPromotion(-1L, NormalizedCandidateType.VOCABULARY)).isEmpty();
    }

    @Test
    void candidateLockQueryReturnsEmptyWhenTheCandidateTypeDoesNotMatch() {
        String ref = ref();
        store.saveVocabulary(vocab(ref, 1L, "E1", "語", "ご"));
        NormalizedContentCandidate candidate = onlyVocabularyCandidate(ref);

        assertThat(candidates.findByIdAndCandidateTypeForPromotion(candidate.getId(), NormalizedCandidateType.GRAMMAR))
                .isEmpty();
    }

    @Test
    void provenanceLockQueryReturnsTheExactTupleRow() {
        String ref = ref();
        ContentItem item = contentItems.save(
                new ContentItem("lock-test-slug-" + UUID.randomUUID(), ContentType.WORD, ref, false));
        ImportedSourceRecord record = new ImportedSourceRecord(ref, "JLPT MAX덱 어휘", 7L, "N5", "", "f", "v");
        record.linkContentItem(item);
        importedSourceRecords.save(record);

        Optional<ImportedSourceRecord> locked =
                importedSourceRecords.findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion(ref, "JLPT MAX덱 어휘", 7L);

        assertThat(locked).isPresent();
        assertThat(locked.get().getSourceNoteId()).isEqualTo(7L);
        assertThat(locked.get().getContentItem().getId()).isEqualTo(item.getId());
    }

    @Test
    void provenanceLockQueryReturnsEmptyOnAnyTupleMismatch() {
        String ref = ref();
        importedSourceRecords.save(new ImportedSourceRecord(ref, "JLPT MAX덱 어휘", 7L, "N5", "", "f", "v"));

        assertThat(importedSourceRecords.findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion(ref, "JLPT MAX덱 어휘", 8L))
                .isEmpty();
        assertThat(importedSourceRecords.findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion(ref, "다른 노트타입", 7L))
                .isEmpty();
        assertThat(importedSourceRecords.findBySourceRefAndNoteTypeAndSourceNoteIdForPromotion("다른-ref", "JLPT MAX덱 어휘", 7L))
                .isEmpty();
    }

    private String ref() {
        return "lock-test-" + UUID.randomUUID();
    }

    private NormalizedContentCandidate onlyVocabularyCandidate(String ref) {
        List<NormalizedContentCandidate> found =
                candidates.findByCandidateTypeAndSourceRef(NormalizedCandidateType.VOCABULARY, ref);
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private VocabularyNormalizationResult vocab(String ref, long noteId, String entryId, String expression,
            String reading) {
        return new VocabularyNormalizationResult(
                ref, noteId, entryId, expression, reading, "noun", null,
                List.of(new NormalizedMeaning(1, "meaning")),
                List.of(), new NormalizedJlptLevel("N5", "N5", "WordJLPT"), expression, reading, Map.of(),
                List.of(), true);
    }
}
