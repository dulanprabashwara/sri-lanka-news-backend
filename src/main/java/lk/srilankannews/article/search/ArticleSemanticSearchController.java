package lk.srilankannews.article.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/search/semantic")
public class ArticleSemanticSearchController {
    private final ArticleSemanticSearchService service;

    public ArticleSemanticSearchController(ArticleSemanticSearchService service) {
        this.service = service;
    }

    @GetMapping
    public SemanticSearchResponse search(
            @RequestParam @NotBlank @Size(max = TextSearchQueryNormalizer.MAX_QUERY_LENGTH) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false)
            @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*",
                    message = "must be a lowercase URL-safe slug") String source,
            @RequestParam(required = false) ArticleCategory category,
            @RequestParam(required = false) Language language,
            @RequestParam(required = false) Language displayLanguage) {
        return service.search(q, page, size, source, category, language, displayLanguage);
    }
}
