package com.japanese.learning.controller;
import com.japanese.account.service.CurrentUserService;
import com.japanese.learning.service.LearningAnalyticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller public class StatisticsController {
 private final CurrentUserService user; private final LearningAnalyticsService analytics;
 public StatisticsController(CurrentUserService u, LearningAnalyticsService a){user=u;analytics=a;}
 @GetMapping("/statistics") public String page(Model model){var account=user.currentAccount();model.addAttribute("statistics",analytics.statistics(account));model.addAttribute("weaknesses",analytics.weaknesses(account));model.addAttribute("levelProgress",analytics.levelProgress(account));model.addAttribute("recentErrors",analytics.recentErrors(account, 8));model.addAttribute("weeklyActivity",analytics.weeklyActivity(account));return "statistics";}
}
@RestController @RequestMapping("/api/v1/statistics") class StatisticsApiController {
 private final CurrentUserService user; private final LearningAnalyticsService analytics;
 StatisticsApiController(CurrentUserService u, LearningAnalyticsService a){user=u;analytics=a;}
 @GetMapping public Object statistics(){return analytics.statistics(user.currentAccount());}
 @GetMapping("/weaknesses") public Object weaknesses(){return analytics.weaknesses(user.currentAccount());}
 @GetMapping("/levels") public Object levels(){return analytics.levelProgress(user.currentAccount());}
 @GetMapping("/recent-errors") public Object recentErrors(){return analytics.recentErrors(user.currentAccount(), 20);}
 @GetMapping("/week") public Object week(){return analytics.weeklyActivity(user.currentAccount());}
}
