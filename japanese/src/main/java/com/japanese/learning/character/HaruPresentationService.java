package com.japanese.learning.character;

import com.japanese.learning.dto.CharacterStatus;
import com.japanese.learning.entity.CharacterGrowthStage;
import com.japanese.learning.entity.LearnerProfile;
import org.springframework.stereotype.Service;

@Service
public class HaruPresentationService {
    private final HaruAssetResolver assets;
    public HaruPresentationService(HaruAssetResolver assets) { this.assets = assets; }
    public String guestPoster() { return assets.resolve(CharacterGrowthStage.YOUNG).path(); }
    public CharacterStatus status(LearnerProfile profile, CharacterDefinition character) {
        var stage = CharacterGrowthStage.forExperience(profile.getExperience());
        var next = stage.next();
        int nextExperience = next == null ? stage.getRequiredExperience() : next.getRequiredExperience();
        int progress = next == null ? 100 : (int) Math.min(100,
                (long) (profile.getExperience() - stage.getRequiredExperience()) * 100 / (nextExperience - stage.getRequiredExperience()));
        var asset = character.key().equals("haru") ? assets.resolve(stage) : new HaruAssetResolver.Asset(null, null, false, false);
        return new CharacterStatus(character.key(), character.displayName(), character.description(), character.assetDirectory(),
                asset.path(), stage.getKey(), stage.getDisplayName(), stage.getDescription(), profile.getLevel(), profile.getExperience(),
                nextExperience, next == null ? 0 : nextExperience - profile.getExperience(), progress,
                profile.getPendingGrowthStageKey() != null, stage.ordinal() + 1, "stage-" + (stage.ordinal() + 1),
                asset.stage(), asset.fallbackUsed(), asset.supportsBlink(), asset.path() == null ? null : HaruAssetResolver.MANIFEST,
                next == null, profile.isGrowthPresentationPending(), profile.getPendingGrowthStageKey());
    }
}
