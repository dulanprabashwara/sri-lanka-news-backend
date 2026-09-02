package lk.srilankannews.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import lk.srilankannews.article.ProcessingStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin")
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public Map<String, Boolean> me() {
        return Map.of("admin", true);
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/sources")
    public List<AdminSourceResponse> sources() {
        return service.sources();
    }

    @GetMapping("/articles")
    public List<AdminArticleResponse> articles(
            @RequestParam(required = false) ProcessingStatus status,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit) {
        return service.articles(status, source, limit);
    }

    @PostMapping("/articles/{articleId}/retry")
    public AdminArticleResponse retry(
            @PathVariable
            @Pattern(regexp = "[0-9a-fA-F]{24}", message = "must be a valid MongoDB ObjectId")
            String articleId) {
        return service.retry(articleId);
    }
}
