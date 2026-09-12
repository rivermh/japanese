package com.japanese.learning.character;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CharacterCatalog {

    private static final List<CharacterDefinition> CHARACTERS = List.of(
            new CharacterDefinition("haru", "하루", "차분하게 매일의 학습을 함께하는 견습 파트너", "/images/characters/haru", true),
            new CharacterDefinition("mio", "미오", "호기심을 따라 새 표현을 찾아보는 학습 파트너", "/images/characters/mio", false));

    private static final Map<String, CharacterDefinition> BY_KEY = CHARACTERS.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(CharacterDefinition::key, value -> value));

    public List<CharacterDefinition> all() { return CHARACTERS; }
    public Optional<CharacterDefinition> find(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_KEY.get(key.trim().toLowerCase(Locale.ROOT)));
    }
    public CharacterDefinition defaultCharacter() { return BY_KEY.get("haru"); }
    public CharacterDefinition resolve(String key) { return find(key).orElseGet(this::defaultCharacter); }
}
