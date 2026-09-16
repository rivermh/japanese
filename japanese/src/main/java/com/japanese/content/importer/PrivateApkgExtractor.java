package com.japanese.content.importer;

import tools.jackson.databind.ObjectMapper;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

/** Explicitly invoked private extraction; never called by a startup runner or publication flow. */
@Service
public class PrivateApkgExtractor {
    private static final int BATCH = 100;
    private static final int MAX_FIELD_CHARS = 8_000_000;
    private static final Pattern SOUND = Pattern.compile("(?i)\\[sound:[^\\]\\r\\n]*\\]");
    private static final Pattern LEVEL = Pattern.compile("(?i)(?:jlpt[-_ ]*)?(n[1-5])");
    private final DataSource dataSource;
    private final ObjectMapper json;
    private final GrammarHtmlParser grammarParser;

    public PrivateApkgExtractor(DataSource dataSource, ObjectMapper json, GrammarHtmlParser grammarParser) {
        this.dataSource = dataSource;
        this.json = json;
        this.grammarParser = grammarParser;
    }

    public Summary extract(Path apkg, String sourceRef) throws IOException, SQLException {
        if (sourceRef == null || sourceRef.isBlank() || sourceRef.length() > 160) {
            throw new IllegalArgumentException("A private sourceRef (max 160 chars) is required");
        }
        String file = apkg.getFileName().toString();
        if (!file.endsWith(".apkg") || file.length() > 160) throw new IllegalArgumentException("Expected .apkg file");
        Path collection = Files.createTempFile("private-apkg-collection-", ".anki21");
        try {
            // Only the collection DB entry is read; ZIP media entries are never opened.
            try (ZipFile zip = new ZipFile(apkg.toFile())) {
                ZipEntry db = zip.getEntry("collection.anki21");
                if (db == null) throw new IllegalArgumentException("Missing collection.anki21");
                try (InputStream input = zip.getInputStream(db)) {
                    Files.copy(input, collection, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return extractCollection(collection, sourceRef, file);
        } finally {
            Files.deleteIfExists(collection);
        }
    }

    /** Package-visible entry point for tiny synthetic SQLite collection fixtures. */
    Summary extractCollection(Path collection, String sourceRef, String file) throws SQLException {
        Map<String, Long> categories = new TreeMap<>();
        Map<String, Long> decks = new TreeMap<>();
        Map<String, Long> types = new TreeMap<>();
        long total = 0, skipped = 0, audioNotes = 0, rawFields = 0, malformed = 0, multiCard = 0;
        try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + collection);
             Connection target = dataSource.getConnection()) {
            Map<Long, Model> models = models(source);
            Map<Long, String> deckNames = new LinkedHashMap<>();
            try (PreparedStatement query = source.prepareStatement("select id,name from decks");
                 ResultSet rows = query.executeQuery()) {
                while (rows.next()) deckNames.put(rows.getLong(1), rows.getString(2).replace('\u001f', '/'));
            }
            target.setAutoCommit(false);
            try (PreparedStatement notes = source.prepareStatement("select id,guid,mid,tags,flds from notes order by id");
                 PreparedStatement cards = source.prepareStatement("select id,did,ord from cards where nid=? order by id");
                 PreparedStatement exists = target.prepareStatement("select model_id,anki_guid from private_apkg_notes where source_ref=? and source_note_id=?");
                 PreparedStatement insert = target.prepareStatement("insert into private_apkg_notes (source_ref,source_file,source_version,source_note_id,model_id,note_type,category,anki_guid,deck_paths,card_metadata,tags,field_names,field_values,normalized_values,audio_reference_count,extracted_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                 ResultSet rows = notes.executeQuery()) {
                int pending = 0;
                while (rows.next()) {
                    long id = rows.getLong(1), modelId = rows.getLong(3);
                    Model model = models.get(modelId);
                    if (model == null) throw new SQLException("Unknown model " + modelId + " for note " + id);
                    cards.setLong(1, id);
                    List<Map<String, Object>> cardInfo = new ArrayList<>();
                    List<String> paths = new ArrayList<>();
                    try (ResultSet cardRows = cards.executeQuery()) {
                        while (cardRows.next()) {
                            String path = deckNames.get(cardRows.getLong(2));
                            if (path == null) throw new SQLException("Unknown deck for card " + cardRows.getLong(1));
                            cardInfo.add(Map.of("id", cardRows.getLong(1), "deck", path, "ord", cardRows.getInt(3)));
                            if (!paths.contains(path)) paths.add(path);
                        }
                    }
                    String category = category(model.name(), paths);
                    categories.merge(category, 1L, Long::sum);
                    types.merge(model.name(), 1L, Long::sum);
                    if (paths.isEmpty()) decks.merge("(no card)", 1L, Long::sum);
                    for (String path : paths) decks.merge(path, 1L, Long::sum);
                    if (cardInfo.size() > 1) multiCard++;
                    String[] values = rows.getString(5).split("\\u001f", -1);
                    if (values.length != model.fields().size()) malformed++;
                    if (values.length > model.fields().size()) throw new SQLException("Field overflow for note " + id);
                    List<String> safeValues = new ArrayList<>();
                    Map<String, String> byName = new LinkedHashMap<>();
                    int audioCount = 0;
                    for (int i = 0; i < model.fields().size(); i++) {
                        String name = model.fields().get(i);
                        String value = i < values.length ? values[i] : "";
                        Matcher matches = SOUND.matcher(value);
                        while (matches.find()) audioCount++;
                        if (isAudioField(name)) {
                            if (!value.isBlank() && audioCount == 0) audioCount++;
                            value = "";
                        } else value = SOUND.matcher(value).replaceAll("");
                        if (value.length() > MAX_FIELD_CHARS) throw new SQLException("Oversized field on note " + id);
                        safeValues.add(value);
                        byName.put(name, value);
                    }
                    if (audioCount > 0) audioNotes++;
                    rawFields += model.fields().size();
                    total++;
                    exists.setString(1, sourceRef);
                    exists.setLong(2, id);
                    try (ResultSet found = exists.executeQuery()) {
                        if (found.next()) {
                            if (found.getLong(1) != modelId || !found.getString(2).equals(rows.getString(2)))
                                throw new SQLException("Source identity conflict for note " + id);
                            skipped++;
                            continue;
                        }
                    }
                    String normalized = normalized(category, byName);
                    int n = 1;
                    insert.setString(n++, sourceRef);
                    insert.setString(n++, file);
                    insert.setString(n++, file.replaceFirst("(?i)^.*?-([0-9]+(?:\\.[0-9]+)*)\\.apkg$", "$1"));
                    insert.setLong(n++, id);
                    insert.setLong(n++, modelId);
                    insert.setString(n++, model.name());
                    insert.setString(n++, category);
                    insert.setString(n++, rows.getString(2));
                    insert.setString(n++, encode(paths));
                    insert.setString(n++, encode(cardInfo));
                    insert.setString(n++, rows.getString(4));
                    insert.setString(n++, encode(model.fields()));
                    insert.setString(n++, encode(safeValues));
                    insert.setString(n++, normalized);
                    insert.setInt(n++, audioCount);
                    insert.setTimestamp(n, Timestamp.from(Instant.now()));
                    insert.addBatch();
                    if (++pending == BATCH) { insert.executeBatch(); target.commit(); pending = 0; }
                }
                if (pending > 0) { insert.executeBatch(); target.commit(); }
            } catch (SQLException | RuntimeException e) {
                target.rollback();
                throw e;
            }
        }
        return new Summary(total, total - skipped, skipped, malformed, multiCard, audioNotes, rawFields,
                Map.copyOf(categories), Map.copyOf(decks), Map.copyOf(types));
    }

    private Map<Long, Model> models(Connection source) throws SQLException {
        Map<Long, Model> result = new LinkedHashMap<>();
        try (PreparedStatement q = source.prepareStatement("select id,name from notetypes order by id");
             ResultSet rows = q.executeQuery();
             PreparedStatement fields = source.prepareStatement("select name from fields where ntid=? order by ord")) {
            while (rows.next()) {
                long id = rows.getLong(1);
                fields.setLong(1, id);
                List<String> names = new ArrayList<>();
                try (ResultSet f = fields.executeQuery()) { while (f.next()) names.add(f.getString(1)); }
                result.put(id, new Model(rows.getString(2), List.copyOf(names)));
            }
        }
        return result;
    }

    static String category(String model, List<String> decks) {
        if (decks.stream().anyMatch(d -> d.contains("종합 실전") || d.contains("종합실전") || d.contains("모의") || d.contains("mock"))) return "COMPREHENSIVE";
        if (model.equals("JLPT MAX덱 어휘")) return "VOCABULARY";
        if (model.equals("JLPT MAX덱 문법")) return "GRAMMAR";
        if (model.equals("JLPT MAX덱 어휘문제") || model.contains("문제") || model.contains("question")) return "PRACTICE";
        if (model.equals("JLPT MAX덱 참조표") || model.contains("참조표")) return "REFERENCE";
        return "OTHER";
    }

    private boolean isAudioField(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).contains("audio") || name.equals("MediaIntentsV2")
                || name.toLowerCase(java.util.Locale.ROOT).contains("media");
    }

    private String normalized(String category, Map<String, String> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        String rawLevel = category.equals("GRAMMAR") ? fields.get("Level") : fields.get("WordJLPT");
        if (rawLevel == null || rawLevel.isBlank()) rawLevel = fields.getOrDefault("JLPT", "");
        Matcher level = LEVEL.matcher(rawLevel.trim());
        if (level.matches()) result.put("jlpt", level.group(1).toUpperCase(java.util.Locale.ROOT));
        if (category.equals("VOCABULARY")) {
            for (String key : List.of("EntryID", "Word", "Reading", "Meaning", "PartOfSpeech"))
                result.put(key, AnkiFieldTextNormalizer.text(fields.get(key)));
        } else if (category.equals("GRAMMAR") || fields.containsKey("FrontHTML")) {
            ParsedGrammar grammar = grammarParser.parse(fields.get("FrontHTML"), fields.get("BackHTML"),
                    fields.get("Kind"), fields.get("UnitID"));
            result.put("pattern", grammar.pattern());
            result.put("explanation", grammar.explanation());
            result.put("connection", grammar.connection());
        } else if (category.equals("PRACTICE") || category.equals("COMPREHENSIVE")) {
            for (String key : List.of("QuestionType", "PromptJP", "PromptKO", "AnswerJP", "AnswerKO", "ExplanationHTML"))
                result.put(key, AnkiFieldTextNormalizer.text(fields.get(key)));
        } else if (category.equals("REFERENCE")) {
            for (String key : List.of("Title", "PartLabel", "TableKind")) result.put(key, AnkiFieldTextNormalizer.text(fields.get(key)));
        }
        return encode(result);
    }

    private String encode(Object value) {
        return json.writeValueAsString(value);
    }

    private record Model(String name, List<String> fields) { }
    public record Summary(long notes, long inserted, long skipped, long malformed, long multiCardNotes,
                          long audioReferenceNotes, long rawFields, Map<String, Long> categories,
                          Map<String, Long> decks, Map<String, Long> noteTypes) { }
}
