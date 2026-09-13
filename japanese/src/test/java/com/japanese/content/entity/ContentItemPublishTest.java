package com.japanese.content.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ContentItemPublishTest {

    @Test
    void wordCannotBePublishedWithoutMeaning() {
        ContentItem item = new ContentItem("word-without-meaning", ContentType.WORD, "test", false);
        item.attachWord(new Word("食べる", "たべる", "동사", null));

        assertThatThrownBy(item::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("단어 의미");
    }

    @Test
    void wordCannotBePublishedWithBlankMeaning() {
        ContentItem item = new ContentItem("word-blank-meaning", ContentType.WORD, "test", false);
        Word word = new Word("食べる", "たべる", "동사", null);
        word.addMeaning(new Meaning("ko", " ", 0));
        item.attachWord(word);

        assertThatThrownBy(item::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("단어 의미");
    }

    @Test
    void grammarCannotBePublishedWithoutExplanation() {
        ContentItem item = new ContentItem("grammar-without-explanation", ContentType.GRAMMAR, "test", false);
        item.attachGrammar(new Grammar("〜ながら", " ", "동사 ます형 + ながら"));

        assertThatThrownBy(item::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("문법 패턴과 설명");
    }
}
