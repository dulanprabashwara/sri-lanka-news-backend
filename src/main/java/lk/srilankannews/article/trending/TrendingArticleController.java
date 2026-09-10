package lk.srilankannews.article.trending;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.domain.Language;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/trending")
public class TrendingArticleController {

    private final TrendingArticleService service;

    public TrendingArticleController(TrendingArticleService service) {
        this.service = service;
    }

    @GetMapping("/articles")
    public List<ArticleResponse> trendingArticles(
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) ArticleCategory category,
            @RequestParam(required = false) Language displayLanguage) {
        return service.trending(limit, category, displayLanguage);
    }
}
