package com.japanese.content.importer;

/**
 * Pitch accent data extracted from the raw PitchAccent HTML fragment
 * ({@code data-pitch-terminal-states="..."} and {@code j1="..."}). Absent (null) whenever the
 * raw field is blank or does not match the expected shape - callers should look for a
 * {@link VocabularyNormalizationIssue#PITCH_ACCENT_PARSE_FAILED} warning to tell those two
 * cases apart.
 */
public record NormalizedPitchAccent(String terminalStates, String mora) {
    public NormalizedPitchAccent {
        if (terminalStates == null || terminalStates.isBlank()) {
            throw new IllegalArgumentException("terminalStates is required");
        }
        if (mora == null || mora.isBlank()) {
            throw new IllegalArgumentException("mora is required");
        }
    }
}
