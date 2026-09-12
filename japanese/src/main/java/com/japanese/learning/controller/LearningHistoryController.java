package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.service.LearningHistoryService;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Controller
class LearningHistoryPageController {
    private final LearningHistoryService historyService;
    private final CurrentUserService currentUserService;

    LearningHistoryPageController(LearningHistoryService historyService, CurrentUserService currentUserService) {
        this.historyService = historyService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/history")
    String page(@RequestParam(required = false) String month, @RequestParam(required = false) String date, Model model) {
        YearMonth selectedMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        LocalDate selectedDate = date == null || date.isBlank()
                ? (selectedMonth.equals(YearMonth.now()) ? LocalDate.now() : selectedMonth.atDay(1))
                : LocalDate.parse(date);
        var account = currentUserService.currentAccount();
        model.addAttribute("activity", historyService.month(account, selectedMonth));
        model.addAttribute("report", historyService.day(account, selectedDate));
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1));
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
        return "learning-history";
    }
}

@RestController
@RequestMapping("/api/v1/history")
class LearningHistoryApiController {
    private final LearningHistoryService historyService;
    private final CurrentUserService currentUserService;

    LearningHistoryApiController(LearningHistoryService historyService, CurrentUserService currentUserService) {
        this.historyService = historyService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/month")
    Object month(@RequestParam String month) {
        return historyService.month(currentUserService.currentAccount(), YearMonth.parse(month));
    }

    @GetMapping("/day")
    Object day(@RequestParam String date) {
        return historyService.day(currentUserService.currentAccount(), LocalDate.parse(date));
    }
}
