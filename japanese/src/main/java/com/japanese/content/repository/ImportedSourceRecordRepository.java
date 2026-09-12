package com.japanese.content.repository;

import com.japanese.content.entity.ImportedSourceRecord;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportedSourceRecordRepository extends JpaRepository<ImportedSourceRecord, Long> {

    Optional<ImportedSourceRecord> findBySourceRefAndNoteTypeAndSourceNoteId(
            String sourceRef, String noteType, long sourceNoteId);

    Page<ImportedSourceRecord> findByNoteTypeOrderBySourceNoteId(String noteType, Pageable pageable);

    Page<ImportedSourceRecord> findByNoteTypeAndLevelCodeOrderBySourceNoteId(
            String noteType, String levelCode, Pageable pageable);

    Optional<ImportedSourceRecord> findByIdAndNoteType(Long id, String noteType);
}
