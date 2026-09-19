package com.japanese.account.controller;

import com.japanese.account.service.AccountDeletionService;
import com.japanese.account.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AccountDeletionController {
    private final CurrentUserService currentUser;
    private final AccountDeletionService accountDeletion;

    public AccountDeletionController(CurrentUserService currentUser, AccountDeletionService accountDeletion) {
        this.currentUser = currentUser;
        this.accountDeletion = accountDeletion;
    }

    @GetMapping("/settings/delete-account")
    public String confirmation() {
        currentUser.currentAccount();
        return "delete-account";
    }

    @PostMapping("/settings/delete-account")
    public String delete(@RequestParam("currentPassword") String currentPassword,
            HttpServletRequest request, Model model) {
        AccountDeletionService.Result result;
        try {
            result = accountDeletion.deleteOwnAccount(currentUser.currentAccount(), currentPassword);
        } catch (RuntimeException exception) {
            model.addAttribute("deleteAccountError", "계정을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
            return "delete-account";
        }
        if (result == AccountDeletionService.Result.INVALID_PASSWORD) {
            model.addAttribute("deleteAccountError", "현재 비밀번호를 확인해 주세요.");
            return "delete-account";
        }
        if (result == AccountDeletionService.Result.NOT_SUPPORTED_FOR_ADMIN) {
            model.addAttribute("deleteAccountError", "관리자 계정 삭제는 지원팀에 문의해 주세요.");
            return "delete-account";
        }
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return "redirect:/login?accountDeleted=true";
    }
}
