package com.japanese.content.importer;

import java.util.List;

public record ParsedGrammar(
        String pattern,
        String explanation,
        String connection,
        List<ParsedExample> examples
) {
}
