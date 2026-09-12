package com.japanese.content.importer;

import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Example;
import com.japanese.content.entity.ImportedSourceRecord;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.ImportedSourceRecordRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("import-sample")
public class ApkgVocabularyImporter {

    private static final Logger log = LoggerFactory.getLogger(ApkgVocabularyImporter.class);
    private static final String VOCABULARY_NOTE_TYPE = "JLPT MAX덱 어휘";
    private static final String GRAMMAR_NOTE_TYPE = "JLPT MAX덱 문법";
    private static final String QUESTION_NOTE_TYPE = "JLPT MAX덱 어휘문제";
    private static final String REFERENCE_NOTE_TYPE = "JLPT MAX덱 참조표";
    private static final String RETIRED_TAG = "jlpt-max-vocabulary-retired";
    private static final String SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";
    private static final Pattern PITCH_TERMINAL = Pattern.compile("data-pitch-terminal-states=\\\"([^\\\"]+)\\\"");
    private static final Pattern PITCH_MORA = Pattern.compile("j1=\\\"([^\\\"]+)\\\"");

    private final ContentItemRepository contentItemRepository;
    private final LevelRepository levelRepository;
    private final ExampleHtmlParser exampleHtmlParser;
    private final GrammarHtmlParser grammarHtmlParser;
    private final ImportedSourceRecordRepository importedSourceRecordRepository;
    private final TransactionTemplate transactionTemplate;
    private static final int IMPORT_BATCH_SIZE = 100;

    @Value("${japanese.import.apkg-path:}")
    private String apkgPath;

    @Value("${japanese.import.limit:20}")
    private int limit;

    @Value("${japanese.import.vocabulary-enabled:true}")
    private boolean vocabularyEnabled;

    @Value("${japanese.import.grammar-enabled:true}")
    private boolean grammarEnabled;

    @Value("${japanese.import.source-records-enabled:false}")
    private boolean sourceRecordsEnabled;

