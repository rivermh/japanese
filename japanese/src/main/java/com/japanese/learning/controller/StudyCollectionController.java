package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.entity.StudyResult;
import com.japanese.learning.service.LearningService;
import com.japanese.learning.service.StudyCollectionService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class StudyCollectionPageController {
    private final StudyCollectionService collectionService;
    private final LearningService learningService;
    private final CurrentUserService currentUserService;

    StudyCollectionPageController(StudyCollectionService collectionService, LearningService learningService,
                                  CurrentUserService currentUserService) {
        this.collectionService = collectionService;
        this.learningService = learningService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/collections")
    String list(Model model) {
        model.addAttribute("collections", collectionService.list(currentUserService.currentAccount()));
        return "collections";
    }

    @PostMapping("/collections")
    String create(@RequestParam String name, RedirectAttributes attributes) {
        var collection = collectionService.create(currentUserService.currentAccount(), name);
        attributes.addFlashAttribute("collectionMessage", "단어장을 만들었습니다.");
        return "redirect:/collections/" + collection.id();
    }

    @GetMapping("/collections/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAttribute("collection", collectionService.details(currentUserService.currentAccount(), id));
        return "collection-detail";
    }

    @PostMapping("/collections/{id}/rename")
    String rename(@PathVariable Long id, @RequestParam String name, RedirectAttributes attributes) {
        collectionService.rename(currentUserService.currentAccount(), id, name);
        attributes.addFlashAttribute("collectionMessage", "단어장 이름을 변경했습니다.");
        return "redirect:/collections/" + id;
    }

    @PostMapping("/collections/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes attributes) {
        collectionService.delete(currentUserService.currentAccount(), id);
        attributes.addFlashAttribute("collectionMessage", "단어장을 삭제했습니다.");
        return "redirect:/collections";
    }

    @PostMapping("/collections/{id}/items/{slug}")
    String addItem(@PathVariable Long id, @PathVariable String slug,
                   @RequestParam(required = false) String returnTo, RedirectAttributes attributes) {
        collectionService.addContent(currentUserService.currentAccount(), id, slug);
        attributes.addFlashAttribute("collectionMessage", "단어장에 추가했습니다.");
        return "redirect:" + (returnTo != null && returnTo.startsWith("/") ? returnTo : "/collections/" + id);
    }

    @PostMapping("/collections/add/{slug}")
    String addItemFromDetail(@PathVariable String slug, @RequestParam Long collectionId,
                             @RequestParam(required = false) String returnTo, RedirectAttributes attributes) {
        collectionService.addContent(currentUserService.currentAccount(), collectionId, slug);
        attributes.addFlashAttribute("collectionMessage", "단어장에 추가했습니다.");
        return "redirect:" + (returnTo != null && returnTo.startsWith("/") ? returnTo : "/contents/" + slug);
    }

    @PostMapping("/collections/{id}/items/{slug}/remove")
    String removeItem(@PathVariable Long id, @PathVariable String slug, RedirectAttributes attributes) {
        collectionService.removeContent(currentUserService.currentAccount(), id, slug);
        attributes.addFlashAttribute("collectionMessage", "단어장에서 제거했습니다.");
        return "redirect:/collections/" + id;
    }

    @GetMapping("/collections/{id}/study")
    String study(@PathVariable Long id, Model model, HttpSession session) {
        var account = currentUserService.currentAccount();
        model.addAttribute("overview", learningService.overview(account));
        model.addAttribute("dailyProgress", learningService.todayProgress(account));
        model.addAttribute("cards", collectionService.cards(account, id));
        model.addAttribute("collection", collectionService.details(account, id));
        model.addAttribute("studySessionKey", sessionKey(session, "collection-" + id));
        return "collection-study";
    }

    @PostMapping("/collections/{id}/study/{slug}/answer")
    String answer(@PathVariable Long id, @PathVariable String slug, @RequestParam StudyResult result,
                  @RequestParam(required = false) String sessionKey) {
        collectionService.details(currentUserService.currentAccount(), id);
        learningService.answer(currentUserService.currentAccount(), slug, result, sessionKey, true);
        return "redirect:/collections/" + id + "/study";
    }

    private String sessionKey(HttpSession session, String prefix) {
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        String attribute = prefix + "Key";
        String dateAttribute = prefix + "Date";
        if (!today.equals(session.getAttribute(dateAttribute))) {
            session.setAttribute(dateAttribute, today);
            session.setAttribute(attribute, prefix + "-" + today + "-" + UUID.randomUUID());
        }
        return (String) session.getAttribute(attribute);
    }
}

@RestController
@RequestMapping("/api/v1/collections")
class StudyCollectionApiController {
    private final StudyCollectionService collectionService;
    private final CurrentUserService currentUserService;

    StudyCollectionApiController(StudyCollectionService collectionService, CurrentUserService currentUserService) {
        this.collectionService = collectionService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    Object list() { return collectionService.list(currentUserService.currentAccount()); }

    @PostMapping
    Object create(@RequestParam String name) { return collectionService.create(currentUserService.currentAccount(), name); }

    @GetMapping("/{id}")
    Object details(@PathVariable Long id) { return collectionService.details(currentUserService.currentAccount(), id); }

    @PostMapping("/{id}")
    Object rename(@PathVariable Long id, @RequestParam String name) { return collectionService.rename(currentUserService.currentAccount(), id, name); }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        collectionService.delete(currentUserService.currentAccount(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items/{slug}")
    ResponseEntity<Void> addItem(@PathVariable Long id, @PathVariable String slug) {
        collectionService.addContent(currentUserService.currentAccount(), id, slug);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/items/{slug}")
    ResponseEntity<Void> removeItem(@PathVariable Long id, @PathVariable String slug) {
        collectionService.removeContent(currentUserService.currentAccount(), id, slug);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/study")
    Object cards(@PathVariable Long id) { return collectionService.cards(currentUserService.currentAccount(), id); }
}
