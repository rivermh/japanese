package com.japanese.learning.dto;

public record CharacterStatus(
        String characterKey,
        String characterName,
        String characterDescription,
        String assetDirectory,
        String illustrationPath,
        String stageKey,
        String stageName,
        String stageDescription,
        int level,
        int experience,
        int nextStageExperience,
        int experienceToNextStage,
        int stageProgressPercent,
        boolean growthNoticePending,
        int stageNumber,
        String animationStageKey,
        Integer assetStage,
        boolean fallbackUsed,
        boolean supportsBlink,
        String animationManifestPath,
        boolean finalStage,
        boolean growthPresentationPending,
        String pendingGrowthStageKey
) {
}
