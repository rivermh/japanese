package com.japanese.content.controller;

import com.japanese.content.dto.ContentSummary;
import com.japanese.content.dto.ContentDetails;
import com.japanese.content.dto.ContentFilterOptions;
import com.japanese.content.dto.ContentSearchPage;
import com.japanese.content.dto.SourceDetails;
import com.japanese.content.dto.CategoryOverview;
import com.japanese.content.entity.ContentType;
import com.japanese.content.service.ContentQueryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contents")
public class ContentApiController {

    private final ContentQueryService contentQueryService;

    public ContentApiController(ContentQueryService contentQueryService) {
        this.contentQueryService = contentQueryService;
    }

    @GetMapping
    public List<ContentSummary> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category
    ) {
        return contentQueryService.search(keyword, type, level, category);
    }

    @GetMapping("/page")
    public ContentSearchPage searchPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return contentQueryService.searchPage(keyword, type, level, category, page, size);
    }

    @GetMapping("/filters")
    public ContentFilterOptions filters() {
        return contentQueryService.filterOptions();
    }

    @GetMapping("/sources")
    public List<SourceDetails> sources() {
        return contentQueryService.sources();
    }

    @GetMapping("/categories")
    public List<CategoryOverview> categories() {
        return contentQueryService.categoryOverview();
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ContentDetails> findBySlug(@org.springframework.web.bind.annotation.PathVariable String slug) {
        return ResponseEntity.of(contentQueryService.findBySlug(slug));
    }
}
