package com.japanese.learning.dto;

import java.util.List;

public record OnboardingOptions(List<OnboardingOption> jlptLevels,
                                List<OnboardingOption> categories,
                                List<OnboardingPreset> presets) {
}
