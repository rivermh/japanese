package com.japanese.learning.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CharacterGrowthStageTest {

    @Test
    void usesTotalExperienceInsteadOfJlptLevelForGrowth() {
        assertThat(CharacterGrowthStage.forExperience(0)).isEqualTo(CharacterGrowthStage.YOUNG);
        assertThat(CharacterGrowthStage.forExperience(299)).isEqualTo(CharacterGrowthStage.YOUNG);
        assertThat(CharacterGrowthStage.forExperience(300)).isEqualTo(CharacterGrowthStage.APPRENTICE);
        assertThat(CharacterGrowthStage.forExperience(1_000)).isEqualTo(CharacterGrowthStage.CONFIDENT);
        assertThat(CharacterGrowthStage.forExperience(2_500)).isEqualTo(CharacterGrowthStage.RELIABLE);
        assertThat(CharacterGrowthStage.APPRENTICE.next()).isEqualTo(CharacterGrowthStage.CONFIDENT);
        assertThat(CharacterGrowthStage.RELIABLE.next()).isNull();
    }
}
