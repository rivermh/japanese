package com.japanese.content.importer;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Component;

@Component
public class GrammarHtmlParser {

    public ParsedGrammar parse(String frontHtml, String backHtml, String kind, String unitId) {
        Element front = Jsoup.parseBodyFragment(valueOrEmpty(frontHtml)).body();
        Element back = Jsoup.parseBodyFragment(valueOrEmpty(backHtml)).body();
        Element patternElement = front.selectFirst("mark");
        if (patternElement == null) {
            patternElement = front.selectFirst("div[lang=ja]");
        }
        String pattern = patternElement == null ? front.text() : patternElement.text();
        String explanation = text(back.selectFirst("div._j4z"));
        if (explanation.isBlank()) {
            explanation = back.text();
        }
        String connection = AnkiFieldTextNormalizer.text(kind);
        List<ParsedExample> examples = new ArrayList<>();
        for (Element section : back.select("section._j4a")) {
            Element japanese = section.selectFirst("div._jcq");
            Element translation = section.selectFirst("div._j4v");
            if (japanese == null || translation == null || japanese.text().isBlank()) {
                continue;
            }
            examples.add(new ParsedExample(
                    null,
                    textWithoutRuby(japanese),
                    rubyReading(japanese),
                    AnkiFieldTextNormalizer.text(translation.html()),
                    null));
        }
        return new ParsedGrammar(
                limit(pattern, 200),
                limit(explanation, 2000),
                limit(connection, 500),
                List.copyOf(examples));
    }

    private String text(Element element) {
        return element == null ? "" : element.text().trim();
    }

    private String valueOrEmpty(String value) {
        return value == null || "⁣".equals(value) ? "" : value;
    }

    private String textWithoutRuby(Element element) {
        Element copy = element.clone();
        copy.select("rt").remove();
        return AnkiFieldTextNormalizer.text(copy.html());
    }

    private String rubyReading(Element element) {
        String reading = element.select("ruby rt").text().trim();
        return reading.isBlank() ? null : reading;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
