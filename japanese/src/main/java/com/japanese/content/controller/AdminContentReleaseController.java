package com.japanese.content.controller;

import com.japanese.content.service.ContentReleaseBatchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/content-release/batches")
public class AdminContentReleaseController {
    private final ContentReleaseBatchService batches;

    public AdminContentReleaseController(ContentReleaseBatchService batches) { this.batches = batches; }

    @GetMapping
    public String history(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("history", batches.history(page));
        return "admin/content-batch-history";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("batch", batches.batch(id));
        return "admin/content-batch-detail";
    }
}
