package com.japanese.content.controller;

import com.japanese.content.dto.AdminContentSourceRightsModels.SourceRightsView;
import com.japanese.content.entity.ContentSource;
import com.japanese.content.entity.ContentSourceRightsStatus;
import com.japanese.content.service.ContentSourceRightsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/content-sources")
public class AdminContentSourceRightsApiController {

    private final ContentSourceRightsService rights;

    public AdminContentSourceRightsApiController(ContentSourceRightsService rights) {
        this.rights = rights;
    }

    public record RightsReviewRequest(
            @NotNull ContentSourceRightsStatus status,
            @NotBlank @Size(max = 2000) String note,
            Boolean attributionRequired,
            @Size(max = 2000) String attributionText
    ) {
    }

    @GetMapping
    public List<SourceRightsView> sources() {
        return rights.sources().stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    public SourceRightsView source(@PathVariable Long id) {
        return view(rights.source(id));
    }

    @PostMapping("/{id}/rights")
    public SourceRightsView reviewRights(
            @PathVariable Long id,
            @Valid @RequestBody RightsReviewRequest request) {
        return view(rights.reviewRights(
                id,
                request.status(),
                request.note(),
                request.attributionRequired(),
                request.attributionText()));
    }

    private SourceRightsView view(ContentSource source) {
        return SourceRightsView.from(source, rights.releaseEligibilityForSource(source));
    }
}
