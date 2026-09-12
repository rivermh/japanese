package com.japanese.content.importer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/** Converts Anki display HTML into plain stored text without changing the raw source record. */
public final class AnkiFieldTextNormalizer {
    private AnkiFieldTextNormalizer() { }

    public static String text(String raw) {
        if (raw == null) return null;
        String source = raw.replace("\u2063", "").replace("\u200b", "").replace("\ufeff", "");
        if (source.isBlank()) return null;
        Element body = Jsoup.parseBodyFragment(source).body();
        String value = collect(body).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return value.isBlank() ? null : value;
    }

    private static String collect(Node node) {
        if (node instanceof TextNode text) return text.getWholeText();
        if (!(node instanceof Element element)) return "";
        if ("rt".equals(element.tagName())) return "";
        StringBuilder result = new StringBuilder();
        for (Node child : element.childNodes()) result.append(collect(child));
        if ("br".equals(element.tagName()) || "div".equals(element.tagName()) || "p".equals(element.tagName())
                || "li".equals(element.tagName())) result.append(' ');
        return result.toString();
    }
}
