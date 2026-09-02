package lk.srilankannews.story.ask;

import java.time.Instant;
import lk.srilankannews.article.ArticleCategory;

public record GroundedSourceContext(
        String id,
        String publisher,
        String title,
        Instant publishedAt,
        ArticleCategory category,
        String summary,
        String content) {
}
