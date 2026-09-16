package com.japanese.content.importer;

/**
 * JLPT level for a vocabulary candidate, keeping the normalized code alongside the raw value it
 * came from so a reviewer can tell "no level present" apart from "level present but unparseable".
 *
 * @param code           normalized N1-N5 code, or null if absent/unparseable - never guessed
 * @param rawValue       the raw field value that was evaluated (WordJLPT or JLPT), or null if
 *                       neither field had a value
 * @param sourceField    which raw field {@code rawValue} came from ("WordJLPT" takes priority over
 *                       "JLPT"), or null if neither was present
 */
public record NormalizedJlptLevel(String code, String rawValue, String sourceField) {
}
