package com.japanese.config;

import com.japanese.content.entity.Category;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Example;
import com.japanese.content.entity.Grammar;
import com.japanese.content.entity.Level;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.Word;
import com.japanese.content.repository.CategoryRepository;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.LevelRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("sample")
public class SampleContentDataLoader implements CommandLineRunner {

    private final ContentItemRepository contentItemRepository;
    private final CategoryRepository categoryRepository;
    private final LevelRepository levelRepository;

    public SampleContentDataLoader(
            ContentItemRepository contentItemRepository,
            CategoryRepository categoryRepository,
            LevelRepository levelRepository
    ) {
        this.contentItemRepository = contentItemRepository;
        this.categoryRepository = categoryRepository;
        this.levelRepository = levelRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Category jlpt = category("jlpt", "JLPT");
        Category dailyLife = category("daily-life", "일상생활");
        Category it = category("it", "IT");
        Level jlptN5 = level("JLPT", "N5", "JLPT N5");

        createWord(
                "taberu", "食べる", "たべる", "일단동사 · 타동사", "2",
                "먹다", "毎朝、朝ご飯を食べます。", "まいあさ、あさごはんをたべます。", "매일 아침밥을 먹습니다.",
                jlptN5, jlpt, dailyLife
        );
        createGrammar(
                "temo-ii", "〜てもいい", "허가를 구하거나 허가를 나타낼 때 사용합니다.", "동사 て형 + もいい",
                "ここで写真を撮ってもいいですか。", "ここでしゃしんをとってもいいですか。", "여기서 사진을 찍어도 됩니까?",
                jlptN5, jlpt, dailyLife
        );
        createWord(
                "server", "サーバー", "サーバー", "명사", "-",
                "서버", "このサーバーは安定しています。", "このサーバーはあんていしています。", "이 서버는 안정적입니다.",
                null, it
        );
    }

    private void createWord(
            String slug,
            String expression,
            String reading,
            String partOfSpeech,
            String pitchAccent,
            String meaningText,
            String japaneseExample,
            String exampleReading,
            String translation,
            Level level,
            Category... categories
    ) {
        if (contentItemRepository.findBySlug(slug).isPresent()) {
            return;
        }
        ContentItem item = new ContentItem(slug, ContentType.WORD, "sample", true);
        Word word = new Word(expression, reading, partOfSpeech, pitchAccent);
        Meaning meaning = new Meaning("ko", meaningText, 1);
        word.addMeaning(meaning);
        Example example = new Example(japaneseExample, exampleReading, translation, 1);
        example.setMeaning(meaning);
        item.attachWord(word);
        item.addExample(example);
        addMetadata(item, level, categories);
        contentItemRepository.save(item);
    }

    private void createGrammar(
            String slug,
            String pattern,
            String explanation,
            String connection,
            String japaneseExample,
            String exampleReading,
            String translation,
            Level level,
            Category... categories
    ) {
        if (contentItemRepository.findBySlug(slug).isPresent()) {
            return;
        }
        ContentItem item = new ContentItem(slug, ContentType.GRAMMAR, "sample", true);
        item.attachGrammar(new Grammar(pattern, explanation, connection));
        item.addExample(new Example(japaneseExample, exampleReading, translation, 1));
        addMetadata(item, level, categories);
        contentItemRepository.save(item);
    }

    private void addMetadata(ContentItem item, Level level, Category... categories) {
        if (level != null) {
            item.addLevel(level);
        }
        for (Category category : categories) {
            item.addCategory(category);
        }
    }

    private Category category(String slug, String name) {
        return categoryRepository.findBySlug(slug)
                .orElseGet(() -> categoryRepository.save(new Category(slug, name)));
    }

    private Level level(String system, String code, String name) {
        return levelRepository.findBySystemAndCode(system, code)
                .orElseGet(() -> levelRepository.save(new Level(system, code, name)));
    }
}
