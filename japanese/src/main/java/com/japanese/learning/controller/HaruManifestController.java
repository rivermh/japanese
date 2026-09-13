package com.japanese.learning.controller;

import com.japanese.learning.character.HaruAssetResolver;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HaruManifestController {
    private final HaruAssetResolver assets;
    public HaruManifestController(HaruAssetResolver assets) { this.assets = assets; }
    @GetMapping(HaruAssetResolver.MANIFEST)
    public Map<String, Object> manifest() { return assets.manifest(); }
}
