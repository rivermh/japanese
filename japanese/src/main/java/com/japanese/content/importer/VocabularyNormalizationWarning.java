package com.japanese.content.importer;

/** One detected problem, human-readable, tagged with the fixed severity of its {@link VocabularyNormalizationIssue}. */
public record VocabularyNormalizationWarning(VocabularyNormalizationIssue issue, String message) {

    public VocabularyNormalizationWarning {
        if (issue == null) throw new IllegalArgumentException("issue is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message is required");
    }

    public VocabularyNormalizationSeverity severity() {
        return issue.severity();
    }
}
