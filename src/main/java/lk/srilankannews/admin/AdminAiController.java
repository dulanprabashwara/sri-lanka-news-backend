package lk.srilankannews.admin;

import lk.srilankannews.article.ProcessingStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai")
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
public class AdminAiController {

    private final AdminMongoOperations operations;

    public AdminAiController(AdminMongoOperations operations) {
        this.operations = operations;
    }

    @GetMapping
    public AdminAiOverviewResponse overview() {
        return new AdminAiOverviewResponse(
                new AdminAiOverviewResponse.EnrichmentCounts(
                        operations.articleCount(ProcessingStatus.COMPLETED),
                        operations.articleCount(ProcessingStatus.FAILED),
                        operations.articleCount(ProcessingStatus.RETRYING)
                ),
                new AdminAiOverviewResponse.ProviderStatus(
                        true, "Gemini Provider", "gemini-3.6-flash", "gemini-embedding-2"
                )
        );
    }
}
