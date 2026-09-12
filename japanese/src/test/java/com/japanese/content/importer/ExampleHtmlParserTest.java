package com.japanese.content.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExampleHtmlParserTest {

    private final ExampleHtmlParser parser = new ExampleHtmlParser();

    @Test
    void extractsPlainTextRubyTranslationAndSafeAudioReference() {
        String html = """
                <section class="_j47" aria-label="뜻 묶음: 먹다">
                  <div class="_j48" lang="ja"><ruby><rb>毎朝</rb><rt>まいあさ</rt></ruby>ご飯を食べます。</div>
                  <div class="_jau" lang="ja"><ruby><rb>毎朝</rb><rt>まいあさ</rt></ruby>ご飯を食べます。</div>
                  <div class="_jn"><audio src="jlpt-v2-example-ci-abc.mp3"></audio></div>
                  <div class="_j49">매일 아침밥을 먹습니다.</div>
                </section>
                """;

        List<ParsedExample> examples = parser.parse(html);

        assertThat(examples).singleElement().satisfies(example -> {
            assertThat(example.meaningLabel()).isEqualTo("먹다");
            assertThat(example.japaneseText()).isEqualTo("毎朝ご飯を食べます。");
            assertThat(example.reading()).isEqualTo("まいあさご飯を食べます。");
            assertThat(example.translation()).isEqualTo("매일 아침밥을 먹습니다.");
            assertThat(example.audioFileName()).isEqualTo("jlpt-v2-example-ci-abc.mp3");
        });
    }

    @Test
    void ignoresUnsupportedMarkupAndInvalidAudioPaths() {
        String html = """
                <section class="_j47" aria-label="뜻 묶음: 테스트">
                  <div class="_j48" lang="ja">テスト</div>
                  <audio src="../private.mp3"></audio>
                  <div class="_j49">테스트</div>
                </section>
                """;

        assertThat(parser.parse(html)).singleElement()
                .extracting(ParsedExample::audioFileName)
                .isNull();
    }
}
