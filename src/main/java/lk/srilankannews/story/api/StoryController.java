package lk.srilankannews.story.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.api.PagedResponse;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class StoryController {

    static final int MAX_PAGE_SIZE = 100;

    private final StoryApiService storyApiService;

    public StoryController(StoryApiService storyApiService) {
        this.storyApiService = storyApiService;
    }

    @GetMapping("/stories")
    public PagedResponse<StorySummaryResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) ArticleCategory category,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant publishedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant publishedTo,
            @RequestParam(defaultValue = "lastPublishedAt,desc")
            @Pattern(
                    regexp = "lastPublishedAt,(?:asc|desc)",
                    message = "must be lastPublishedAt,asc or lastPublishedAt,desc")
            String sort) {
        Sort.Direction direction = sort.endsWith(",asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return storyApiService.list(
                page, size, category, publishedFrom, publishedTo, direction);
    }

    @GetMapping("/stories/{storyId}")
    public StoryDetailResponse detail(
            @PathVariable
            @Pattern(regexp = "[0-9a-fA-F]{24}", message = "must be a valid MongoDB ObjectId")
            String storyId) {
        return storyApiService.detail(storyId);
    }

    @GetMapping("/articles/{articleId}/story")
    public StorySummaryResponse storyForArticle(
            @PathVariable
            @Pattern(regexp = "[0-9a-fA-F]{24}", message = "must be a valid MongoDB ObjectId")
            String articleId) {
        return storyApiService.storyForArticle(articleId);
    }
}