    public ApkgVocabularyImporter(
            ContentItemRepository contentItemRepository,
            LevelRepository levelRepository,
            ExampleHtmlParser exampleHtmlParser,
            GrammarHtmlParser grammarHtmlParser,
            ImportedSourceRecordRepository importedSourceRecordRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.contentItemRepository = contentItemRepository;
        this.levelRepository = levelRepository;
        this.exampleHtmlParser = exampleHtmlParser;
        this.grammarHtmlParser = grammarHtmlParser;
        this.importedSourceRecordRepository = importedSourceRecordRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void importVocabularySample() throws IOException, SQLException {
        if (apkgPath == null || apkgPath.isBlank()) {
            throw new IllegalArgumentException(
                    "japanese.import.apkg-path must point to JLPT-MAX-Deck-2.1.1.apkg");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("japanese.import.limit must be zero or greater");
        }

        Path apkg = Path.of(apkgPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(apkg)) {
            throw new IllegalArgumentException("APKG file does not exist: " + apkg);
        }

        Path collection = Files.createTempFile("japanese-collection-", ".anki21");
        try {
            extractCollection(apkg, collection);
            ImportCounts imported = importFromCollection(collection, limit);
            log.info("Imported {} vocabulary, {} grammar, and {} source records",
                    imported.vocabulary(), imported.grammar(), imported.sourceRecords());
        } finally {
            Files.deleteIfExists(collection);
        }
    }

    private void extractCollection(Path apkg, Path target) throws IOException {
        try (ZipFile zipFile = new ZipFile(apkg.toFile())) {
            ZipEntry entry = zipFile.getEntry("collection.anki21");
            if (entry == null) {
                throw new IllegalArgumentException("collection.anki21 was not found in APKG: " + apkg);
            }
            try (InputStream input = zipFile.getInputStream(entry)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private ImportCounts importFromCollection(Path collection, int importLimit) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + collection)) {
            Map<Long, String> noteTypeNames = loadNoteTypeNames(connection);
            Long vocabularyTypeId = noteTypeNames.entrySet().stream()
                    .filter(entry -> VOCABULARY_NOTE_TYPE.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Vocabulary note type was not found: " + VOCABULARY_NOTE_TYPE));
            Map<Long, List<String>> fieldNames = loadFieldNames(connection, vocabularyTypeId);
            List<String> fields = fieldNames.get(vocabularyTypeId);
            requireFields(fields, VOCABULARY_NOTE_TYPE, "EntryID", "Word", "Reading", "PitchAccent",
                    "Meaning", "ExamplesRendered", "PartOfSpeech", "JLPT", "WordJLPT");

            int vocabularyImported = 0;
            if (vocabularyEnabled) {
                String sql = "select id, tags, flds from notes where mid = ? "
                        + "and tags not like ? order by id" + (importLimit > 0 ? " limit ?" : "");
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, vocabularyTypeId);
                    statement.setString(2, "%" + RETIRED_TAG + "%");
                    if (importLimit > 0) {
                        statement.setInt(3, importLimit);
                    }
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<SourceNote> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
                        while (resultSet.next()) {
                            batch.add(new SourceNote(resultSet.getLong("id"), resultSet.getString("tags"), resultSet.getString("flds")));
                            if (batch.size() == IMPORT_BATCH_SIZE) { vocabularyImported += importVocabularyBatch(batch, fields); batch.clear(); }
                        }
                        vocabularyImported += importVocabularyBatch(batch, fields);
                    }
                }
            }
            int grammarImported = grammarEnabled
                    ? importGrammar(connection, noteTypeNames)
                    : 0;
            int sourceRecordsImported = sourceRecordsEnabled
                    ? importSourceRecords(connection, noteTypeNames)
                    : 0;
            return new ImportCounts(vocabularyImported, grammarImported, sourceRecordsImported);
        }
    }

    private int importGrammar(Connection connection, Map<Long, String> noteTypeNames)
            throws SQLException {
        Long grammarTypeId = noteTypeNames.entrySet().stream()
                .filter(entry -> GRAMMAR_NOTE_TYPE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (grammarTypeId == null) {
            log.info("Grammar note type was not found; skipping grammar import");
            return 0;
        }
        List<String> fields = loadFieldNames(connection, grammarTypeId).get(grammarTypeId);
        requireFields(fields, GRAMMAR_NOTE_TYPE, "Level", "UnitID", "FrontHTML", "BackHTML");
        int imported = 0;
        String sql = "select id, tags, flds from notes where mid = ? order by id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, grammarTypeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SourceNote> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
                while (resultSet.next()) {
                    batch.add(new SourceNote(resultSet.getLong("id"), resultSet.getString("tags"), resultSet.getString("flds")));
                    if (batch.size() == IMPORT_BATCH_SIZE) { imported += importGrammarBatch(batch, fields); batch.clear(); }
                }
                imported += importGrammarBatch(batch, fields);
            }
        }
        return imported;
    }

    private int importVocabularyBatch(List<SourceNote> notes, List<String> fields) {
        if (notes.isEmpty()) return 0;
        return transactionTemplate.execute(status -> {
            int imported = 0;
            for (SourceNote note : notes) {
                try {
                    if (importRecord(note.id(), note.tags(), note.fields(), fields)) imported++;
                } catch (RuntimeException exception) {
                    log.warn("Skipped vocabulary note {}: {}", note.id(), exception.getMessage());
                }
            }
            contentItemRepository.flush();
            return imported;
        });
    }

    private int importGrammarBatch(List<SourceNote> notes, List<String> fields) {
        if (notes.isEmpty()) return 0;
        return transactionTemplate.execute(status -> {
            int imported = 0;
            for (SourceNote note : notes) {
                try {
                    if (importGrammarRecord(note.id(), note.tags(), note.fields(), fields)) imported++;
                } catch (RuntimeException exception) {
                    log.warn("Skipped grammar note {}: {}", note.id(), exception.getMessage());
                }
            }
            contentItemRepository.flush();
            return imported;
        });
    }

    private int importSourceRecords(Connection connection, Map<Long, String> noteTypeNames) throws SQLException {
        int imported = 0;
        for (Map.Entry<Long, String> entry : noteTypeNames.entrySet()) {
            String noteType = entry.getValue();
            if (!QUESTION_NOTE_TYPE.equals(noteType) && !REFERENCE_NOTE_TYPE.equals(noteType)) {
                continue;
            }
            List<String> fields = loadFieldNames(connection, entry.getKey()).get(entry.getKey());
            if (fields == null || fields.isEmpty()) {
                continue;
            }
            String sql = "select id, tags, flds from notes where mid = ? order by id";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, entry.getKey());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        long sourceNoteId = resultSet.getLong("id");
                        String rawFields = resultSet.getString("flds");
                        Map<String, String> values = valuesByName(rawFields, fields);
                        String levelCode = firstValue(values, "JLPT", "Level");
                        var existing = importedSourceRecordRepository
                                .findBySourceRefAndNoteTypeAndSourceNoteId(SOURCE_REF, noteType, sourceNoteId);
                        if (existing.isPresent()) {
                            if (existing.get().getLevelCode() == null && levelCode != null) {
                                existing.get().setLevelCode(levelCode);
                            }
                            continue;
                        }
                        importedSourceRecordRepository.save(new ImportedSourceRecord(
                                SOURCE_REF,
                                noteType,
                                sourceNoteId,
                                levelCode,
                                resultSet.getString("tags"),
                                String.join("\u001f", fields),
                                rawFields));
                        imported++;
                    }
                }
            }
        }
        return imported;
    }

    private boolean importGrammarRecord(long noteId, String tags, String rawFields, List<String> fieldNames) {
        Map<String, String> values = valuesByName(rawFields, fieldNames);
        String unitId = value(values, "UnitID");
        String frontHtml = value(values, "FrontHTML");
        String backHtml = value(values, "BackHTML");
        String levelValue = value(values, "Level");
        if (unitId == null || frontHtml == null || backHtml == null) {
            return false;
        }
        String slug = "jlpt-max-grammar-" + noteId;
        ParsedGrammar parsed = grammarHtmlParser.parse(frontHtml, backHtml, value(values, "Kind"), unitId);
        if (parsed.pattern() == null || parsed.pattern().isBlank()) {
            return false;
        }
        var source = sourceRecord(GRAMMAR_NOTE_TYPE, noteId, tags, rawFields, fieldNames, normalizeJlpt(levelValue));
        if (source.getContentItem() != null) return false;
        ContentItem item = contentItemRepository.findBySlug(slug)
                .orElseGet(() -> new ContentItem(slug, ContentType.GRAMMAR, SOURCE_REF, false));
        if (item.getGrammar() != null) {
            source.linkContentItem(item);
            importedSourceRecordRepository.save(source);
            return false;
        }
        item.attachGrammar(new com.japanese.content.entity.Grammar(parsed.pattern(), parsed.explanation(), parsed.connection()));
        String level = normalizeJlpt(levelValue);
        if (level != null) item.addLevel(level("JLPT", level, "JLPT " + level));
        attachGrammarExamples(item, parsed.examples());
        source.linkContentItem(contentItemRepository.save(item));
        importedSourceRecordRepository.save(source);
        return true;
    }

    private void attachGrammarExamples(ContentItem item, List<ParsedExample> examples) {
        for (int index = 0; index < examples.size(); index++) {
            ParsedExample parsed = examples.get(index);
            item.addExample(new Example(
                    parsed.japaneseText(), parsed.reading(), parsed.translation(), index + 1));
        }
    }

    private boolean importRecord(long noteId, String tags, String rawFields, List<String> fieldNames) {
        Map<String, String> values = valuesByName(rawFields, fieldNames);
        String entryId = value(values, "EntryID");
        String wordValue = clean(value(values, "Word"));
        String reading = clean(value(values, "Reading"));
        if (entryId == null || wordValue == null || reading == null) {
            log.warn("Skipped vocabulary record without identity fields: {}", entryId);
            return false;
        }

        String slug = "jlpt-max-" + entryId;
        String jlpt = normalizeJlpt(firstValue(values, "WordJLPT", "JLPT"));
        var source = sourceRecord(VOCABULARY_NOTE_TYPE, noteId, tags, rawFields, fieldNames, jlpt);
        if (source.getContentItem() != null) return false;
        ContentItem item = contentItemRepository.findBySlug(slug)
                .orElseGet(() -> new ContentItem(slug, ContentType.WORD, SOURCE_REF, false));
        if (item.getWord() != null) { source.linkContentItem(item); importedSourceRecordRepository.save(source); return false; }
        Word word = new Word(
                wordValue,
                reading,
                clean(value(values, "PartOfSpeech")),
                extractPitchAccent(value(values, "PitchAccent"))
        );
        List<Meaning> meanings = createMeanings(word, clean(value(values, "Meaning")));
        item.attachWord(word);
        if (jlpt != null) {
            item.addLevel(level("JLPT", jlpt, "JLPT " + jlpt));
        }
        attachExamples(item, meanings, value(values, "ExamplesRendered"));
        source.linkContentItem(contentItemRepository.save(item));
        importedSourceRecordRepository.save(source);
        return true;
    }

    private List<Meaning> createMeanings(Word word, String rawMeaning) {
        List<Meaning> meanings = new ArrayList<>();
        if (rawMeaning == null) {
            return meanings;
        }
        String[] senseValues = rawMeaning.split("\\s+/(?:\\s+|$)");
        for (int index = 0; index < senseValues.length; index++) {
            String text = senseValues[index].trim();
            if (!text.isBlank()) {
                Meaning meaning = new Meaning("ko", text, index + 1);
                word.addMeaning(meaning);
                meanings.add(meaning);
            }
        }
        return meanings;
    }

    private void attachExamples(ContentItem item, List<Meaning> meanings, String renderedHtml) {
        List<ParsedExample> examples = exampleHtmlParser.parse(renderedHtml);
        for (int index = 0; index < examples.size(); index++) {
            ParsedExample parsed = examples.get(index);
            Example example = new Example(
                    parsed.japaneseText(),
                    parsed.reading(),
                    parsed.translation(),
                    index + 1,
                    parsed.audioFileName()
            );
            meanings.stream()
                    .filter(meaning -> meaning.getText().equals(parsed.meaningLabel()))
                    .findFirst()
                    .ifPresent(example::setMeaning);
            item.addExample(example);
        }
    }

    private Map<Long, String> loadNoteTypeNames(Connection connection) throws SQLException {
        Map<Long, String> noteTypeNames = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("select id, name from notetypes");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                noteTypeNames.put(resultSet.getLong("id"), resultSet.getString("name"));
            }
        }
        return noteTypeNames;
    }

    private Map<Long, List<String>> loadFieldNames(Connection connection, long noteTypeId) throws SQLException {
        Map<Long, List<String>> fieldNames = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select ntid, ord, name from fields where ntid = ? order by ord")) {
            statement.setLong(1, noteTypeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (resultSet.next()) {
                    names.add(resultSet.getString("name"));
                }
                fieldNames.put(noteTypeId, names);
            }
        }
        return fieldNames;
    }

    private Map<String, String> valuesByName(String rawFields, List<String> fieldNames) {
        String[] rawValues = rawFields.split("\\u001f", -1);
        Map<String, String> values = new HashMap<>();
        for (int index = 0; index < fieldNames.size() && index < rawValues.length; index++) {
            String value = rawValues[index];
            values.put(fieldNames.get(index), value);
        }
        return values;
    }

    private String value(Map<String, String> values, String name) {
        String value = values.get(name);
        return value == null || value.isBlank() ? null : value;
    }

    private String clean(String value) { return AnkiFieldTextNormalizer.text(value); }

    private void requireFields(List<String> fields, String noteType, String... required) {
        if (fields == null || !fields.containsAll(List.of(required))) {
            throw new IllegalArgumentException("Unexpected " + noteType + " field schema: " + fields);
        }
    }

    private ImportedSourceRecord sourceRecord(String noteType, long noteId, String tags, String rawFields,
                                               List<String> fields, String levelCode) {
        ImportedSourceRecord record = importedSourceRecordRepository
                .findBySourceRefAndNoteTypeAndSourceNoteId(SOURCE_REF, noteType, noteId)
                .orElseGet(() -> new ImportedSourceRecord(SOURCE_REF, noteType, noteId, levelCode,
                        tags, String.join("\u001f", fields), rawFields));
        record.refresh(levelCode, tags, String.join("\u001f", fields), rawFields);
        return record;
    }

    private String normalizeJlpt(String raw) {
        if (raw == null) return null;
        Matcher matcher = Pattern.compile("(?i)(?:jlpt[-_ ]*)?(n[1-5])").matcher(raw.trim());
        return matcher.matches() ? matcher.group(1).toUpperCase(java.util.Locale.ROOT) : null;
    }

    private String firstValue(Map<String, String> values, String... names) {
        for (String name : names) {
            String value = value(values, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String extractPitchAccent(String html) {
        if (html == null) {
            return null;
        }
        Matcher terminal = PITCH_TERMINAL.matcher(html);
        Matcher mora = PITCH_MORA.matcher(html);
        if (!terminal.find() || !mora.find()) {
            return null;
        }
        return "terminal=" + terminal.group(1) + ";mora=" + mora.group(1);
    }

    private Level level(String system, String code, String name) {
        return levelRepository.findBySystemAndCode(system, code)
                .orElseGet(() -> levelRepository.save(new Level(system, code, name)));
    }

    private record ImportCounts(int vocabulary, int grammar, int sourceRecords) {
    }

    private record SourceNote(long id, String tags, String fields) { }
}
