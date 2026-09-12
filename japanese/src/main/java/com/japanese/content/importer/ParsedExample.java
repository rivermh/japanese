package com.japanese.content.importer;

public record ParsedExample(
        String meaningLabel,
        String japaneseText,
        String reading,
        String translation,
        String audioFileName
) {
}
