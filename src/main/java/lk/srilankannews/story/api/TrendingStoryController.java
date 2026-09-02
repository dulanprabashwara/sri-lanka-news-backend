package lk.srilankannews.story.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/stories")
public class TrendingStoryController {

    private final TrendingStoryService service;

    public TrendingStoryController(TrendingStoryService service) {
        this.service = service;
    }

    @GetMapping("/trending")
    public List<TrendingStoryResponse> trending(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) ArticleCategory category,
            @RequestParam(required = false) Language displayLanguage) {
        return service.trending(limit, category, displayLanguage);
    }
}
