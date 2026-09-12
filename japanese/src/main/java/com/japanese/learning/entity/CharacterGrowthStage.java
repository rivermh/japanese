package com.japanese.learning.entity;

public enum CharacterGrowthStage {
    YOUNG("young", "어린 학습자", "호기심으로 공부를 시작한 어린 하루", 0),
    APPRENTICE("apprentice", "자라나는 학습자", "공부 습관과 함께 조금 성장한 하루", 300),
    CONFIDENT("confident", "청년 학습자", "스스로 배움의 방향을 찾아가는 청년 하루", 1_000),
    RELIABLE("reliable", "성숙한 학습 파트너", "오랜 공부를 함께한 성인 하루", 2_500);

    private final String key; private final String displayName; private final String description; private final int requiredExperience;
    CharacterGrowthStage(String key, String displayName, String description, int requiredExperience) { this.key=key; this.displayName=displayName; this.description=description; this.requiredExperience=requiredExperience; }
    public static CharacterGrowthStage forExperience(int experience) {
        CharacterGrowthStage current=YOUNG;
        for (CharacterGrowthStage stage: values()) if (experience >= stage.requiredExperience) current=stage;
        return current;
    }
    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getRequiredExperience() { return requiredExperience; }
    public CharacterGrowthStage next() { int nextOrdinal=ordinal()+1; return nextOrdinal<values().length?values()[nextOrdinal]:null; }
}
