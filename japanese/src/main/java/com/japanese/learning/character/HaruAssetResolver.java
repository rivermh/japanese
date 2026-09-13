package com.japanese.learning.character;

import com.japanese.learning.entity.CharacterGrowthStage;
import java.util.*;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Resolves only installed assets; domain stage is never downgraded by a missing image. */
@Component
public class HaruAssetResolver {
    public static final String DIRECTORY = "/images/characters/haru";
    public static final String MANIFEST = DIRECTORY + "/animation.json";
    private final ResourceLoader resources;
    public HaruAssetResolver(ResourceLoader resources) { this.resources = resources; }
    public record Asset(Integer stage, String path, boolean fallbackUsed, boolean supportsBlink) {}
    private boolean exists(String name) {
        return resources.getResource("classpath:/static" + DIRECTORY + "/" + name).exists();
    }
    public Asset resolve(CharacterGrowthStage stage) {
        int requested = stage.ordinal() + 1;
        for (int n = requested; n >= 1; n--) {
            String prefix = "haru-stage-" + n;
            if (exists(prefix + ".png")) {
                boolean blink = exists(prefix + "-blink-01.png") && exists(prefix + "-blink-02.png")
                        && exists(prefix + "-blink-03.png");
                return new Asset(n, DIRECTORY + "/" + prefix + ".png", n != requested, blink);
            }
        }
        return new Asset(null, null, true, false);
    }
    public Map<String, Object> manifest() {
        Map<String, Object> stages = new LinkedHashMap<>();
        for (CharacterGrowthStage stage : CharacterGrowthStage.values()) {
            Asset asset = resolve(stage);
            if (asset.path() == null) continue;
            String prefix = "haru-stage-" + asset.stage();
            String poster = prefix + ".png";
            Map<String, Object> states = new LinkedHashMap<>();
            states.put("idle", Map.of("poster", poster, "assetStatus", "available"));
            for (String state : List.of("study", "happy", "goal-complete", "growth")) {
                String specific = prefix + "-" + state + ".png";
                String motion = state.equals("study") ? "focus" : state.equals("happy") ? "happy" : "celebrate";
                states.put(state, Map.of("poster", exists(specific) ? specific : poster,
                        "fallback", "idle", "motion", motion, "assetStatus", "motion-only", "returnToIdle", !state.equals("study")));
            }
            states.put("look-around", Map.of("fallback", "idle", "assetStatus", "required"));
            List<Object> ambient = new ArrayList<>();
            ambient.add(Map.of("motion", "breathe", "weight", 4));
            ambient.add(Map.of("motion", "settle", "weight", 1));
            if (asset.supportsBlink()) {
                states.put("blink", Map.of("poster", poster, "assetStatus", "available", "returnToIdle", true,
                        "frames", List.of(frame(poster, 90), frame(prefix + "-blink-01.png", 70),
                                frame(prefix + "-blink-02.png", 110), frame(prefix + "-blink-03.png", 70), frame(poster, 120))));
                ambient.add(Map.of("state", "blink", "weight", 2));
            } else states.put("blink", Map.of("fallback", "idle", "assetStatus", "required"));
            stages.put("stage-" + (stage.ordinal() + 1), Map.of("idle", Map.of("asset", poster), "states", states,
                    "ambient", ambient, "ambientDelayMs", Map.of("min", 8000, "max", 16000)));
        }
        return Map.of("version", 2, "character", "haru", "stages", stages,
                "frameDefaults", Map.of("durationMs", 140, "crossfadeMs", 90),
                "motionPresets", Map.of("breathe", Map.of("durationMs", 3600), "settle", Map.of("durationMs", 2400),
                        "focus", Map.of("durationMs", 1800), "happy", Map.of("durationMs", 1100), "celebrate", Map.of("durationMs", 1800)));
    }
    private Map<String, Object> frame(String asset, int duration) { return Map.of("asset", asset, "durationMs", duration); }
}
