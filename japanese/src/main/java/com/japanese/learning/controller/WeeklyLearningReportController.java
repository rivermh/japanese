package com.japanese.learning.controller;

import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.dto.WeeklyLearningReport;
import com.japanese.learning.service.WeeklyLearningReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
public class WeeklyLearningReportController {
    private final CurrentUserService users; private final WeeklyLearningReportService reports;
    public WeeklyLearningReportController(CurrentUserService users, WeeklyLearningReportService reports) { this.users=users;this.reports=reports; }
    @GetMapping("/report/weekly") public String page(Model model) { model.addAttribute("report", reports.report(users.currentAccount())); return "weekly-report"; }
}

@RestController
@RequestMapping("/api/v1/reports")
class WeeklyLearningReportApiController {
    private final CurrentUserService users; private final WeeklyLearningReportService reports;
    WeeklyLearningReportApiController(CurrentUserService users, WeeklyLearningReportService reports) { this.users=users;this.reports=reports; }
    @GetMapping("/weekly") WeeklyLearningReport weekly() { return reports.report(users.currentAccount()); }
}
