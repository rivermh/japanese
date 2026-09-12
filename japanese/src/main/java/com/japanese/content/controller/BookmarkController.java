package com.japanese.content.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.content.entity.ContentType;
import com.japanese.content.service.BookmarkService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BookmarkController {
    private final BookmarkService bookmarkService; private final CurrentUserService currentUserService;
    public BookmarkController(BookmarkService bookmarkService, CurrentUserService currentUserService) { this.bookmarkService = bookmarkService; this.currentUserService = currentUserService; }
    @PostMapping("/contents/{slug}/bookmark") public String toggle(@PathVariable String slug, @RequestParam(required = false) String returnTo, RedirectAttributes attributes) {
        boolean added = bookmarkService.toggle(currentUserService.currentAccount(), slug);
        attributes.addFlashAttribute("bookmarkMessage", added ? "북마크에 저장했습니다." : "북마크를 해제했습니다.");
        return "redirect:" + (returnTo == null || !returnTo.startsWith("/") ? "/contents/" + slug : returnTo);
    }
    @GetMapping("/bookmarks") public String bookmarks(@RequestParam(required = false) ContentType type, @RequestParam(required = false) String level, Model model) {
        model.addAttribute("bookmarks", bookmarkService.list(currentUserService.currentAccount(), type, level));
        model.addAttribute("type", type == null ? "" : type.name()); model.addAttribute("level", level == null ? "" : level);
        return "bookmarks";
    }
}
