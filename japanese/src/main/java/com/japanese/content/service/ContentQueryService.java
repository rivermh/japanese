package com.japanese.content.service;

import com.japanese.content.dto.ContentFilterOptions;
import com.japanese.content.dto.ContentSearchPage;
import com.japanese.content.dto.SourceDetails;
import com.japanese.content.dto.ContentSummary;
import com.japanese.content.dto.ContentDetails;
import com.japanese.content.dto.CategoryOverview;
import com.japanese.content.entity.ContentItem;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentType;
import com.japanese.content.entity.Meaning;
import com.japanese.content.entity.Example;
import com.japanese.content.search.ContentSearchNormalizer;
import com.japanese.content.repository.ContentItemRepository;
import com.japanese.content.repository.CategoryRepository;
import com.japanese.content.repository.LevelRepository;
import com.japanese.content.repository.ContentSourceRepository;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentQueryService {

    private static final int MAX_RESULTS = 50;

    private final ContentItemRepository contentItemRepository;
    private final CategoryRepository categoryRepository;
    private final LevelRepository levelRepository;
    private final ContentSourceRepository contentSourceRepository;
    private final GrammarLearningService grammarLearningService;

    public ContentQueryService(
            ContentItemRepository contentItemRepository,
            CategoryRepository categoryRepository,
            LevelRepository levelRepository,
            ContentSourceRepository contentSourceRepository,
            GrammarLearningService grammarLearningService
    ) {
        this.contentItemRepository = contentItemRepository;
        this.categoryRepository = categoryRepository;
        this.levelRepository = levelRepository;
        this.contentSourceRepository = contentSourceRepository;
        this.grammarLearningService = grammarLearningService;
    }

    @Transactional(readOnly = true)
    public List<ContentSummary> search(String keyword) {
        return search(keyword, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<ContentSummary> search(
            String keyword,
            ContentType type,
            String levelCode,
        String categorySlug
    ) {
        return searchPage(keyword, type, levelCode, categorySlug, 0, MAX_RESULTS).contents();
    }

    @Transactional(readOnly = true)
    public ContentSearchPage searchPage(
            String keyword,
            ContentType type,
            String levelCode,
            String categorySlug,
            int page,
            int size
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_RESULTS);
        Page<ContentItem> result = contentItemRepository.searchPublished(
                        normalizedKeyword,
                        type,
                        normalizeFilter(levelCode),
                        normalizeFilter(categorySlug),
                        PageRequest.of(normalizedPage, normalizedSize)
                );
        return new ContentSearchPage(
                result.getContent().stream()
                .map(this::toSummary)
                .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious());
    }

    @Transactional(readOnly = true)
    public ContentFilterOptions filterOptions() {
        return new ContentFilterOptions(
                List.of(ContentType.WORD, ContentType.GRAMMAR),
                levelRepository.findAllByOrderBySystemAscCodeAsc().stream()
                        .map(level -> new ContentFilterOptions.LevelOption(
                                level.getSystem(), level.getCode(), level.getName()))
                        .toList(),
                categoryRepository.findAllByOrderByNameAsc().stream()
                        .map(category -> new ContentFilterOptions.CategoryOption(
                                category.getSlug(), category.getName()))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<SourceDetails> sources() {
        return contentSourceRepository.findAllByOrderByDisplayNameAsc().stream()
                .map(this::toSourceDetails)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryOverview> categoryOverview() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> new CategoryOverview(
                        category.getSlug(),
                        category.getName(),
                        contentItemRepository.countByPublishedTrueAndCategoriesSlug(category.getSlug())))
                .toList();
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = ContentSearchNormalizer.normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    @Transactional(readOnly = true)
    public Optional<ContentDetails> findBySlug(String slug) {
        return contentItemRepository.findBySlugAndPublishedTrue(slug).map(this::toDetails);
    }

    private ContentDetails toDetails(ContentItem item) {
        ContentSummary summary = toSummary(item);
        List<ContentDetails.MeaningDetails> meanings = item.getWord() == null
                ? List.of()
                : item.getWord().getMeanings().stream()
                .map(meaning -> new ContentDetails.MeaningDetails(
                        meaning.getLanguageTag(), meaning.getText(), meaning.getSenseOrder()))
                .toList();
        List<ContentDetails.ExampleDetails> examples = item.getExamples().stream()
                .map(this::toExampleDetails)
                .toList();
        Map<Meaning, List<ContentDetails.ExampleDetails>> examplesByMeaning = new LinkedHashMap<>();
        if (item.getWord() != null) {
            item.getWord().getMeanings().forEach(meaning -> examplesByMeaning.put(meaning, new java.util.ArrayList<>()));
        }
        List<ContentDetails.ExampleDetails> unlinkedExamples = new java.util.ArrayList<>();
        for (Example example : item.getExamples()) {
            ContentDetails.ExampleDetails details = toExampleDetails(example);
            if (example.getMeaning() != null && examplesByMeaning.containsKey(example.getMeaning())) {
                examplesByMeaning.get(example.getMeaning()).add(details);
            } else {
                unlinkedExamples.add(details);
            }
        }
        List<ContentDetails.MeaningGroup> meaningGroups = item.getWord() == null
                ? List.of()
                : item.getWord().getMeanings().stream()
                .map(meaning -> new ContentDetails.MeaningGroup(
                        new ContentDetails.MeaningDetails(meaning.getLanguageTag(), meaning.getText(), meaning.getSenseOrder()),
                        List.copyOf(examplesByMeaning.get(meaning))))
                .toList();
        ContentDetails.GrammarDetails grammar = item.getGrammar() == null
                ? null
                : new ContentDetails.GrammarDetails(
                        item.getGrammar().getPattern(),
                        item.getGrammar().getExplanation(),
                        item.getGrammar().getConnection(),
                        grammarLearningService.publicDetails(item.getGrammar()));
        return new ContentDetails(
                summary,
                toSourceDetails(item.getSourceRef()),
                item.getWord() == null ? null : item.getWord().getPartOfSpeech(),
                item.getWord() == null ? null : item.getWord().getPitchAccent(),
                meanings,
                examples,
                meaningGroups,
                List.copyOf(unlinkedExamples),
                grammar,
                pitchAccentDisplay(item.getWord() == null ? null : item.getWord().getPitchAccent()));
    }

    private ContentDetails.ExampleDetails toExampleDetails(Example example) {
        return new ContentDetails.ExampleDetails(
                example.getMeaning() == null ? null : example.getMeaning().getText(),
                example.getJapaneseText(),
                example.getReading(),
                example.getTranslation(),
                example.getAudioFileName(),
                example.getDisplayOrder());
    }

    private ContentDetails.PitchAccentDisplay pitchAccentDisplay(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }
        if (value.matches("\\d+")) {
            int downstep = Integer.parseInt(value);
            String label = downstep == 0 ? "0형 · 평판형" : downstep + "형 · " + downstep + "번째 모라 뒤 하강";
            return new ContentDetails.PitchAccentDisplay(label, downstep, false);
        }
        if (value.startsWith("terminal=") && value.contains(";mora=")) {
            return new ContentDetails.PitchAccentDisplay("원본 악센트 정보", null, true);
        }
        return new ContentDetails.PitchAccentDisplay("원본 악센트 표기", null, true);
    }

    private SourceDetails toSourceDetails(String sourceRef) {
        if (sourceRef == null) {
            return null;
        }
        return contentSourceRepository.findBySourceRef(sourceRef)
                .map(this::toSourceDetails)
                .orElse(null);
    }

    private SourceDetails toSourceDetails(ContentSource source) {
        return new SourceDetails(
                source.getSourceRef(),
                source.getDisplayName(),
                source.getVersion(),
                source.getLicenseSummary(),
                source.getLicenseUrl(),
                source.getAttribution(),
                source.getUsageNote());
    }

    private ContentSummary toSummary(ContentItem item) {
        String title;
        String reading = null;
        String description;
        if (item.getType() == ContentType.WORD) {
            title = item.getWord().getExpression();
            reading = item.getWord().getReading();
            description = item.getWord().getMeanings().stream()
                    .filter(meaning -> "ko".equals(meaning.getLanguageTag()))
                    .findFirst()
                    .map(Meaning::getText)
                    .orElse("");
        } else {
            title = item.getGrammar().getPattern();
            description = item.getGrammar().getExplanation();
        }

        return new ContentSummary(
                item.getId(),
                item.getSlug(),
                item.getType(),
                title,
                reading,
                description,
                item.getLevels().stream()
                        .map(level -> new ContentSummary.LevelSummary(level.getSystem(), level.getCode(), level.getName()))
                        .toList(),
                item.getCategories().stream()
                        .map(category -> new ContentSummary.CategorySummary(category.getSlug(), category.getName()))
                        .toList()
        );
    }
}
