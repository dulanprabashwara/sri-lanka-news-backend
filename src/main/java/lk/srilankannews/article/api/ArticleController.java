package lk.srilankannews.article.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.domain.Language;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/articles")
public class ArticleController {

    static final int MAX_PAGE_SIZE = 100;

    private final ArticleApiService articleApiService;

    public ArticleController(ArticleApiService articleApiService) {
        this.articleApiService = articleApiService;
    }

    @GetMapping
    public PagedResponse<ArticleResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(required = false)
            @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "must be a lowercase URL-safe slug")
            String source,
            @RequestParam(required = false) ArticleCategory category,
            @RequestParam(required = false) Language language,
            @RequestParam(required = false) Language displayLanguage,
            @RequestParam(defaultValue = "publishedAt,desc")
            @Pattern(regexp = "publishedAt,(?:asc|desc)", message = "must be publishedAt,asc or publishedAt,desc")
            String sort
    ) {
        Sort.Direction direction = sort.endsWith(",asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return displayLanguage == null
                ? articleApiService.list(page, size, source, category, language, direction)
                : articleApiService.list(
                        page, size, source, category, language, displayLanguage, direction);
    }

    @GetMapping("/{id}")
    public ArticleResponse detail(
            @PathVariable
            @Pattern(regexp = "[0-9a-fA-F]{24}", message = "must be a valid MongoDB ObjectId")
            String id,
            @RequestParam(required = false) Language displayLanguage
    ) {
        return displayLanguage == null
                ? articleApiService.detail(id)
                : articleApiService.detail(id, displayLanguage);
    }
}
