package com.japanese.content.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GrammarHtmlParserTest {

    private final GrammarHtmlParser parser = new GrammarHtmlParser();

    @Test
    void extractsPatternExplanationAndExamplesFromDeckHtml() {
        ParsedGrammar grammar = parser.parse(
                "<section><div lang=\"ja\"><mark>〜てもいい</mark></div></section>",
                """
                        <div class=\"_j4z\">허가를 나타내는 표현</div>
                        <section class=\"_j4a\">
                          <div class=\"_jcq\">ここで写真を撮ってもいいです。</div>
                          <div class=\"_j4v\">여기서 사진을 찍어도 됩니다.</div>
                        </section>
                        """,
                "동사 て형 + もいい",
                "unit-1");

        assertThat(grammar.pattern()).isEqualTo("〜てもいい");
        assertThat(grammar.explanation()).isEqualTo("허가를 나타내는 표현");
        assertThat(grammar.connection()).isEqualTo("동사 て형 + もいい");
        assertThat(grammar.examples()).singleElement().satisfies(example -> {
            assertThat(example.japaneseText()).isEqualTo("ここで写真を撮ってもいいです。");
            assertThat(example.translation()).isEqualTo("여기서 사진을 찍어도 됩니다.");
        });
    }

    @Test
    void leavesConnectionEmptyWhenKindIsBlankBecauseUnitIdIsInternalMetadata() {
        ParsedGrammar grammar = parser.parse("<mark>〜そうだ</mark>", "설명", "⁣", "unit-2");

        assertThat(grammar.pattern()).isEqualTo("〜そうだ");
        assertThat(grammar.connection()).isNull();
    }
}
