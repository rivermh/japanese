package com.japanese.content.search;

import java.text.Normalizer;
import java.util.Locale;

/** Produces a stable search key without changing source text shown to users. */
public final class ContentSearchNormalizer {

    private ContentSearchNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        StringBuilder kanaNormalized = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '~' || character == 0x301c || character == 0x223c) {
                kanaNormalized.append((char) 0x301c);
            } else if (character >= 0x30a1 && character <= 0x30f6) {
                kanaNormalized.append((char) (character - 0x60));
            } else {
                kanaNormalized.append(character);
            }
        }
        return kanaNormalized.toString();
    }
}
