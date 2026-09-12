package com.japanese.content.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

@Component
public class ExampleHtmlParser {

    private static final Pattern MEDIA_FILE_NAME = Pattern.compile("[A-Za-z0-9_.-]+\\.(mp3|ogg|wav|m4a)");

    public List<ParsedExample> parse(String renderedHtml) {
        if (renderedHtml == null || renderedHtml.isBlank()) {
            return List.of();
        }

        List<ParsedExample> result = new ArrayList<>();
        for (Element section : Jsoup.parseBodyFragment(renderedHtml).select("section._j47")) {
            Element japanese = section.selectFirst("div._j48[lang=ja]");
            Element reading = section.selectFirst("div._jau[lang=ja]");
            Element translation = section.selectFirst("div._j49");
            if (japanese == null || translation == null) {
                continue;
            }

            String audioFileName = null;
            Element audio = section.selectFirst("audio[src]");
            if (audio != null && MEDIA_FILE_NAME.matcher(audio.attr("src")).matches()) {
                audioFileName = audio.attr("src");
            }
            result.add(new ParsedExample(
                    meaningLabel(section.attr("aria-label")),
                    AnkiFieldTextNormalizer.text(textWithoutRuby(japanese)),
                    reading == null ? null : AnkiFieldTextNormalizer.text(rubyReading(reading)),
                    AnkiFieldTextNormalizer.text(translation.html()),
                    audioFileName
            ));
        }
        return List.copyOf(result);
    }

    private String meaningLabel(String ariaLabel) {
        int separator = ariaLabel.indexOf(':');
        return separator < 0 ? ariaLabel : ariaLabel.substring(separator + 1).trim();
    }

    private String textWithoutRuby(Element element) {
        return collectText(element, false).trim();
    }

    private String rubyReading(Element element) {
        return collectText(element, true).trim();
    }

    private String collectText(Node node, boolean reading) {
        if (node instanceof TextNode textNode) {
            return textNode.getWholeText();
        }
        if (!(node instanceof Element element)) {
            return "";
        }
        if ("rt".equals(element.tagName())) {
            return reading ? element.text() : "";
        }
        if ("ruby".equals(element.tagName())) {
            if (reading) {
                return element.select("rt").text();
            }
            return element.select("rb").text();
        }
        StringBuilder result = new StringBuilder();
        for (Node child : element.childNodes()) {
            result.append(collectText(child, reading));
        }
        return result.toString();
    }
}
