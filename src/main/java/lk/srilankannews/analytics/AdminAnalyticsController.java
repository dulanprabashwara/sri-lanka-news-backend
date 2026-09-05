package lk.srilankannews.analytics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import org.bson.Document;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
public class AdminAnalyticsController {

    private final AdminAnalyticsService service;
    private final AnalyticsRollupService rollupService;

    public AdminAnalyticsController(AdminAnalyticsService service, AnalyticsRollupService rollupService) {
        this.service = service;
        this.rollupService = rollupService;
    }

    @org.springframework.web.bind.annotation.PostMapping("/trigger-rollup")
    @PreAuthorize("permitAll()")
    public void triggerRollup() {
        rollupService.runRollup();
    }

    @GetMapping("/overview")
    public Map<String, Long> getOverview(@RequestParam(defaultValue = "7") int days) {
        return service.getOverview(days);
    }

    @GetMapping("/timeseries")
    public List<Document> getTimeSeries(@RequestParam(defaultValue = "7") int days, @RequestParam List<AnalyticsEventType> metrics) {
        return service.getTimeSeries(days, metrics);
    }

    @GetMapping("/content")
    public List<Document> getTopContent(@RequestParam(defaultValue = "7") int days, @RequestParam String type) {
        // type = ARTICLE, STORY, SOURCE, CATEGORY
        return service.getTopContent(days, type, 20);
    }

    @GetMapping("/sections")
    public Map<String, Long> getSections(@RequestParam(defaultValue = "7") int days,
                                         @RequestParam List<AnalyticsEventType> types) {
        return service.getSectionMetrics(days, types);
    }

    @GetMapping("/dimensions")
    public Map<String, Long> getDimensions(@RequestParam(defaultValue = "7") int days,
                                           @RequestParam AnalyticsEventType metric,
                                           @RequestParam String dimensionType) {
        return service.getDimensionMetrics(days, metric, dimensionType);
    }
}

