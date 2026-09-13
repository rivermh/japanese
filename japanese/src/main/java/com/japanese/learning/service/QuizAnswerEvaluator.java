package com.japanese.learning.service;

import com.japanese.learning.entity.QuizQuestionType;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class QuizAnswerEvaluator {
    public boolean matches(QuizQuestionType type, String submitted, String expected) {
        return type.isInput()
                ? normalizeReading(submitted).equals(normalizeReading(expected))
                : normalizeText(submitted).equals(normalizeText(expected));
    }

    public String normalizeText(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim()
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public String normalizeReading(String value) {
        String normalized = normalizeText(value).replace(" ", "");
        StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            result.append(character >= 'ァ' && character <= 'ヶ' ? (char) (character - 0x60) : character);
        }
        return result.toString();
    }
}
