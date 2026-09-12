package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.character.CharacterCatalog;
import com.japanese.learning.character.CharacterDefinition;
import com.japanese.learning.dto.StudyOverview;
import com.japanese.learning.service.LearningService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/characters")
public class CharacterApiController {
    private final CharacterCatalog characterCatalog; private final LearningService learningService; private final CurrentUserService currentUserService;
    public CharacterApiController(CharacterCatalog characterCatalog, LearningService learningService, CurrentUserService currentUserService) { this.characterCatalog=characterCatalog; this.learningService=learningService; this.currentUserService=currentUserService; }
    @GetMapping public List<CharacterDefinition> characters() { return characterCatalog.all(); }
    @PostMapping("/{key}/select") public StudyOverview select(@PathVariable String key) { var account=currentUserService.currentAccount(); learningService.updateCharacter(account, key); return learningService.overview(account); }
    @PostMapping("/growth/acknowledge") public StudyOverview acknowledgeGrowth() { var account=currentUserService.currentAccount(); learningService.acknowledgeGrowth(account); return learningService.overview(account); }
}
