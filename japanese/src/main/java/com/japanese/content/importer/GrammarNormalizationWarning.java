package com.japanese.content.importer;

/** One detected problem, human-readable, tagged with the fixed severity of its {@link GrammarNormalizationIssue}. */
public record GrammarNormalizationWarning(GrammarNormalizationIssue issue, String message) {

    public GrammarNormalizationWarning {
        if (issue == null) throw new IllegalArgumentException("issue is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message is required");
    }

    public GrammarNormalizationSeverity severity() {
        return issue.severity();
    }
}
