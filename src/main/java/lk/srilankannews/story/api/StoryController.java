package lk.srilankannews.story.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.domain.Language;
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
    private final CoverageComparisonService coverageComparisonService;
    private final StoryTimelineService storyTimelineService;

    public StoryController(
            StoryApiService storyApiService,
            CoverageComparisonService coverageComparisonService,
            StoryTimelineService storyTimelineService) {
        this.storyApiService = storyApiService;
        this.coverageComparisonService = coverageComparisonService;
        this.storyTimelineService = storyTimelineService;
    }

    @GetMapping("/stories")
    public PagedResponse<StorySummaryResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) ArticleCategory category,
            @RequestParam(required = false) Language displayLanguage,
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
        return displayLanguage == null
                ? storyApiService.list(page, size, category, publishedFrom, publishedTo, direction)
                : storyApiService.list(
                        page, size, category, publishedFrom, publishedTo, direction, displayLanguage);
    }

    @GetMapping("/stories/{storyId}")
    public StoryDetailResponse detail(
            @PathVariable
            @Pattern(regexp = "[0-9a-fA-F]{24}", message = "must be a valid MongoDB ObjectId")
            String storyId,
            @RequestParam(required = false) Language displayLanguage) {
        return displayLanguage == null
                ? storyApiService.detail(storyId)
                : storyApiService.detail(storyId, displayLanguage);
    }

    @GetMapping("/stories/{storyId}/coverage")
    public CoverageComparisonResponse coverage(
            @PathVariable
            @Pattern(regexp = "[0-9a-fA-F]{24}", message = "must be a valid MongoDB ObjectId")
            String storyId,
            @RequestParam(required = false) Language displayLanguage) {
        return displayLanguage == null
                ? coverageComparisonService.compare(storyId)
                : coverageComparisonService.compare(storyId, displayLanguage);
    }

    @GetMapping("/stories/{storyId}/timeline")
    public StoryTimelineResponse timeline(
            @PathVariable
            @Pattern(regexp = "[0-9a-fA-F]{24}", message = "must be a valid MongoDB ObjectId")
            String storyId,
            @RequestParam(required = false) Language displayLanguage) {
        return displayLanguage == null
                ? storyTimelineService.timeline(storyId)
                : storyTimelineService.timeline(storyId, displayLanguage);
    }

    @GetMapping("/articles/{articleId}/story")
    public StorySummaryResponse storyForArticle(
            @PathVariable
            @Pattern(regexp = "[0-9a-fA-F]{24}", message = "must be a valid MongoDB ObjectId")
            String articleId,
            @RequestParam(required = false) Language displayLanguage) {
        return displayLanguage == null
                ? storyApiService.storyForArticle(articleId)
                : storyApiService.storyForArticle(articleId, displayLanguage);
    }
}
