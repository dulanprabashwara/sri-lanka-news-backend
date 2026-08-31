package lk.srilankannews.story;

import java.time.Instant;
import lk.srilankannews.article.ArticleCategory;

public record StoryFilter(
        ArticleCategory category,
        Instant publishedFrom,
        Instant publishedTo
) {
}
