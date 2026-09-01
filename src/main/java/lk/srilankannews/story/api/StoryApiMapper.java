package lk.srilankannews.story.api;

import java.util.List;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.story.Story;
import org.springframework.stereotype.Component;

@Component
public class StoryApiMapper {

    public StorySummaryResponse toSummary(Story story) {
        return toSummary(story, null);
    }

    public StorySummaryResponse toSummary(
            Story story, LocalizedStoryContentResponse localizedContent) {
        return new StorySummaryResponse(
                story.id(),
                story.canonicalTitle(),
                story.category(),
                story.firstPublishedAt(),
                story.lastPublishedAt(),
                story.articleCount(),
                story.sourceIds().size(),
                localizedContent);
    }

    public StoryDetailResponse toDetail(Story story, List<ArticleResponse> articles) {
        return toDetail(story, articles, null);
    }

    public StoryDetailResponse toDetail(
            Story story,
            List<ArticleResponse> articles,
            LocalizedStoryContentResponse localizedContent) {
        return new StoryDetailResponse(
                story.id(),
                story.canonicalTitle(),
                story.category(),
                story.firstPublishedAt(),
                story.lastPublishedAt(),
                story.articleCount(),
                story.sourceIds().size(),
                articles,
                localizedContent);
    }
}
