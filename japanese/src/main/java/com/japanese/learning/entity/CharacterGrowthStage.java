package com.japanese.learning.entity;

public enum CharacterGrowthStage {
    YOUNG("young", "어린 Haru", "아직 서툴지만 조금씩 균형을 잡아 가는 친구", 0),
    APPRENTICE("apprentice", "자라나는 Haru", "자신 있게 뛰기 시작한 활발한 어린 친구", 300),
    CONFIDENT("confident", "함께 걷는 Haru", "안정된 걸음으로 공부를 함께하는 친구", 1_000),
    RELIABLE("reliable", "든든한 Haru", "꾸준한 공부를 함께해 온 믿음직한 학습 파트너", 2_500);

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
